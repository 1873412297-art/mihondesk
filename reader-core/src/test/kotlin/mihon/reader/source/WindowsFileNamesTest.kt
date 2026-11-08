package mihon.reader.source

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WindowsFileNamesTest {
    @Test
    fun `reserved characters and control characters become underscores`() {
        WindowsFileNames.sanitize("Manga: Volume 1 / Part 2? *Special* <Final>") shouldBe
            "Manga_ Volume 1 _ Part 2_ _Special_ _Final_"
        WindowsFileNames.sanitize("a\\b|c\"d") shouldBe "a_b_c_d"
        WindowsFileNames.sanitize("tab\there\u0000nul\u001fend") shouldBe "tab_here_nul_end"
    }

    @Test
    fun `trailing dots and spaces are removed because win32 strips them silently`() {
        WindowsFileNames.sanitize("Chapter 1. ") shouldBe "Chapter 1"
        WindowsFileNames.sanitize("end...") shouldBe "end"
        WindowsFileNames.sanitize("  padded  ") shouldBe "padded"
    }

    @Test
    fun `names with nothing usable fall back to a placeholder`() {
        for (name in listOf("", " ", ".", "..", "...", ". .")) {
            WindowsFileNames.sanitize(name) shouldBe "unnamed"
        }
    }

    @Test
    fun `device names are reserved with any case and any extension`() {
        val reserved = listOf(
            "CON", "con", "PRN", "AUX", "NUL", "nul.txt", "COM1", "com9.log", "LPT1", "lpt9",
            "CONIN$", "CONOUT$", "COM¹", "LPT²", "COM³.png", "AUX .txt",
        )
        for (name in reserved) {
            WindowsFileNames.isReservedDeviceName(name) shouldBe true
            WindowsFileNames.sanitize(name).startsWith("_") shouldBe true
        }
    }

    @Test
    fun `ordinary names that only resemble device names are kept`() {
        for (name in listOf("CONSOLE", "COM0", "COM10", "LPT0", "nullable", "Con Air", "AUXILIARY.txt")) {
            WindowsFileNames.sanitize(name) shouldBe name
        }
    }

    @Test
    fun `long names are cut without splitting a surrogate pair`() {
        WindowsFileNames.sanitize("a".repeat(500)).length shouldBe WindowsFileNames.MAX_LENGTH

        // An emoji is two UTF-16 units; one starting at the last allowed index must not be halved.
        val emoji = "😀"
        val cut = WindowsFileNames.sanitize("a".repeat(WindowsFileNames.MAX_LENGTH - 1) + emoji + "tail")
        cut shouldBe "a".repeat(WindowsFileNames.MAX_LENGTH - 1)
        cut.none(Character::isSurrogate) shouldBe true
    }

    @Test
    fun `truncation that exposes a trailing dot still removes it`() {
        val name = "a".repeat(WindowsFileNames.MAX_LENGTH - 1) + ". tail"
        WindowsFileNames.sanitize(name) shouldBe "a".repeat(WindowsFileNames.MAX_LENGTH - 1)
    }

    @Test
    fun `cjk titles are preserved`() {
        WindowsFileNames.sanitize("进击的巨人：最终季") shouldBe "进击的巨人：最终季"
    }

    @Test
    fun `every sanitized name is a creatable single path component`(@TempDir temp: Path) {
        val hostile = listOf("..", "a/../b", "C:evil", "\\\\server\\share", "x".repeat(400), "nul.", "CON ")
        for (name in hostile) {
            val safe = WindowsFileNames.sanitize(name)
            val created = Files.createDirectories(temp.resolve(safe))
            created.parent shouldBe temp
        }
    }
}
