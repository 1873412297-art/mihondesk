package mihon.desktop.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.LibraryChapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopDownloaderCancelRaceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `processDownload does not throw NoSuchElementException when download was cancelled and removed`() =
        runBlocking {
            val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
            val store = DownloadStore(tempDir.resolve("queue.json"))
            val networkHelper = DesktopNetworkHelper()
            val downloader = DesktopDownloader(store, disk, networkHelper)
            try {
                val cancelledDownload = DesktopDownload(
                    chapterId = 999L,
                    mangaId = 1L,
                    sourceId = 100L,
                    mangaTitle = "Test Manga",
                    chapterName = "Chapter 1",
                    chapterUrl = "/chapter/1",
                    pages = emptyList(),
                    status = DownloadStatus.DOWNLOADING,
                )

                // Queue does NOT contain chapterId 999L (it was cancelled/removed)
                downloader.queueState.value shouldBe emptyList()

                // Prior to fix, calling processDownload would throw NoSuchElementException.
                // With fix, it logs a warning and returns cleanly without exception.
                downloader.processDownload(cancelledDownload)
            } finally {
                downloader.shutdown()
            }
        }

    @Test
    fun `concurrent cancel during enqueue and start does not throw uncaught exceptions`() = runBlocking {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val networkHelper = DesktopNetworkHelper()
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = disk,
            networkHelper = networkHelper,
            pageListFetcher = { _, _ ->
                delay(50)
                emptyList()
            },
        )
        try {
            val chapter = LibraryChapter(
                id = 42L,
                mangaId = 1L,
                url = "/ch/42",
                name = "Chapter 42",
                scanlator = null,
                read = false,
                bookmark = false,
                lastPageRead = 0L,
                dateFetch = 0L,
                dateUpload = 0L,
                chapterNumber = 42.0,
                sourceOrder = 1L,
                lastModifiedAt = 0L,
                version = 1L,
                memoJson = "{}",
            )

            val enqueueJob = launch {
                downloader.enqueue(
                    sourceId = 10L,
                    mangaId = 1L,
                    mangaTitle = "Racing Manga",
                    chapters = listOf(chapter),
                )
            }
            val cancelJob = launch {
                downloader.cancel(42L)
            }
            enqueueJob.join()
            cancelJob.join()

            downloader.start()
            delay(100)
            downloader.pause()
        } finally {
            downloader.shutdown()
        }
    }
}
