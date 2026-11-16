package mihon.desktop.updates

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.ui.settings.inlineMarkdown
import org.junit.jupiter.api.Test

class ReleaseNotesTest {

    /** Shape of the published v0.4.0 body: CRLF lines, install section, then the companion block. */
    private val v040 = listOf(
        "## mihondesk 0.4.0",
        "",
        "本次是一次较大的稳定性与安全更新。",
        "",
        "- **架构与质量**：桌面端所有 Kotlin 文件重新拆分到 400 行以内。",
        "- **安全**：拒绝扩展包 ZIP 路径穿越。",
        "",
        "### 安装",
        "",
        "- `mihondesk-0.4.0.exe`：推荐，下载后按向导安装",
        "- 下载后请使用 `SHA256SUMS.txt` 校验文件完整性",
        "",
    ).joinToString("\r\n") + "\n\n<!-- companion-notes:begin -->\n### Android 伴侣应用（测试版）\n\n" +
        "- `mihondesk-companion-0.2.1.apk`\n\n**SHA-256**\n\n```\n03c50c1f  mihondesk-companion-0.2.1.apk\n```\n" +
        "<!-- companion-notes:end -->\n"

    @Test
    fun `keeps the change notes and drops the title, install section and companion block`() {
        releaseNoteBlocks(v040) shouldContainExactly listOf(
            ReleaseNoteBlock.Paragraph("本次是一次较大的稳定性与安全更新。"),
            ReleaseNoteBlock.Bullet("**架构与质量**：桌面端所有 Kotlin 文件重新拆分到 400 行以内。"),
            ReleaseNoteBlock.Bullet("**安全**：拒绝扩展包 ZIP 路径穿越。"),
        )
    }

    @Test
    fun `a marked companion block is dropped even without an install section before it`() {
        val body = "- Fix sync\n\n" +
            "<!-- companion-notes:begin -->\n- Companion only\n<!-- companion-notes:end -->\n" +
            "- Faster reader"
        releaseNoteBlocks(body).map { it.text } shouldContainExactly listOf("Fix sync", "Faster reader")
    }

    @Test
    fun `section headings stay unless they only name the release, and code blocks are skipped`() {
        val body = """
            ### New
            - Update reminders
              that span two lines
            ```
            checksum  file.exe
            ```
            ### Fixes
            1. Sync no longer hangs
            ## Installation
            - Download the exe
        """.trimIndent()
        releaseNoteBlocks(body) shouldContainExactly listOf(
            ReleaseNoteBlock.Heading("New"),
            ReleaseNoteBlock.Bullet("Update reminders that span two lines"),
            ReleaseNoteBlock.Heading("Fixes"),
            ReleaseNoteBlock.Bullet("Sync no longer hangs"),
        )
    }

    @Test
    fun `a body with only install instructions has no change notes`() {
        releaseNoteBlocks("").shouldBeEmpty()
        releaseNoteBlocks("## v0.5.0\n\n### 安装\n- 下载 exe").shouldBeEmpty()
    }

    @Test
    fun `inline markdown keeps the words and drops the markers`() {
        val styled = inlineMarkdown("**安全**：校验 `SHA256SUMS.txt`，详见 [说明](https://example.invalid)")
        styled.text shouldBe "安全：校验 SHA256SUMS.txt，详见 说明"
        styled.text shouldNotContain "**"
        styled.spanStyles.size shouldBe 2
    }
}
