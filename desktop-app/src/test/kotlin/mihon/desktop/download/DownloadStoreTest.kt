package mihon.desktop.download

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DownloadStoreTest {

    @Test
    fun `save and restore queue successfully`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)

        val queue = listOf(
            DesktopDownload(
                chapterId = 101L,
                mangaId = 1L,
                sourceId = 10L,
                mangaTitle = "One Piece",
                chapterName = "Chapter 1000",
                chapterUrl = "/ch1000",
                pages = listOf(
                    DownloadPage(index = 0, url = "https://example.com/0.jpg", status = PageStatus.READY),
                    DownloadPage(index = 1, url = "https://example.com/1.jpg", status = PageStatus.QUEUE),
                ),
                status = DownloadStatus.QUEUED,
                progress = 0.5f,
            ),
        )

        store.save(queue)
        val restored = store.restore()

        restored shouldHaveSize 1
        val item = restored.first()
        item.chapterId shouldBe 101L
        item.mangaTitle shouldBe "One Piece"
        item.chapterName shouldBe "Chapter 1000"
        item.status shouldBe DownloadStatus.QUEUED
        item.pages shouldHaveSize 2
        item.downloadedImages shouldBe 1
    }

    @Test
    fun `resets DOWNLOADING status to QUEUED on restore for crash recovery`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)

        val queue = listOf(
            DesktopDownload(
                chapterId = 202L,
                mangaId = 2L,
                sourceId = 10L,
                mangaTitle = "Bleach",
                chapterName = "Chapter 1",
                chapterUrl = "/ch1",
                status = DownloadStatus.DOWNLOADING,
            ),
        )

        store.save(queue)
        val restored = store.restore()

        restored shouldHaveSize 1
        restored.first().status shouldBe DownloadStatus.QUEUED
    }

    @Test
    fun `restore non-existent file returns empty list`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("missing.json")
        val store = DownloadStore(storeFile)

        store.restore() shouldBe emptyList()
    }

    @Test
    fun `save produces compact JSON without pretty print`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)
        val queue = listOf(
            DesktopDownload(
                chapterId = 303L,
                mangaId = 3L,
                sourceId = 10L,
                mangaTitle = "Naruto",
                chapterName = "Chapter 1",
                chapterUrl = "/ch1",
                status = DownloadStatus.QUEUED,
            ),
        )
        store.save(queue)
        val text = java.nio.file.Files.readString(storeFile)
        text.contains("\n  \"chapterId\"") shouldBe false
        store.restore() shouldHaveSize 1
    }

    @Test
    fun `consecutive saves skip backup rewrite when known clean`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val bakFile = tempDir.resolve("downloads.json.bak")
        val store = DownloadStore(storeFile)
        val queue1 = listOf(
            DesktopDownload(
                chapterId = 1L,
                mangaId = 1L,
                sourceId = 1L,
                mangaTitle = "Manga",
                chapterName = "Ch 1",
                chapterUrl = "/1",
                status = DownloadStatus.QUEUED,
            ),
        )
        store.save(queue1)
        java.nio.file.Files.exists(bakFile) shouldBe false

        // Second save creates bakFile containing queue1
        val queue2 = queue1 + DesktopDownload(
            chapterId = 2L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Manga",
            chapterName = "Ch 2",
            chapterUrl = "/2",
            status = DownloadStatus.QUEUED,
        )
        store.save(queue2)
        java.nio.file.Files.exists(bakFile) shouldBe true
        val bakContentAfterSave2 = java.nio.file.Files.readString(bakFile)
        bakContentAfterSave2.contains("\"chapterId\":1") shouldBe true
        bakContentAfterSave2.contains("\"chapterId\":2") shouldBe false

        // Third save when clean should skip rewriting bakFile
        val queue3 = queue2 + DesktopDownload(
            chapterId = 3L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Manga",
            chapterName = "Ch 3",
            chapterUrl = "/3",
            status = DownloadStatus.QUEUED,
        )
        store.save(queue3)
        // bakFile was not rewritten, still contains original content
        val bakContentAfterSave3 = java.nio.file.Files.readString(bakFile)
        bakContentAfterSave3 shouldBe bakContentAfterSave2
        store.restore() shouldHaveSize 3
    }
}
