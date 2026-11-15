package mihon.desktop.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Stands in for the presenter that owns chapter search/selection when a test renders
 * [MangaDetailScreen] on its own.
 */
@Composable
internal fun WithChapterBrowse(
    state: MangaDetailUiState,
    content: @Composable (MangaDetailUiState, (ChapterBrowseAction) -> Unit) -> Unit,
) {
    val model = remember { ChapterBrowseModel() }
    val browse by model.state.collectAsState()
    content(state.copy(chapterBrowse = browse)) { model.onAction(state.manga?.id, it) }
}
