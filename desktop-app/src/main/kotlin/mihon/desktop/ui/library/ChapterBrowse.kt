package mihon.desktop.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Chapter search and multi-selection on a manga detail page. It belongs to one manga: state that
 * was built for another manga is discarded (see [forManga]) so a selection never leaks across pages.
 */
data class ChapterBrowseState(
    val mangaId: Long? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedChapterIds: Set<Long> = emptySet(),
    /** Last chapter toggled by a plain click; Shift+click selects everything between it and the target. */
    val selectionAnchorId: Long? = null,
) {
    val isSelecting: Boolean get() = isSelectionMode || selectedChapterIds.isNotEmpty()

    fun forManga(id: Long?): ChapterBrowseState = if (mangaId == id) this else ChapterBrowseState(mangaId = id)
}

sealed interface ChapterBrowseAction {
    data object ToggleSearch : ChapterBrowseAction
    data class QueryChanged(val query: String) : ChapterBrowseAction
    data object ToggleSelectionMode : ChapterBrowseAction
    data class SetSelectionMode(val enabled: Boolean) : ChapterBrowseAction
    data class SetSelectedChapters(val chapterIds: Set<Long>) : ChapterBrowseAction
    data class SetSelectionAnchor(val chapterId: Long?) : ChapterBrowseAction
    data object CloseSelection : ChapterBrowseAction
}

fun ChapterBrowseState.reduce(action: ChapterBrowseAction): ChapterBrowseState = when (action) {
    ChapterBrowseAction.ToggleSearch -> if (isSearching) {
        copy(isSearching = false, searchQuery = "")
    } else {
        copy(isSearching = true)
    }
    is ChapterBrowseAction.QueryChanged -> copy(searchQuery = action.query)
    ChapterBrowseAction.ToggleSelectionMode -> if (isSelectionMode) {
        copy(isSelectionMode = false, selectedChapterIds = emptySet())
    } else {
        copy(isSelectionMode = true)
    }
    is ChapterBrowseAction.SetSelectionMode -> copy(isSelectionMode = action.enabled)
    is ChapterBrowseAction.SetSelectedChapters -> copy(selectedChapterIds = action.chapterIds)
    is ChapterBrowseAction.SetSelectionAnchor -> copy(selectionAnchorId = action.chapterId)
    ChapterBrowseAction.CloseSelection -> copy(isSelectionMode = false, selectedChapterIds = emptySet())
}

/** Holder used by detail presenters; actions always apply to the manga that is currently shown. */
class ChapterBrowseModel {
    private val mutableState = MutableStateFlow(ChapterBrowseState())
    val state: StateFlow<ChapterBrowseState> = mutableState

    fun onAction(mangaId: Long?, action: ChapterBrowseAction) {
        mutableState.update { it.forManga(mangaId).reduce(action) }
    }
}
