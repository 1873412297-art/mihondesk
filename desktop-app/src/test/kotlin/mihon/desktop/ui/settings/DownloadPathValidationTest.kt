package mihon.desktop.ui.settings

import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.UiText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadPathValidationTest {
    @Test
    fun `invalid relative and file paths cannot replace the saved location`(@TempDir dir: Path) {
        validateDownloadPath("\u0000").error shouldBe UiText.DownloadPathInvalid
        validateDownloadPath("relative-folder").error shouldBe UiText.DownloadPathAbsolute
        val file = Files.writeString(dir.resolve("existing.txt"), "preserve")
        validateDownloadPath(file.toString()).error shouldBe UiText.DownloadPathNotDirectory
        validateDownloadPath(file.resolve("child").toString()).error shouldBe UiText.DownloadPathUnwritable
        Files.readString(file) shouldBe "preserve"
        validateDownloadPath("  ") shouldBe DownloadPathValidation()
    }
}
