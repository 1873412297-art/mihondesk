package mihon.desktop.download

import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadOrphanCleanupTest {
    @TempDir
    lateinit var tempDir: Path

    private fun chapterWithPage(dir: Path, bytes: String = "12345") {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("001.jpg"), bytes)
    }

    @Test
    fun `deletes downloads of manga that are no longer in the library`() {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val kept = MangaRecord(id = 1, sourceId = 100, url = "/a", title = "Kept", favorite = true)
        val removed = MangaRecord(id = 2, sourceId = 100, url = "/b", title = "Removed", favorite = false)
        val keptDir = disk.getChapterDir(100, kept.title, "c1", kept.id, 11)
        val removedDir = disk.getChapterDir(100, removed.title, "c1", removed.id, 21)
        val unknownDir = disk.getChapterDir(100, "Gone", "c1", 99, 991)
        val queuedDir = disk.getChapterDir(100, "Queued", "c1", 50, 501)
        val legacyKept = disk.downloadsDir.resolve("100").resolve("Kept").resolve("Chapter 1")
        val legacyGone = disk.downloadsDir.resolve("100").resolve("Legacy Gone").resolve("Chapter 1")
        listOf(keptDir, removedDir, unknownDir, queuedDir, legacyKept, legacyGone).forEach { chapterWithPage(it) }

        val report = DownloadCacheCleaner.deleteDownloadsNotInLibrary(
            manga = listOf(kept, removed),
            diskProvider = disk,
            protectedMangaIds = setOf(50L),
        )

        report.deletedChaptersCount shouldBe 3
        report.freedBytes shouldBe 15L
        Files.exists(keptDir) shouldBe true
        Files.exists(queuedDir) shouldBe true
        Files.exists(legacyKept) shouldBe true
        Files.exists(removedDir.parent) shouldBe false
        Files.exists(unknownDir.parent) shouldBe false
        Files.exists(legacyGone.parent) shouldBe false
    }

    @Test
    fun `preview counts orphans without deleting`() {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val orphan = disk.getChapterDir(7, "X", "c1", 70, 701)
        chapterWithPage(orphan)
        val report = DownloadCacheCleaner.deleteDownloadsNotInLibrary(emptyList(), disk, dryRun = true)
        report.deletedChaptersCount shouldBe 1
        Files.exists(orphan) shouldBe true
    }
}
