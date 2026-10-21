package mihon.desktop.download

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.tasks.dragReorderTarget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DownloadGroupActionsTest {
    private fun manga(id: Long) = LibraryManga(
        id = id,
        sourceId = 100L,
        url = "/manga/$id",
        title = "Manga $id",
        thumbnailUrl = null,
        chapterCount = 3L,
        unreadCount = 3L,
        author = null,
    )

    private fun chapter(id: Long, mangaId: Long) = LibraryChapter(
        id = id,
        mangaId = mangaId,
        url = "/chapter/$id",
        name = "Chapter $id",
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

    private fun downloader(tempDir: Path, store: DownloadStore) = DesktopDownloader(
        store = store,
        diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
        networkHelper = DesktopNetworkHelper(),
        pageListFetcher = { _, _ -> emptyList() },
    )

    @Test
    fun `group actions pause, move to top and cancel a whole manga`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val downloader = downloader(tempDir, store)
        downloader.pause()
        downloader.enqueue(manga(1), listOf(chapter(11, 1), chapter(12, 1)))
        downloader.enqueue(manga(2), listOf(chapter(21, 2), chapter(22, 2)))

        downloader.pauseManga(2L) shouldBe 2
        downloader.queueState.value.filter { it.mangaId == 2L }.map { it.status }.toSet() shouldBe
            setOf(DownloadStatus.PAUSED)

        downloader.moveMangaToTop(2L) shouldBe true
        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(21L, 22L, 11L, 12L)
        store.restore().map { it.chapterId } shouldContainExactly listOf(21L, 22L, 11L, 12L)
        downloader.moveMangaToTop(2L) shouldBe false

        downloader.cancelManga(1L).map { it.chapterId } shouldContainExactly listOf(11L, 12L)
        downloader.queueState.value.map { it.chapterId } shouldContainExactly listOf(21L, 22L)
        store.restore().map { it.chapterId } shouldContainExactly listOf(21L, 22L)
        downloader.shutdown()
    }

    @Test
    fun `group retry only touches failed chapters of that manga`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("queue.json"))
        val downloader = downloader(tempDir, store)
        downloader.pause()
        downloader.enqueue(manga(1), listOf(chapter(11, 1)))
        downloader.retryManga(1L) shouldBe 0
        downloader.shutdown()
    }

    @Test
    fun `drag target index follows the pointer and is clamped`() {
        dragReorderTarget(fromIndex = 2, dragOffsetPx = 0f, itemExtentPx = 100f, lastIndex = 5) shouldBe 2
        dragReorderTarget(fromIndex = 2, dragOffsetPx = 49f, itemExtentPx = 100f, lastIndex = 5) shouldBe 2
        dragReorderTarget(fromIndex = 2, dragOffsetPx = 51f, itemExtentPx = 100f, lastIndex = 5) shouldBe 3
        dragReorderTarget(fromIndex = 2, dragOffsetPx = -160f, itemExtentPx = 100f, lastIndex = 5) shouldBe 0
        dragReorderTarget(fromIndex = 2, dragOffsetPx = -1000f, itemExtentPx = 100f, lastIndex = 5) shouldBe 0
        dragReorderTarget(fromIndex = 2, dragOffsetPx = 1000f, itemExtentPx = 100f, lastIndex = 5) shouldBe 5
        dragReorderTarget(fromIndex = 2, dragOffsetPx = 80f, itemExtentPx = 0f, lastIndex = 5) shouldBe 2
    }
}
