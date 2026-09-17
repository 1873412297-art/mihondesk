package mihon.desktop.download

import io.kotest.matchers.shouldBe
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadStorageSafetyTest {
    @org.junit.jupiter.api.BeforeEach
    fun verifyInstalledClassOriginWhenRequested() {
        System.getenv("MIHON_AUDIT_INSTALLED_APP")?.let { installed ->
            listOf(DownloadDiskProvider::class.java, DesktopLibraryDatabaseFactory::class.java).forEach { type ->
                val origin = Path.of(type.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
                origin.startsWith(Path.of(installed).toAbsolutePath().normalize()) shouldBe true
                println("AUDIT_INSTALLED_CLASS ${type.name} $origin")
            }
        }
    }

    @Test
    fun `ambiguous legacy names cannot be claimed by two different chapter IDs`(@TempDir temp: Path) {
        val disk = DownloadDiskProvider(temp.resolve("downloads"))
        val legacy = disk.getChapterDir(42, "Manga", "Chapter")
        disk.savePage(legacy, 0, validDownloadImage())
        disk.adoptLegacyDownloads(
            listOf(11L, 12L).map { DesktopDownload(it, 1, 42, "Manga", "Chapter", "/$it") },
        )
        disk.findChapterDir(42, "Manga", "Chapter", 1, 11) shouldBe null
        disk.findChapterDir(42, "Manga", "Chapter", 1, 12) shouldBe null
        Files.exists(legacy.resolve("001.jpg")) shouldBe true
    }

    @Test
    fun `changing download root keeps earlier chapters readable`(@TempDir temp: Path) {
        DesktopLibraryDatabaseFactory.open(temp.resolve("library.db")).use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 42, url = "/manga", title = "Manga"))
            val firstId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/1", name = "Chapter 1"))
            val secondId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/2", name = "Chapter 2"))
            fun publish(root: String, chapterId: Long, name: String): Path {
                val disk = DownloadDiskProvider(temp.resolve(root))
                disk.savePage(disk.getTempChapterDir(42, "Manga", name, mangaId, chapterId), 0, validDownloadImage())
                return disk.finalizeChapter(42, mangaId, chapterId, "Manga", name, 1, repository)
            }
            val first = publish("old-root", firstId, "Chapter 1")
            publish("new-root", secondId, "Chapter 2")

            val asset = requireNotNull(repository.chapterAsset(firstId))
            asset.storageRoot.resolve(asset.relativePath) shouldBe first
            Files.exists(first.resolve("001.jpg")) shouldBe true
        }
    }

    @Test
    fun `publishing another chapter with the same name preserves the first chapter`(@TempDir temp: Path) {
        val disk = DownloadDiskProvider(temp.resolve("downloads"))
        fun publish(chapterId: Long): Path {
            disk.savePage(disk.getTempChapterDir(42, "Manga", "Chapter 1", 1, chapterId), 0, validDownloadImage())
            return disk.finalizeChapter(42, 1, chapterId, "Manga", "Chapter 1", 1)
        }
        val first = publish(11)
        val marker = Files.writeString(first.resolve("keep-first-chapter"), "first")

        publish(12)

        Files.exists(marker) shouldBe true
    }

    @Test
    fun `dot chapter name cannot delete neighboring manga downloads`(@TempDir temp: Path) {
        val root = temp.resolve("downloads")
        val disk = DownloadDiskProvider(root)
        val sibling = root.resolve("42/Other Manga/Chapter 1")
        Files.createDirectories(sibling)
        Files.writeString(sibling.resolve("keep.txt"), "unrelated download")
        Files.createDirectories(root.resolve("42/Manga"))
        // Even the broken implementation is confined to this disposable test root.
        check(disk.getChapterDir(42, "Manga", "..").normalize().startsWith(temp))

        disk.deleteChapter(42, "Manga", "..")

        Files.exists(sibling.resolve("keep.txt")) shouldBe true
    }

    @Test
    fun `source supplied names remain single usable Windows path components`(@TempDir temp: Path) {
        val disk = DownloadDiskProvider(temp.resolve("downloads"))
        for (name in listOf(".", "..", "...", "CON", "NUL.txt", "aux", "LPT1.", "bad\u0000name", "end. ")) {
            val safe = disk.sanitizeFileName(name)
            (safe.isNotBlank() && safe != "." && safe != ".." && !safe.endsWith('.') && !safe.endsWith(' ')) shouldBe
                true
            safe.none { it.code < 32 || it in "\\/:*?\"<>|" } shouldBe true
            Files.createDirectories(temp.resolve("names").resolve(safe))
        }
    }
}
