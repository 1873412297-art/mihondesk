package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

/** Claims queued chapters (fair per source) and runs them with bounded parallelism. */
internal class DownloadScheduler(
    private val diskProvider: DownloadDiskProvider,
    private val engine: DownloadEngine,
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val isRunning: StateFlow<Boolean>,
    private val persistence: DownloadQueuePersistence,
    private val activeJobs: ConcurrentHashMap<Long, Job>,
    private val pendingCleanupJobs: ConcurrentHashMap<Long, Job>,
    private val downloadParallelism: () -> Int,
    private val sourceParallelism: () -> Int,
    private val onFailed: ((DesktopDownload, String) -> Unit)?,
) {
    private var lastClaimedSource: Long? = null

    suspend fun runLoop() = supervisorScope {
        val workers = List(downloadParallelism().coerceIn(1, 16)) {
            launch {
                while (isRunning.value) {
                    val (chapterId, chapterJob) = synchronized(this@DownloadScheduler) {
                        val next = claimNext() ?: return@synchronized null
                        val chapterJob = launchChapterJob(next)
                        activeJobs[next.chapterId] = chapterJob
                        next.chapterId to chapterJob
                    } ?: break
                    try {
                        // Save the claimed state before starting I/O, outside the downloader monitor.
                        // A failed save must release the claim, or the chapter stays DOWNLOADING with no job.
                        try {
                            persistence.persist()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            engine.updateDownload(chapterId) {
                                it.copy(status = DownloadStatus.QUEUED, error = null, failureReason = null)
                            }
                            break
                        }
                        chapterJob.start()
                        chapterJob.join()
                    } finally {
                        activeJobs.remove(chapterId, chapterJob)
                        if (!chapterJob.isCompleted) chapterJob.cancel()
                    }
                }
            }
        }
        workers.forEach { it.join() }
    }

    @Synchronized
    private fun claimNext(): DesktopDownload? {
        while (true) {
            val current = queueState.value
            val activeBySource = current.filter {
                it.status == DownloadStatus.DOWNLOADING
            }.groupingBy { it.sourceId }.eachCount()
            val eligible = current.filter {
                it.status == DownloadStatus.QUEUED &&
                    (activeBySource[it.sourceId] ?: 0) < sourceParallelism().coerceIn(1, 16)
            }
            val sources = current.map { it.sourceId }.distinct()
            val after = sources.indexOfFirst { it == lastClaimedSource } + 1
            val rotation = sources.drop(after) + sources.take(after)
            val source = rotation.firstOrNull { id -> eligible.any { it.sourceId == id } } ?: return null
            val next = eligible.first { it.sourceId == source }
            val claimed = next.copy(status = DownloadStatus.DOWNLOADING, error = null, failureReason = null)
            val updated = current.map { item -> if (item.chapterId == next.chapterId) claimed else item }
            if (queueState.compareAndSet(current, updated)) {
                lastClaimedSource = next.sourceId
                return claimed
            }
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchChapterJob(next: DesktopDownload): Job =
        launch(start = CoroutineStart.LAZY) {
            pendingCleanupJobs[next.chapterId]?.join()
            if (!diskProvider.checkDiskSpace()) {
                val errorMessage = "Insufficient disk space"
                engine.updateDownload(next.chapterId) {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        error = errorMessage,
                        failureReason = DownloadFailureReason.STORAGE_FULL,
                    )
                }
                onFailed?.invoke(next, errorMessage)
                return@launch
            }

            try {
                engine.processDownload(next)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val errorMessage = error.message ?: "Unknown download error"
                mihon.desktop.logging.DesktopLogger.error(
                    "Downloader",
                    "Download failed for chapter ${next.chapterName} (${next.chapterId}): $errorMessage",
                    error,
                )
                val failed = engine.updateDownload(next.chapterId) {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        error = errorMessage,
                        failureReason = classifyDownloadFailure(error),
                    )
                }
                onFailed?.invoke(failed ?: next, errorMessage)
            }
        }
}
