package mihon.desktop.download

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopDownloaderUndoTest {

    private fun createManga(id: Long = 1L, sourceId: Long = 100L, title: String = "Test Manga"): LibraryManga {
        return LibraryManga(
            id = id,
            sourceId = sourceId,
            url = "/manga/test",
            title = title,
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 1L,
            author = null,
        )
    }

    private fun createChapter(id: Long = 101L, mangaId: Long = 1L, name: String = "Chapter 101"): LibraryChapter {
        return LibraryChapter(
            id = id,
            mangaId = mangaId,
            url = "/chapter/101",
            name = name,
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )
    }

    @Test
    fun `cancel returns download and restoreDownloads re-adds it to queue`(@TempDir tempDir: Path) = runBlocking {
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("undo-queue.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("undo-downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val manga = createManga(id = 1L)
        val chapter = createChapter(id = 101L, mangaId = 1L)

        downloader.pause()
        downloader.enqueue(manga, listOf(chapter))
        downloader.queueState.value shouldHaveSize 1

        val cancelled = downloader.cancel(101L)
        cancelled shouldNotBe null
        cancelled?.chapterId shouldBe 101L
        downloader.queueState.value shouldHaveSize 0

        downloader.restoreDownloads(listOf(cancelled!!))
        downloader.queueState.value shouldHaveSize 1
        downloader.queueState.value.first().chapterId shouldBe 101L

        downloader.shutdown()
    }

    @Test
    fun `clearCompleted returns cleared downloads and restoreDownloads restores them`(@TempDir tempDir: Path) {
        val store = DownloadStore(tempDir.resolve("completed-queue.json"))
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = DownloadDiskProvider(tempDir.resolve("completed-downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val completedDownload = DesktopDownload(
            chapterId = 201L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Completed Manga",
            chapterName = "Chapter 1",
            chapterUrl = "/c/1",
            status = DownloadStatus.COMPLETED,
            progress = 1f,
        )

        downloader.restoreDownloads(listOf(completedDownload))
        downloader.queueState.value shouldHaveSize 1

        val cleared = downloader.clearCompleted()
        cleared shouldHaveSize 1
        cleared.first().chapterId shouldBe 201L
        downloader.queueState.value shouldHaveSize 0

        downloader.restoreDownloads(cleared)
        downloader.queueState.value shouldHaveSize 1
        downloader.queueState.value.first().chapterId shouldBe 201L
        downloader.queueState.value.first().status shouldBe DownloadStatus.COMPLETED

        downloader.close()
    }

    @Test
    fun `cancel on paused download and restoreDownloads preserves PAUSED status`(@TempDir tempDir: Path) = runBlocking {
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("paused-undo-queue.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("paused-undo-downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val manga = createManga(id = 1L)
        val chapter = createChapter(id = 301L, mangaId = 1L)

        downloader.pause()
        downloader.enqueue(manga, listOf(chapter))
        downloader.pause(301L) shouldBe true
        downloader.queueState.value.first().status shouldBe DownloadStatus.PAUSED

        val cancelled = downloader.cancel(301L)
        cancelled shouldNotBe null
        cancelled?.status shouldBe DownloadStatus.PAUSED
        downloader.queueState.value shouldHaveSize 0

        downloader.restoreDownloads(listOf(cancelled!!))
        downloader.queueState.value shouldHaveSize 1
        downloader.queueState.value.first().chapterId shouldBe 301L
        downloader.queueState.value.first().status shouldBe DownloadStatus.PAUSED

        downloader.shutdown()
    }

    @Test
    fun `restoreDownloads maps DOWNLOADING status back to QUEUED`(@TempDir tempDir: Path) = runBlocking {
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("active-undo-queue.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("active-undo-downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val downloadingItem = DesktopDownload(
            chapterId = 401L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Active Manga",
            chapterName = "Chapter 1",
            chapterUrl = "/c/1",
            status = DownloadStatus.DOWNLOADING,
            progress = 0.4f,
        )

        downloader.restoreDownloads(listOf(downloadingItem))
        downloader.queueState.value shouldHaveSize 1
        downloader.queueState.value.first().chapterId shouldBe 401L
        // The restored queue restarts immediately, so the item may already be claimed again.
        downloader.queueState.value.first().status shouldBeIn
            listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)

        downloader.shutdown()
    }

    @Test
    fun `restoreDownloads restarts a drained queue so restored items get processed`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("drained-undo-queue.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("drained-undo-downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )
        try {
            val manga = createManga(id = 1L)
            val chapter = createChapter(id = 501L, mangaId = 1L)

            // Enqueue without starting: the queue is drained (isRunning == false), which is the
            // state in which restored items used to sit in QUEUED forever.
            downloader.enqueue(manga, listOf(chapter), autoStart = false)
            downloader.queueState.value.single().status shouldBe DownloadStatus.QUEUED
            downloader.isRunning.value shouldBe false

            val cancelled = downloader.cancel(501L)
            cancelled shouldNotBe null
            downloader.queueState.value shouldHaveSize 0

            downloader.restoreDownloads(listOf(cancelled!!))
            downloader.queueState.value.single().chapterId shouldBe 501L
            // The restored item may already be claimed by the restarted loop.
            downloader.queueState.value.single().status shouldBeIn
                listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)

            // Regression: before the fix the restored item stayed QUEUED forever because a drained
            // queue was never restarted. Now it must be picked up and processed again (the empty
            // page list makes processing fail fast with ERROR).
            withTimeout(5_000) {
                while (downloader.queueState.value.any { it.status == DownloadStatus.QUEUED }) {
                    delay(50)
                }
            }
            downloader.queueState.value.single().status shouldBe DownloadStatus.ERROR
        } finally {
            downloader.shutdown()
        }
    }
}
