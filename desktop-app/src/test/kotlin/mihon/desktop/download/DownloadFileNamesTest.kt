package mihon.desktop.download

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.ui.reader.readerPageFileName
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Covers the rule change from the 0.3.26 sanitizer to the shared Windows one. */
class DownloadFileNamesTest {
    @TempDir
    lateinit var tempDir: Path

    // 0.3.26 kept all 230 characters; the shared rule cuts at 200.
    private val longTitle = "长".repeat(230)

    @Test
    fun `names both rules agree on yield a single candidate`() {
        DownloadFileNames.candidates("Manga: Volume 1") shouldContainExactly listOf("Manga_ Volume 1")
    }

    @Test
    fun `names the rules disagree on yield the new name first and the old one second`() {
        DownloadFileNames.candidates("COM¹") shouldContainExactly listOf("_COM¹", "COM¹")
        DownloadFileNames.candidates(longTitle) shouldContainExactly listOf("长".repeat(200), longTitle)
    }

    @Test
    fun `a chapter downloaded under the old naming rule is still found`() {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val legacy = disk.downloadsDir.resolve("7").resolve(longTitle).resolve("Chapter 1")
        Files.createDirectories(legacy)

        disk.findChapterDir(7, longTitle, "Chapter 1") shouldBe legacy
        // New downloads get the shorter name.
        disk.getChapterDir(7, longTitle, "Chapter 1") shouldNotBe legacy
    }

    @Test
    fun `cleaning keeps a favourite whose folder uses the old naming rule`() {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val favourite = MangaRecord(id = 1, sourceId = 7, url = "/a", title = longTitle, favorite = true)
        val legacy = disk.downloadsDir.resolve("7").resolve(longTitle).resolve("Chapter 1")
        Files.createDirectories(legacy)
        Files.writeString(legacy.resolve("001.jpg"), "page")

        val report = DownloadCacheCleaner.deleteDownloadsNotInLibrary(listOf(favourite), disk)

        report.deletedChaptersCount shouldBe 0
        Files.exists(legacy.resolve("001.jpg")) shouldBe true
    }

    @Test
    fun `a saved page never suggests a reserved device name`() {
        val page = PageDescriptor(PageId("chapter", "pages/CON.jpg"), width = 10, height = 10)
        readerPageFileName(page, pageIndex = 0) shouldBe "_CON.png"
        val plain = PageDescriptor(PageId("chapter", "pages/001.jpg"), width = 10, height = 10)
        readerPageFileName(plain, pageIndex = 0) shouldBe "001.png"
    }
}
