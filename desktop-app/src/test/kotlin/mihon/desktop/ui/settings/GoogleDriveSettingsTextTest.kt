package mihon.desktop.ui.settings

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.i18n.TraditionalChineseStrings
import mihon.desktop.sync.gdrive.GoogleDriveError
import org.junit.jupiter.api.Test

class GoogleDriveSettingsTextTest {
    private val locales = listOf(EnglishStrings, SimplifiedChineseStrings, TraditionalChineseStrings)

    @Test
    fun `every error maps to a distinct non-blank message in every locale`() {
        locales.forEach { strings ->
            val texts = GoogleDriveError.entries.map { gdriveErrorText(strings, it) }
            texts.all { it.isNotBlank() } shouldBe true
            texts.toSet().size shouldBe GoogleDriveError.entries.size
        }
    }

    @Test
    fun `chinese section is titled Google Drive sync and network error is friendly`() {
        SimplifiedChineseStrings.gdriveTitle shouldBe "Google Drive 同步"
        gdriveErrorText(SimplifiedChineseStrings, GoogleDriveError.NETWORK) shouldContain "代理"
    }

    @Test
    fun `status line shows never before first sync and formatted time after`() {
        gdriveLastSyncText(SimplifiedChineseStrings, 0, "") shouldBe "上次同步：" + SimplifiedChineseStrings.syncNever
        gdriveLastSyncText(EnglishStrings, 1_700_000_000_000, "↓1 ↑0 · 3") shouldContain "(↓1 ↑0 · 3)"
    }
}
