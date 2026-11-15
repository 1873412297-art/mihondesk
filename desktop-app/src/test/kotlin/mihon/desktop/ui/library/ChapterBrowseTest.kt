package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterBrowseTest {

    @Test
    fun `closing search clears the query`() {
        val searching = ChapterBrowseState(mangaId = 1)
            .reduce(ChapterBrowseAction.ToggleSearch)
            .reduce(ChapterBrowseAction.QueryChanged("ch 3"))
        searching.isSearching shouldBe true
        searching.searchQuery shouldBe "ch 3"

        val closed = searching.reduce(ChapterBrowseAction.ToggleSearch)
        closed.isSearching shouldBe false
        closed.searchQuery shouldBe ""
    }

    @Test
    fun `leaving selection mode drops the selection`() {
        val selected = ChapterBrowseState(mangaId = 1)
            .reduce(ChapterBrowseAction.ToggleSelectionMode)
            .reduce(ChapterBrowseAction.SetSelectedChapters(setOf(10L, 11L)))
            .reduce(ChapterBrowseAction.SetSelectionAnchor(11L))
        selected.isSelecting shouldBe true

        val left = selected.reduce(ChapterBrowseAction.ToggleSelectionMode)
        left.isSelectionMode shouldBe false
        left.selectedChapterIds shouldBe emptySet()
        left.isSelecting shouldBe false
        selected.reduce(ChapterBrowseAction.CloseSelection).isSelecting shouldBe false
    }

    @Test
    fun `selection never carries over to another manga`() {
        val model = ChapterBrowseModel()
        model.onAction(1L, ChapterBrowseAction.SetSelectedChapters(setOf(10L)))
        model.onAction(1L, ChapterBrowseAction.QueryChanged("x"))
        model.state.value.selectedChapterIds shouldBe setOf(10L)

        model.state.value.forManga(2L) shouldBe ChapterBrowseState(mangaId = 2L)
        model.onAction(2L, ChapterBrowseAction.SetSelectionMode(true))
        model.state.value shouldBe ChapterBrowseState(mangaId = 2L, isSelectionMode = true)
    }
}
