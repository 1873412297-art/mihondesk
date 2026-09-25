package mihon.sync.core.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SyncKeysTest {

    @Test
    fun `MangaKey serializes and parses correctly`() {
        val key = MangaKey(123456789L, "https://example.com/manga/one-piece?param=1:2")
        val keyStr = key.toKeyString()
        keyStr shouldBe "123456789:https://example.com/manga/one-piece?param=1:2"
        MangaKey.parse(keyStr) shouldBe key
    }

    @Test
    fun `ChapterKey serializes and parses correctly`() {
        val mangaKey = MangaKey(123L, "https://example.com/manga/1")
        val chapterKey = ChapterKey(mangaKey, "/chapter-42")
        val keyStr = chapterKey.toKeyString()
        keyStr shouldBe "123:https://example.com/manga/1::/chapter-42"
        ChapterKey.parse(keyStr) shouldBe chapterKey
    }

    @Test
    fun `CategoryKey normalizes name ignoring case and whitespace`() {
        val key1 = CategoryKey("  Action Manga  ")
        val key2 = CategoryKey("action manga")
        key1.normalizedName shouldBe "action manga"
        key1.toKeyString() shouldBe "action manga"
        CategoryKey.parse("  ACTION MANGA  ") shouldBe key2
    }

    @Test
    fun `HistoryKey serializes and parses correctly`() {
        val chapterKey = ChapterKey(MangaKey(1L, "manga-url"), "chapter-url")
        val histKey = HistoryKey(chapterKey)
        val keyStr = histKey.toKeyString()
        HistoryKey.parse(keyStr) shouldBe histKey
    }

    @Test
    fun `TrackingKey serializes and parses correctly`() {
        val mangaKey = MangaKey(999L, "manga-url")
        val trackKey = TrackingKey(mangaKey, 2L)
        val keyStr = trackKey.toKeyString()
        keyStr shouldBe "999:manga-url#2"
        TrackingKey.parse(keyStr) shouldBe trackKey
    }
}
