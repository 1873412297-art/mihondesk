package mihon.desktop.activity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ActivityKind { DOWNLOAD, LIBRARY_UPDATE, EXTENSION_INSTALL, SYNC }

enum class ActivityState { RUNNING, FAILED, SUCCEEDED }

/**
 * One background task as shown in the activity center. Titles are localised by the UI from
 * [kind]; [subject] carries the user-facing name of what is being worked on (manga, extension).
 */
data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val state: ActivityState,
    val subject: String? = null,
    val current: Int? = null,
    val total: Int? = null,
    /** 0..1 when known; null renders an indeterminate indicator while running. */
    val progress: Float? = null,
    val failedCount: Int = 0,
    val failureReason: String? = null,
    val updatedAt: Long = 0L,
    val retry: (() -> Unit)? = null,
    /** Extra one-click actions rendered under the failure reason. */
    val actions: List<ActivityAction> = emptyList(),
)

data class ActivityAction(val id: String, val label: String, val run: () -> Unit)

data class ActivitySummary(val running: Int, val failed: Int)

/** Progress reporter handed to [ActivityCenter.track] blocks. */
fun interface ActivityProgress {
    fun progress(current: Int, total: Int)
}

/**
 * Process-wide registry of background work (downloads, library updates, extension installs,
 * sync). Producers report into it; the title-bar activity center renders [items].
 */
class ActivityCenter(private val clock: () -> Long = System::currentTimeMillis) {
    private val entries = MutableStateFlow<Map<String, ActivityItem>>(emptyMap())
    private val _items = MutableStateFlow<List<ActivityItem>>(emptyList())
    val items: StateFlow<List<ActivityItem>> = _items.asStateFlow()
    private val _summary = MutableStateFlow(ActivitySummary(0, 0))
    val summary: StateFlow<ActivitySummary> = _summary.asStateFlow()

    fun begin(id: String, kind: ActivityKind, subject: String? = null, retry: (() -> Unit)? = null) = mutate { map ->
        val previous = map[id]
        map + (
            id to ActivityItem(
                id = id,
                kind = kind,
                state = ActivityState.RUNNING,
                subject = subject ?: previous?.subject,
                updatedAt = clock(),
                retry = retry ?: previous?.retry,
            )
            )
    }

    fun progress(id: String, current: Int, total: Int, subject: String? = null) = mutate { map ->
        val item = map[id] ?: return@mutate map
        map + (
            id to item.copy(
                current = current,
                total = total,
                progress = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else null,
                subject = subject ?: item.subject,
                updatedAt = clock(),
            )
            )
    }

    fun succeed(id: String) = mutate { map ->
        val item = map[id] ?: return@mutate map
        map + (
            id to item.copy(
                state = ActivityState.SUCCEEDED,
                progress = null,
                failureReason = null,
                failedCount = 0,
                updatedAt = clock(),
            )
            )
    }

    fun fail(id: String, reason: String, retry: (() -> Unit)? = null, failedCount: Int = 0) = mutate { map ->
        val item = map[id] ?: return@mutate map
        map + (
            id to item.copy(
                state = ActivityState.FAILED,
                progress = null,
                failureReason = reason,
                failedCount = failedCount,
                retry = retry ?: item.retry,
                updatedAt = clock(),
            )
            )
    }

    /** Replaces (or with `null` removes) an entry derived from another component's state. */
    fun publish(item: ActivityItem?, id: String = item?.id.orEmpty()) = mutate { map ->
        if (item == null) {
            map - id
        } else {
            val current = map[item.id]
            if (current != null && current.copy(updatedAt = 0) == item.copy(updatedAt = 0)) {
                map
            } else {
                map + (item.id to item.copy(updatedAt = clock()))
            }
        }
    }

    /** Re-runs a failed task. Returns false when the entry has no retry action. */
    fun retry(id: String): Boolean {
        val item = entries.value[id] ?: return false
        val action = item.retry ?: return false
        mutate { map ->
            map + (
                id to item.copy(
                    state = ActivityState.RUNNING,
                    failureReason = null,
                    failedCount = 0,
                    progress = null,
                    updatedAt = clock(),
                )
                )
        }
        action()
        return true
    }

    /** Removes a finished entry; running work cannot be dismissed. */
    fun dismiss(id: String) = mutate { map ->
        if (map[id]?.state == ActivityState.RUNNING) map else map - id
    }

    fun clearFinished() = mutate { map -> map.filterValues { it.state == ActivityState.RUNNING } }

    suspend fun <T> track(
        id: String,
        kind: ActivityKind,
        subject: String? = null,
        retry: (() -> Unit)? = null,
        block: suspend (ActivityProgress) -> T,
    ): T {
        begin(id, kind, subject, retry)
        return try {
            block { current, total -> progress(id, current, total) }.also { succeed(id) }
        } catch (cancelled: CancellationException) {
            remove(id)
            throw cancelled
        } catch (error: Throwable) {
            fail(id, error.message ?: error.javaClass.simpleName)
            throw error
        }
    }

    /** Drops an entry regardless of state (e.g. the work was cancelled by the user). */
    fun remove(id: String) = mutate { it - id }

    private fun mutate(transform: (Map<String, ActivityItem>) -> Map<String, ActivityItem>) {
        synchronized(this) {
            entries.update { map -> prune(transform(map)) }
            val sorted = entries.value.values.sortedWith(
                compareBy<ActivityItem> { it.state.ordinal }.thenByDescending { it.updatedAt },
            )
            _items.value = sorted
            _summary.value = ActivitySummary(
                running = sorted.count { it.state == ActivityState.RUNNING },
                failed = sorted.count { it.state == ActivityState.FAILED },
            )
        }
    }

    private fun prune(map: Map<String, ActivityItem>): Map<String, ActivityItem> {
        val finished = map.values.filter { it.state == ActivityState.SUCCEEDED }
        if (finished.size <= MAX_FINISHED) return map
        val drop = finished.sortedByDescending { it.updatedAt }.drop(MAX_FINISHED).map { it.id }.toSet()
        return map - drop
    }

    companion object {
        const val MAX_FINISHED = 8
    }
}
