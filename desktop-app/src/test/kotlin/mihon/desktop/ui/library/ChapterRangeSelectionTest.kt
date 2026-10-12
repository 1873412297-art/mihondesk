package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterRangeSelectionTest {
    private val ids = listOf(10L, 11L, 12L, 13L, 14L)

    @Test
    fun `selects inclusive range in either direction and keeps prior selection`() {
        chapterRangeSelection(ids, 11L, 13L, emptySet()) shouldBe setOf(11L, 12L, 13L)
        chapterRangeSelection(ids, 14L, 12L, setOf(10L)) shouldBe setOf(10L, 12L, 13L, 14L)
    }

    @Test
    fun `missing anchor selects only the target and unknown targets change nothing`() {
        chapterRangeSelection(ids, null, 12L, emptySet()) shouldBe setOf(12L)
        chapterRangeSelection(ids, 99L, 12L, setOf(10L)) shouldBe setOf(10L, 12L)
        chapterRangeSelection(ids, 11L, 99L, setOf(10L)) shouldBe setOf(10L)
    }
}
