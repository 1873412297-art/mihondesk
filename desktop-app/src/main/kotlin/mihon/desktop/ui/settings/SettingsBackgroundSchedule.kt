package mihon.desktop.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.platform.WindowsBackgroundScheduler
import mihon.desktop.preferences.DesktopPreferences

/**
 * Keeps the saved library/backup intervals aligned with the Windows tasks. A failed registration
 * restores the last schedule that was actually applied instead of leaving the preference ahead of it.
 */
internal class BackgroundScheduleCoordinator(
    private val scheduler: WindowsBackgroundScheduler?,
    private val scope: CoroutineScope,
    initialSchedule: Pair<Int, Int>,
    initiallyEnabled: Boolean,
) {
    private val gate = Mutex()

    @Volatile private var desired = initialSchedule

    @Volatile private var enabled = initiallyEnabled
    private var saved = initialSchedule

    fun observe(
        updated: DesktopPreferences,
        restore: (Pair<Int, Int>) -> Unit,
        onFailure: suspend (String) -> Unit,
    ): Job {
        desired = updated.libraryUpdateIntervalHours to updated.backupIntervalHours
        enabled = updated.backgroundTasksEnabled
        return scope.launch {
            val failure = withContext(NonCancellable) { gate.withLock { drain(restore) } }
            if (failure != null) onFailure(failure)
        }
    }

    private suspend fun drain(restore: (Pair<Int, Int>) -> Unit): String? {
        val active = scheduler
        if (!enabled || active == null) {
            saved = desired
            return null
        }
        var failure: String? = null
        while (enabled && desired != saved && failure == null) {
            val target = desired
            failure = backgroundScheduleFailure(active, target)
            if (!enabled) {
                saved = desired
                return null
            }
            if (failure == null) {
                saved = target
            } else if (desired == target) {
                restore(saved)
                desired = saved
            } else {
                failure = null
            }
        }
        return failure
    }

    /**
     * Applies the background-tasks switch. Serialized with interval reconciliation through [gate],
     * so enabling/disabling cannot race a pending interval change. Returns the scheduler error on
     * failure, after restoring the previously applied task state.
     */
    suspend fun setEnabled(enabled: Boolean, schedule: Pair<Int, Int>): String? = gate.withLock {
        val active = scheduler
        if (active == null) {
            desired = schedule
            this.enabled = enabled
            saved = schedule
            return@withLock null
        }
        val failure = backgroundScheduleFailure(active, schedule, enabled)
        if (failure == null) {
            desired = schedule
            this.enabled = enabled
            saved = schedule
        } else {
            val restoreFailure = backgroundScheduleFailure(active, saved, this.enabled)
            if (restoreFailure != null) {
                DesktopLogger.warn("Settings", "Cannot restore background tasks after a failure: $restoreFailure")
            }
        }
        failure
    }
}

internal suspend fun backgroundScheduleFailure(
    scheduler: WindowsBackgroundScheduler,
    schedule: Pair<Int, Int>,
    enabled: Boolean = true,
): String? = try {
    val results = withContext(Dispatchers.IO) {
        scheduler.reconcile(enabled, schedule.first, schedule.second)
    }
    results.values.firstOrNull { it.exitCode != 0 }?.output?.let(::shortScheduleError)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    DesktopLogger.warn("Settings", "Background schedule failed", error)
    shortScheduleError(error.message)
}

private fun shortScheduleError(message: String?): String =
    message?.trim()?.lineSequence()?.firstOrNull()?.take(MAX_SCHEDULE_ERROR)?.ifBlank { null }
        ?: "the system scheduler rejected the change"

private const val MAX_SCHEDULE_ERROR = 180
