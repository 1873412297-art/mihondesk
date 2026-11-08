package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.extension.downloadSourcePage
import mihon.desktop.extension.getPageList
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.extension.ipc.RequestPriority
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Downloads the pages of one chapter and publishes it, reporting progress through callbacks. */
internal class DownloadEngine(
    private val diskProvider: DownloadDiskProvider,
    private val networkHelper: DesktopNetworkHelper,
    private val sourceManager: DesktopSourceManager?,
    private val processManager: WindowsExtensionProcessManager?,
    private val mutationPort: LibraryMutationPort?,
    private val pageListFetcher: (suspend (Long, String) -> List<Page>)?,
    private val pageParallelism: () -> Int,
    private val tallImageSplitter: TallImageSplitter?,
    private val queueState: MutableStateFlow<List<DesktopDownload>>,
    private val persistence: DownloadQueuePersistence,
    private val sessionBytes: AtomicLong,
    private val sessionStartedAt: () -> Long,
    private val onSpeed: (Double) -> Unit,
    private val onCompleted: ((DesktopDownload) -> Unit)?,
    private val onProgress: ((DesktopDownload) -> Unit)?,
) {

    internal suspend fun processDownload(download: DesktopDownload) {
        // Fetch page list if not already populated
        val currentDownload = queueState.value.firstOrNull { it.chapterId == download.chapterId } ?: run {
            mihon.desktop.logging.DesktopLogger.warn(
                "Downloader",
                "Download for chapter ${download.chapterId} was cancelled before processing",
            )
            return
        }
        val pages = if (currentDownload.pages.isEmpty()) {
            val fetchedPages = fetchPages(currentDownload.sourceId, currentDownload.chapterUrl)
            val downloadPages = fetchedPages.distinctBy { it.index }.mapIndexed { index, page ->
                DownloadPage(
                    index = index,
                    url = page.url,
                    imageUrl = page.imageUrl,
                    headers = page.headers,
                    status = PageStatus.QUEUE,
                )
            }
            updateDownload(currentDownload.chapterId) { it.copy(pages = downloadPages) }
            downloadPages
        } else {
            currentDownload.pages
        }

        if (pages.isEmpty()) {
            throw IOException("Page list is empty for chapter ${download.chapterName}")
        }

        val tempDir = diskProvider.getTempChapterDir(
            download.sourceId,
            download.mangaTitle,
            download.chapterName,
            download.mangaId,
            download.chapterId,
        )
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir)
        }

        diskProvider.cleanPartialPages(tempDir)
        diskProvider.restoreLegacyTemporaryPages(download, tempDir)
        // A previous run may have published the images before database registration failed.
        // Only reuse pages the saved queue marked ready, and validate their bytes again.
        val publishedDir =
            diskProvider.findChapterEntry(
                download.sourceId,
                download.mangaTitle,
                download.chapterName,
                download.mangaId,
                download.chapterId,
            )?.path
                ?: diskProvider.getChapterDir(
                    download.sourceId,
                    download.mangaTitle,
                    download.chapterName,
                    download.mangaId,
                    download.chapterId,
                )
        pages.filter { it.status == PageStatus.READY }.forEach { page ->
            if (diskProvider.validPageMetadata(tempDir, page.index, page.expectedPartCount()) != null) return@forEach
            val publishedFiles = diskProvider.pageFiles(publishedDir, page.index)
            if (publishedFiles.isEmpty() || publishedFiles.any { !diskProvider.isValidPage(it) }) return@forEach
            publishedFiles.forEach { published ->
                Files.copy(
                    published,
                    tempDir.resolve(published.fileName.toString()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        val pageSizes = mutableMapOf<Int, Long>()
        for (page in pages) {
            val stored = diskProvider.validPageMetadata(tempDir, page.index, page.expectedPartCount()) ?: continue
            pageSizes[page.index] = stored.sizeBytes
        }
        val totalBytes = AtomicLong(pageSizes.values.sum())
        updateDownload(download.chapterId) { item ->
            item.copy(
                bytesDownloaded = totalBytes.get(),
                pages = item.pages.map { page ->
                    val size = pageSizes[page.index]
                    if (size != null) {
                        page.copy(
                            status = PageStatus.READY,
                            progress = 1f,
                            bytesWritten = size,
                            error = null,
                        )
                    } else {
                        page.copy(status = PageStatus.QUEUE, progress = 0f, error = null)
                    }
                },
            )
        }
        val pageFailures = ConcurrentHashMap<Int, Exception>()
        val pageSemaphore = Semaphore(pageParallelism().coerceIn(1, 32))
        coroutineScope {
            pages.map { page ->
                async {
                    pageSemaphore.withPermit {
                        val existingSize = pageSizes[page.index]
                        if (existingSize != null) {
                            updatePageStatus(
                                download.chapterId,
                                page.index,
                                PageStatus.READY,
                                1.0f,
                                bytesWritten = existingSize,
                            )
                            return@withPermit
                        }

                        updatePageStatus(download.chapterId, page.index, PageStatus.DOWNLOADING, 0.1f)
                        try {
                            val bytes = mihon.desktop.extension.downloadSourcePage(
                                download.sourceId,
                                Page(page.index, page.url, page.imageUrl, page.headers),
                                download.chapterUrl,
                                networkHelper,
                                sourceManager,
                                processManager,
                                priority = mihon.extension.ipc.RequestPriority.BACKGROUND,
                            )
                            currentCoroutineContext().ensureActive()
                            val savedPage = diskProvider.savePage(tempDir, page.index, bytes)
                            // Splitting is best effort: it never fails the download, and the byte
                            // totals below follow what is actually on disk afterwards.
                            val split = tallImageSplitter?.split(savedPage, page.index)
                            val storedBytes = (split as? TallImageSplitResult.Split)?.totalBytes
                                ?: bytes.size.toLong()

                            val downloadedBytes = totalBytes.addAndGet(storedBytes)
                            val allSessionBytes = sessionBytes.addAndGet(storedBytes)
                            val elapsedSeconds = (System.currentTimeMillis() - sessionStartedAt()) / 1000.0
                            if (elapsedSeconds > 0.1) {
                                onSpeed(allSessionBytes / elapsedSeconds)
                            }

                            updatePageStatus(
                                download.chapterId,
                                page.index,
                                PageStatus.READY,
                                1.0f,
                                bytesWritten = storedBytes,
                                partCount = (split as? TallImageSplitResult.Split)?.partCount,
                            )
                            val progress = updateDownload(download.chapterId) {
                                it.copy(bytesDownloaded = downloadedBytes)
                            }
                            progress?.let { onProgress?.invoke(it) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            updatePageStatus(
                                download.chapterId,
                                page.index,
                                PageStatus.ERROR,
                                0f,
                                error.message,
                            )
                            pageFailures[page.index] = error
                        }
                    }
                }
            }.forEach { it.await() }
        }

        if (pageFailures.isNotEmpty()) {
            val first = pageFailures.minBy { it.key }
            throw IOException(
                "${pageFailures.size} page(s) failed; page ${first.key + 1}: ${first.value.message}",
                first.value,
            )
        }

        currentCoroutineContext().ensureActive()
        // Finalize chapter atomically and register in database
        val pagePartCounts = queueState.value.firstOrNull { it.chapterId == download.chapterId }
            ?.pages?.associate { it.index to it.partCount }.orEmpty()
        diskProvider.finalizeChapter(
            sourceId = download.sourceId,
            mangaId = download.mangaId,
            chapterId = download.chapterId,
            mangaTitle = download.mangaTitle,
            chapterName = download.chapterName,
            totalPages = pages.size,
            mutationPort = mutationPort,
            pagePartCounts = pagePartCounts,
        )

        // Cancelling or pausing is not observable inside the blocking finalize step. If it landed
        // while the chapter was being published, remove the published chapter again instead of
        // leaving a download the queue does not know about.
        val queued = queueState.value.firstOrNull { it.chapterId == download.chapterId }
        if (queued == null || queued.status == DownloadStatus.PAUSED || !currentCoroutineContext().isActive) {
            rollbackPublishedChapter(download)
            currentCoroutineContext().ensureActive()
            return
        }

        val completedDownload = updateDownload(download.chapterId) {
            it.copy(status = DownloadStatus.COMPLETED, progress = 1.0f, storageLayoutVersion = 1)
        }
        if (completedDownload == null) {
            // The queue row was removed after the check above (cancel raced the publish).
            rollbackPublishedChapter(download)
        } else {
            onCompleted?.invoke(completedDownload)
        }
    }

    /** Removes a chapter that was published after the user paused or cancelled it. */
    private fun rollbackPublishedChapter(download: DesktopDownload) {
        runCatching {
            diskProvider.deleteChapter(
                download.sourceId,
                download.mangaTitle,
                download.chapterName,
                download.mangaId,
                download.chapterId,
            )
        }.onFailure {
            mihon.desktop.logging.DesktopLogger.warn(
                "Downloader",
                "Cannot remove cancelled chapter ${download.chapterId}",
                it,
            )
        }
        mutationPort?.let { port ->
            runCatching { port.deleteLocalChapterAsset(download.mangaId, download.chapterId) }
                .onFailure {
                    mihon.desktop.logging.DesktopLogger.warn(
                        "Downloader",
                        "Cannot unregister cancelled chapter ${download.chapterId}",
                        it,
                    )
                }
        }
    }

    private suspend fun fetchPages(sourceId: Long, chapterUrl: String): List<Page> {
        if (pageListFetcher != null) {
            return pageListFetcher.invoke(sourceId, chapterUrl)
        }
        val chapter = SChapter(url = chapterUrl, name = "")
        if (sourceManager != null) {
            return sourceManager.getPageList(sourceId, chapter)
        }
        if (processManager != null) {
            return processManager.getPageList(sourceId, chapter)
        }
        throw IllegalStateException("No page list fetcher available")
    }

    internal fun updateDownload(
        chapterId: Long,
        persistImmediately: Boolean = false,
        transform: (DesktopDownload) -> DesktopDownload,
    ): DesktopDownload? {
        var updatedItem: DesktopDownload? = null
        queueState.update { list ->
            val index = list.indexOfFirst { it.chapterId == chapterId }
            if (index == -1) return@update list
            val item = list[index]
            val updated = transform(item)
            val readyCount = updated.pages.count { it.status == PageStatus.READY }
            val progress = if (updated.pages.isNotEmpty()) readyCount.toFloat() / updated.pages.size else 0f
            val finalItem = updated.copy(progress = progress)
            updatedItem = finalItem
            val mutable = list.toMutableList()
            mutable[index] = finalItem
            mutable
        }
        val item = updatedItem
        val persistNow =
            persistImmediately ||
                (item != null && (item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.ERROR))
        if (persistNow) {
            persistence.persistImmediate()
        } else {
            persistence.scheduleDebounced()
        }
        return item
    }

    private fun updatePageStatus(
        chapterId: Long,
        pageIndex: Int,
        status: PageStatus,
        progress: Float,
        error: String? = null,
        bytesWritten: Long? = null,
        partCount: Int? = null,
    ) {
        updateDownload(chapterId) { download ->
            val pageIdx = download.pages.indexOfFirst { it.index == pageIndex }
            if (pageIdx == -1) return@updateDownload download
            val mutablePages = download.pages.toMutableList()
            val p = mutablePages[pageIdx]
            mutablePages[pageIdx] = p.copy(
                status = status,
                progress = progress,
                error = error,
                bytesWritten = bytesWritten ?: p.bytesWritten,
                partCount = partCount ?: p.partCount,
            )
            download.copy(pages = mutablePages)
        }
    }

    private fun DownloadPage.expectedPartCount(): Int? = partCount.takeIf { it > 1 }
}
