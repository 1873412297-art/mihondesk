package mihon.desktop.library.model

import io.kotest.matchers.shouldBe
import mihon.reader.model.ReadingMode
import org.junit.jupiter.api.Test

class MangaReaderSettingsTest {

    @Test
    fun `readingModeFromViewerFlags correctly decodes Android and desktop modes`() {
        MangaReaderSettings.readingModeFromViewerFlags(0L) shouldBe null
        MangaReaderSettings.readingModeFromViewerFlags(1L) shouldBe ReadingMode.SINGLE_LTR
        MangaReaderSettings.readingModeFromViewerFlags(2L) shouldBe ReadingMode.SINGLE_RTL
        MangaReaderSettings.readingModeFromViewerFlags(3L) shouldBe ReadingMode.VERTICAL
        MangaReaderSettings.readingModeFromViewerFlags(4L) shouldBe ReadingMode.WEBTOON
        MangaReaderSettings.readingModeFromViewerFlags(5L) shouldBe ReadingMode.WEBTOON
        MangaReaderSettings.readingModeFromViewerFlags(6L) shouldBe ReadingMode.DUAL_LTR
        MangaReaderSettings.readingModeFromViewerFlags(7L) shouldBe ReadingMode.DUAL_RTL
    }

    @Test
    fun `encodeViewerFlags preserves orientation flags and sets reading mode bits`() {
        val baseFlags = 0x00000018L // bits 3 and 4 set (e.g. orientation)
        MangaReaderSettings.encodeViewerFlags(baseFlags, ReadingMode.SINGLE_LTR) shouldBe 0x00000019L
        MangaReaderSettings.encodeViewerFlags(baseFlags, ReadingMode.SINGLE_RTL) shouldBe 0x0000001AL
        MangaReaderSettings.encodeViewerFlags(baseFlags, ReadingMode.WEBTOON) shouldBe 0x0000001CL
        MangaReaderSettings.encodeViewerFlags(baseFlags, null) shouldBe 0x00000018L
    }

    @Test
    fun `parse and encode round trip through memoJson and viewerFlags`() {
        val override = MangaReaderSettingsOverride(
            readingMode = ReadingMode.SINGLE_RTL,
            preloadPages = 6,
        )
        val initialMemo = """{"customKey":"customValue"}"""
        val encodedMemo = MangaReaderSettings.encode(initialMemo, override)
        val encodedFlags = MangaReaderSettings.encodeViewerFlags(0L, override.readingMode)

        encodedFlags shouldBe 2L
        val parsed = MangaReaderSettings.parse(encodedFlags, encodedMemo)
        parsed shouldBe override

        // Other memo keys preserved
        encodedMemo.contains("customKey") shouldBe true
    }

    @Test
    fun `clearing override removes readerSettings from memoJson and clears viewerFlags`() {
        val override = MangaReaderSettingsOverride(
            readingMode = ReadingMode.VERTICAL,
            preloadPages = 4,
        )
        val initialMemo = """{"customKey":"customValue"}"""
        val encodedMemo = MangaReaderSettings.encode(initialMemo, override)
        val encodedFlags = MangaReaderSettings.encodeViewerFlags(0L, override.readingMode)

        val clearedMemo = MangaReaderSettings.encode(encodedMemo, MangaReaderSettingsOverride())
        val clearedFlags = MangaReaderSettings.encodeViewerFlags(encodedFlags, null)

        clearedFlags shouldBe 0L
        clearedMemo.contains("readerSettings") shouldBe false
        clearedMemo.contains("customKey") shouldBe true

        val parsed = MangaReaderSettings.parse(clearedFlags, clearedMemo)
        parsed.isEmpty shouldBe true
        parsed.readingMode shouldBe null
        parsed.preloadPages shouldBe null
    }

    @Test
    fun `parse falls back to viewerFlags when memoJson does not have readerSettings`() {
        // e.g. imported from Android backup where viewerFlags = 3 (VERTICAL)
        val parsed = MangaReaderSettings.parse(3L, "{}")
        parsed.readingMode shouldBe ReadingMode.VERTICAL
        parsed.preloadPages shouldBe null
        parsed.isEmpty shouldBe false
    }
}
