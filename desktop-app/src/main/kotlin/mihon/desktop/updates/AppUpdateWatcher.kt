package mihon.desktop.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.logging.runSuspendCatching
import mihon.desktop.preferences.DesktopPreferenceStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Checks for a new MihonDesk release shortly after startup and then every [interval], and hands a
 * release to [onNewRelease] once per version. Nothing is downloaded: the user decides from the
 * notification. Checks are skipped while the user has turned automatic checking off.
 */
class AppUpdateWatcher(
    private val check: suspend () -> UpdateCheckResult,
    private val preferences: DesktopPreferenceStore,
    private val scope: CoroutineScope,
    private val onNewRelease: (UpdateCheckResult.UpdateAvailable) -> Unit,
    private val initialDelay: Duration = 1.minutes,
    private val interval: Duration = 24.hours,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private var job: Job? = null
    private val mutableLatest = MutableStateFlow<UpdateCheckResult.UpdateAvailable?>(null)

    /** The release most recently announced in this session, for the in-app update card. */
    val latest: StateFlow<UpdateCheckResult.UpdateAvailable?> = mutableLatest.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // Let startup work (library update, extension checks) go first.
            delay(initialDelay)
            while (isActive) {
                checkOnce()
                delay(interval)
            }
        }
    }

    /** One check; returns the release that was announced, or null. Exposed for tests. */
    internal suspend fun checkOnce(): UpdateCheckResult.UpdateAvailable? {
        if (!withContext(ioDispatcher) { preferences.load() }.appUpdateAutoCheck) return null
        val result = runSuspendCatching { check() }
            .onFailure { DesktopLogger.warn(TAG, "Automatic update check failed", it) }
            .getOrNull()
        return when (result) {
            is UpdateCheckResult.UpdateAvailable -> announceOnce(result)
            is UpdateCheckResult.CheckFailed -> {
                // Offline or rate-limited: try again at the next interval, without bothering the user.
                DesktopLogger.info(TAG, "Automatic update check failed: ${result.message}")
                null
            }
            is UpdateCheckResult.UpToDate, null -> null
        }
    }

    private suspend fun announceOnce(
        result: UpdateCheckResult.UpdateAvailable,
    ): UpdateCheckResult.UpdateAvailable? {
        // A release without a package for this platform cannot be installed here.
        if (result.matchedAsset == null) return null
        val version = result.release.tagName
        val alreadyNotified = withContext(ioDispatcher) {
            var seen = false
            preferences.updatePreferences { current ->
                seen = current.appUpdateNotifiedVersion == version
                if (seen) current else current.copy(appUpdateNotifiedVersion = version)
            }
            seen
        }
        if (alreadyNotified) return null
        mutableLatest.value = result
        onNewRelease(result)
        return result
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "AppUpdateWatcher"
    }
}
