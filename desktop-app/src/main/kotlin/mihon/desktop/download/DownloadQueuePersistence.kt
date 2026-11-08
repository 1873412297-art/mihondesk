package mihon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Saves the download queue, coalescing bursts of updates into one debounced write. */
internal class DownloadQueuePersistence(
    private val store: DownloadStore,
    private val scope: CoroutineScope,
    private val queue: () -> List<DesktopDownload>,
    private val onSaved: () -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val lock = Any()
    private val debounceJob = AtomicReference<Job?>(null)

    fun persistImmediate() {
        val failure = synchronized(lock) {
            debounceJob.getAndSet(null)?.cancel()
            try {
                store.save(queue())
                onSaved()
                null
            } catch (error: Exception) {
                error
            }
        }
        if (failure != null) {
            // Never call back into the download control while holding the save lock: pause/resume
            // hold the control monitor while calling persist(), which would invert the lock order.
            onFailure(failure)
            throw failure
        }
    }

    fun persist() = persistImmediate()

    fun scheduleDebounced() {
        if (debounceJob.get() != null) return
        val job = scope.launch {
            delay(500)
            debounceJob.set(null)
            val failure = synchronized(lock) {
                try {
                    store.save(queue())
                    onSaved()
                    null
                } catch (error: Exception) {
                    error
                }
            }
            failure?.let(onFailure)
        }
        if (!debounceJob.compareAndSet(null, job)) {
            job.cancel()
        }
    }
}
