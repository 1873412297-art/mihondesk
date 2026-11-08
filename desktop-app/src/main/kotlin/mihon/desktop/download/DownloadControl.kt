package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Start/pause/resume/cancel of the download session and of individual chapters. */
internal class DownloadControl(
    private val scope: CoroutineScope,
    private val diskProvider: DownloadDiskProvider,
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val isRunning: MutableStateFlow<Boolean>,
    private val speedBytesPerSec: MutableStateFlow<Double>,
    private val persistence: DownloadQueuePersistence,
    private val activeJobs: ConcurrentHashMap<Long, Job>,
    private val pendingCleanupJobs: ConcurrentHashMap<Long, Job>,
    private val sessionBytes: AtomicLong,
    private val sessionGeneration: AtomicLong,
    private val runLoop: suspend () -> Unit,
    private val onFatal: (String) -> Unit,
    private val onCleanupFailure: (String) -> Unit,
) : AutoCloseable {
    private var downloadJob: Job? = null
    private var sessionStartedAt = 0L

    @Synchronized
    fun start(): Boolean {
        if (isRunning.value) return false
        val hasPending = queueState.value.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
        }
        if (!hasPending) return false

        isRunning.value = true
        sessionBytes.set(0L)
        sessionStartedAt = System.currentTimeMillis()
        val generation = sessionGeneration.incrementAndGet()
        val previousJob = downloadJob
        downloadJob = scope.launch {
            try {
                previousJob?.join()
                runLoop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onFatal("下载调度已停止：${error.message}")
            } finally {
                if (sessionGeneration.get() == generation) {
                    isRunning.value = false
                    speedBytesPerSec.value = 0.0
                }
            }
        }
        return true
    }

    @Synchronized
    fun sessionStartedAtMillis(): Long = sessionStartedAt

    /** Stops the run loop; DOWNLOADING chapters become PAUSED so they stay resumable in memory. */
    @Synchronized
    fun stopScheduling() {
        sessionGeneration.incrementAndGet()
        downloadJob?.cancel()
        downloadJob = null
        isRunning.value = false
        speedBytesPerSec.value = 0.0
        // Cancelled workers leave their chapters in DOWNLOADING. Without a persisted save they can
        // never be claimed again (and start() ignores DOWNLOADING), so make them resumable in memory.
        queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.DOWNLOADING) item.copy(status = DownloadStatus.PAUSED) else item
            }
        }
    }

    @Synchronized
    fun pause() {
        sessionGeneration.incrementAndGet()
        downloadJob?.cancel()
        isRunning.value = false
        speedBytesPerSec.value = 0.0

        queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                    item.copy(status = DownloadStatus.PAUSED)
                } else {
                    item
                }
            }
        }
        persistence.persist()
    }

    @Synchronized
    fun resume() {
        queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.PAUSED) {
                    item.copy(status = DownloadStatus.QUEUED)
                } else {
                    item
                }
            }
        }
        persistence.persist()
        start()
    }

    @Synchronized
    fun pause(chapterId: Long): Boolean {
        val job = activeJobs.remove(chapterId)
        job?.cancel()
        var changed = false
        queueState.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId &&
                    (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED)
                ) {
                    changed = true
                    item.copy(status = DownloadStatus.PAUSED)
                } else {
                    item
                }
            }
        }
        if (changed) {
            persistence.persist()
        }
        return changed
    }

    @Synchronized
    fun resume(chapterId: Long): Boolean {
        var changed = false
        queueState.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId && item.status == DownloadStatus.PAUSED) {
                    changed = true
                    item.copy(status = DownloadStatus.QUEUED)
                } else {
                    item
                }
            }
        }
        if (changed) {
            persistence.persist()
            start()
        }
        return changed
    }

    @Synchronized
    fun cancel(chapterId: Long): DesktopDownload? {
        val job = activeJobs.remove(chapterId)
        val previousCleanup = pendingCleanupJobs[chapterId]
        val cancelled = queueState.value.find { it.chapterId == chapterId }
        job?.cancel()
        val cleanup = cancelled?.let { download ->
            scope.launch(start = CoroutineStart.LAZY) {
                previousCleanup?.join()
                job?.join()
                runCatching {
                    diskProvider.deleteTempChapter(
                        download.sourceId,
                        download.mangaTitle,
                        download.chapterName,
                        download.mangaId,
                        download.chapterId,
                    )
                }.onFailure { onCleanupFailure("下载临时文件无法清理：${it.message}") }
            }.also { cleanup ->
                pendingCleanupJobs[chapterId] = cleanup
                cleanup.invokeOnCompletion { pendingCleanupJobs.remove(chapterId, cleanup) }
            }
        }
        queueState.update { list -> list.filterNot { it.chapterId == chapterId } }
        try {
            persistence.persist()
        } finally {
            cleanup?.start()
        }
        return cancelled
    }

    override fun close() {
        sessionGeneration.incrementAndGet()
        downloadJob?.cancel()
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        downloadJob = null
        isRunning.value = false
    }
}
