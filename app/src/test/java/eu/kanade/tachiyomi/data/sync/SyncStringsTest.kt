package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class SyncStringsTest {

    @Test
    fun `locale picks english simplified or traditional chinese`() {
        assertEquals(SyncStrings.Variant.EN, SyncStrings.variantFor(Locale.US))
        assertEquals(SyncStrings.Variant.EN, SyncStrings.variantFor(Locale.JAPAN))
        assertEquals(SyncStrings.Variant.HANS, SyncStrings.variantFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals(SyncStrings.Variant.HANS, SyncStrings.variantFor(Locale.forLanguageTag("zh")))
        assertEquals(SyncStrings.Variant.HANT, SyncStrings.variantFor(Locale.forLanguageTag("zh-Hant")))
        assertEquals(SyncStrings.Variant.HANT, SyncStrings.variantFor(Locale.TRADITIONAL_CHINESE))
        assertEquals(SyncStrings.Variant.HANT, SyncStrings.variantFor(Locale.forLanguageTag("zh-HK")))
        assertEquals(SyncStrings.Variant.HANT, SyncStrings.variantFor(Locale.forLanguageTag("zh-MO")))
        assertEquals(SyncStrings.Variant.HANS, SyncStrings.variantFor(Locale.forLanguageTag("zh-Hans-TW")))
    }

    @Test
    fun `every drive error has a distinct friendly message per locale`() {
        val locales = listOf(Locale.US, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)
        DriveSyncError.entries.forEach { error ->
            val texts = locales.map { SyncStrings.driveError(error, it) }
            texts.forEach { assertTrue(it.isNotBlank()) }
            assertEquals(3, texts.toSet().size, "locales should differ for $error: $texts")
        }
        assertNotEquals(
            SyncStrings.driveError(DriveSyncError.NETWORK, Locale.US),
            SyncStrings.driveError(DriveSyncError.AUTH_REQUIRED, Locale.US),
        )
    }

    @Test
    fun `summary is localized`() {
        assertEquals("Pulled 1, pushed 2, changed 3", SyncStrings.summary(1, 2, 3, Locale.US))
        assertEquals("拉取 1，推送 2，变更 3", SyncStrings.summary(1, 2, 3, Locale.SIMPLIFIED_CHINESE))
        assertEquals("拉取 1，推送 2，變更 3", SyncStrings.summary(1, 2, 3, Locale.TRADITIONAL_CHINESE))
    }
}
