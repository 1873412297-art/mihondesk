package mihon.desktop.library.backup

import io.kotest.matchers.shouldBe
import mihon.sync.core.model.AndroidBackupManga
import org.junit.jupiter.api.Test

class AndroidBackupMappersTest {
    @Test
    fun `a genre list becomes a deduplicated code-point sorted json set`() {
        // Restore and sync both write through this, so the canonical form must not depend on the
        // incoming order or on duplicates.
        canonicalStringSetJson(listOf("b", "a", "b", "é", "\uD83D\uDE00")) shouldBe
            "[\"a\",\"b\",\"é\",\"\uD83D\uDE00\"]"
    }

    @Test
    fun `an empty genre list stays an empty json array`() {
        canonicalStringSetJson(emptyList()) shouldBe "[]"
    }

    @Test
    fun `the memo bytes decode as utf-8 text`() {
        memoJson("{\"title\":\"漫\"}".toByteArray()) shouldBe "{\"title\":\"漫\"}"
    }

    @Test
    fun `manga mapping canonicalizes genre and excluded scanlators and keeps the caller's memo`() {
        val record = AndroidBackupManga(
            source = 7,
            url = "/manga",
            title = "Title",
            genre = listOf("Zeta", "Alpha", "Alpha"),
            excludedScanlators = listOf("Y", "X"),
            status = 2,
            favorite = true,
            lastModifiedAt = 42,
        ).toMangaRecord("{\"k\":1}")

        record.genreJson shouldBe "[\"Alpha\",\"Zeta\"]"
        record.excludedScanlatorsJson shouldBe "[\"X\",\"Y\"]"
        record.memoJson shouldBe "{\"k\":1}"
        record.sourceId shouldBe 7
        record.status shouldBe 2L
        record.favorite shouldBe true
        record.lastModifiedAt shouldBe 42
    }
}
