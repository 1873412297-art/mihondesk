package mihon.desktop.download

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopDownloaderQueueInteractionTest {

    private fun createManga(id: Long = 1L, sourceId: Long = 100L, title: String = "Test Manga"): LibraryManga {
        return LibraryManga(
            id = id,
            sourceId = sourceId,
            url = "/manga/test",
            title = title,
            thumbnailUrl = null,
            chapterCount = 3L,
            unreadCount = 3L,
            author = null,
        )
    }

    private fun createChapter(id: Long, mangaId: Long = 1L, name: String = "Chapter $id"): LibraryChapter {
        return LibraryChapter(
            id = id,
            mangaId = mangaId,
            url = "/chapter/$id",
            name = name,
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = id.toDouble(),
            sourceOrder = id,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )
    }

    @Test
    fun `pause and resume by chapterId updates status and persists`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val manga = createManga()
        val c1 = createChapter(101L)
        val c2 = createChapter(102L)

        downloader.pause()
        downloader.enqueue(manga, listOf(c1, c2), autoStart = false)
        downloader.queueState.value.map { it.status } shouldContainExactly listOf(
            DownloadStatus.QUEUED,
            DownloadStatus.QUEUED,
        )

        downloader.pause(101L) shouldBe true
        downloader.queueState.value.first { it.chapterId == 101L }.status shouldBe DownloadStatus.PAUSED
        downloader.queueState.value.first { it.chapterId == 102L }.status shouldBe DownloadStatus.QUEUED

        // Verify persistence
        val persistedAfterPause = store.restore()
        persistedAfterPause.first { it.chapterId == 101L }.status shouldBe DownloadStatus.PAUSED

        // Resume item
        downloader.resume(101L) shouldBe true
        val resumedStatus = downloader.queueState.value.first { it.chapterId == 101L }.status
        (resumedStatus == DownloadStatus.QUEUED || resumedStatus == DownloadStatus.DOWNLOADING) shouldBe true

        val persistedAfterResume = store.restore()
        persistedAfterResume.first { it.chapterId == 101L }.status shouldNotBe DownloadStatus.PAUSED

        downloader.close()
    }

    @Test
    fun `moveUp and moveDown reorders items and persists order`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val manga = createManga()
        val c1 = createChapter(101L)
        val c2 = createChapter(102L)
        val c3 = createChapter(103L)

        downloader.pause()
        downloader.enqueue(manga, listOf(c1, c2, c3))

        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(101L, 102L, 103L)

        // moveUp on first item returns false
        downloader.moveUp(101L) shouldBe false

        // moveUp on second item moves it to first
        downloader.moveUp(102L) shouldBe true
        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(102L, 101L, 103L)
        store.restore().map { it.chapterId } shouldContainExactly listOf(102L, 101L, 103L)

        // moveDown on last item returns false
        downloader.moveDown(103L) shouldBe false

        // moveDown on first item moves it to second
        downloader.moveDown(102L) shouldBe true
        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(101L, 102L, 103L)
        store.restore().map { it.chapterId } shouldContainExactly listOf(101L, 102L, 103L)

        // reorder directly
        downloader.reorder(0, 2) shouldBe true
        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(102L, 103L, 101L)
        store.restore().map { it.chapterId } shouldContainExactly listOf(102L, 103L, 101L)

        downloader.close()
    }

    @Test
    fun `paused items are not claimed while other queued items are processed`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
            networkHelper = DesktopNetworkHelper(),
            pageListFetcher = { _, _ -> emptyList() },
        )

        val manga = createManga()
        val c1 = createChapter(101L)
        val c2 = createChapter(102L)

        downloader.pause()
        downloader.enqueue(manga, listOf(c1, c2), autoStart = false)

        // Pause the first item
        downloader.pause(101L) shouldBe true

        // With c1 PAUSED and c2 QUEUED, c1 must stay PAUSED and not be claimed
        downloader.queueState.value.first { it.chapterId == 101L }.status shouldBe DownloadStatus.PAUSED
        downloader.queueState.value.first { it.chapterId == 102L }.status shouldBe DownloadStatus.QUEUED

        downloader.close()
    }
}
