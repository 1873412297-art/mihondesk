package mihon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.library.repository.LibraryMutationPort

/** Restores the saved queue and reconciles completed entries with the files on disk. */
internal class DownloadStartupRecovery(
    private val scope: CoroutineScope,
    private val store: DownloadStore,
    private val diskProvider: DownloadDiskProvider,
    private val mutationPort: LibraryMutationPort?,
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val persistQueue: () -> Unit,
    private val startLoop: () -> Unit,
) {
    private var recoveryJob: Job? = null

    fun restorePersistedQueue() {
        val restored = store.restore()
        diskProvider.adoptLegacyDownloads(restored)
        queueState.value = restored
        recoveryJob = if (restored.any { it.status == DownloadStatus.COMPLETED }) {
            scope.launch(Dispatchers.IO) {
                var changed = false
                queueState.update { currentList ->
                    currentList.map { download ->
                        if (download.status == DownloadStatus.COMPLETED) {
                            val recovered = recoverMissing(download)
                            if (recovered != download) {
                                changed = true
                                recovered
                            } else {
                                download
                            }
                        } else {
                            download
                        }
                    }
                }
                if (changed) persistQueue()
            }
        } else {
            null
        }
    }

    suspend fun await() {
        recoveryJob?.join()
    }

    fun recoverMissing(download: DesktopDownload): DesktopDownload {
        if (download.status != DownloadStatus.COMPLETED) return download
        val inspection = diskProvider.inspectChapter(
            sourceId = download.sourceId,
            mangaTitle = download.mangaTitle,
            chapterName = download.chapterName,
            expectedPageIndexes = download.pages.map(DownloadPage::index),
            mangaId = download.mangaId,
            chapterId = download.chapterId,
        )
        if (inspection.isComplete) {
            val registrationError = runCatching {
                mutationPort?.let { mutations ->
                    if (!diskProvider.isChapterRegistrationCurrent(
                            sourceId = download.sourceId,
                            mangaId = download.mangaId,
                            chapterId = download.chapterId,
                            mangaTitle = download.mangaTitle,
                            chapterName = download.chapterName,
                            totalBytes = inspection.totalBytes,
                            mutationPort = mutations,
                        )
                    ) {
                        diskProvider.registerCompletedChapter(
                            sourceId = download.sourceId,
                            mangaId = download.mangaId,
                            chapterId = download.chapterId,
                            mangaTitle = download.mangaTitle,
                            chapterName = download.chapterName,
                            totalBytes = inspection.totalBytes,
                            mutationPort = mutations,
                        )
                    }
                }
            }.exceptionOrNull()
            if (registrationError == null) {
                return download.copy(
                    bytesDownloaded = inspection.totalBytes,
                    pages = download.pages.map { page ->
                        val metadata = inspection.validPages.getValue(page.index)
                        page.copy(
                            status = PageStatus.READY,
                            progress = 1f,
                            bytesWritten = metadata.sizeBytes,
                            error = null,
                        )
                    },
                )
            }
            val message = "Downloaded files could not be registered for offline reading: ${registrationError.message}"
            return download.copy(
                status = DownloadStatus.ERROR,
                error = message,
                failureReason = DownloadFailureReason.REGISTRATION,
            )
        }
        runCatching { mutationPort?.deleteLocalChapterAsset(download.mangaId, download.chapterId) }
            .onFailure {
                mihon.desktop.logging.DesktopLogger.warn("Downloader", "Failed to delete incomplete asset", it)
            }
        val message = "Downloaded files are missing or corrupt. Retry to download them again."
        return download.copy(
            status = DownloadStatus.ERROR,
            error = message,
            failureReason = DownloadFailureReason.MISSING_FILES,
            progress = if (download.pages.isEmpty()) {
                0f
            } else {
                inspection.validPages.size.toFloat() / download.pages.size
            },
            bytesDownloaded = inspection.totalBytes,
            pages = download.pages.map { page ->
                val metadata = inspection.validPages[page.index]
                if (metadata != null) {
                    page.copy(
                        status = PageStatus.READY,
                        progress = 1f,
                        bytesWritten = metadata.sizeBytes,
                        error = null,
                    )
                } else {
                    page.copy(
                        status = PageStatus.ERROR,
                        progress = 0f,
                        bytesWritten = 0L,
                        error = message,
                    )
                }
            },
        )
    }

    fun restore(items: List<DesktopDownload>) {
        if (items.isEmpty()) return
        queueState.update { list ->
            val existingIds = list.map { it.chapterId }.toSet()
            val toAdd = items.filterNot { it.chapterId in existingIds }.map {
                if (it.status == DownloadStatus.DOWNLOADING) {
                    it.copy(status = DownloadStatus.QUEUED)
                } else {
                    it
                }
            }
            list + toAdd
        }
        persistQueue()
        // The queue may have already drained (isRunning == false); without this the restored
        // items would sit in QUEUED forever, unlike resume() which always restarts the loop.
        startLoop()
    }
}
