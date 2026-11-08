package mihon.desktop.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

/** Floating "read next" button shown when no chapter is selected. */
@Composable
internal fun NextChapterFab(text: String, onRead: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onRead,
        icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
        text = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.testTag("manga-detail-fab-read"),
    )
}

/** Batch action bar shown while chapters are selected. */
@Composable
internal fun ChapterActionMenu(
    visible: Boolean,
    selectedChapterIds: Set<Long>,
    displayedChapterItems: List<ChapterListItem>,
    downloadedChapterIds: Set<Long>,
    onSelectedChapterIdsChange: (Set<Long>) -> Unit,
    onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit,
    onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit,
    onMarkPreviousRead: (Long) -> Unit,
    onBatchDownloadChapters: (Set<Long>) -> Unit,
    onBatchDeleteDownloads: (Set<Long>) -> Unit,
    onCloseSelection: () -> Unit,
    modifier: Modifier,
) {
    MangaBottomActionMenu(
        visible = visible,
        selectedCount = selectedChapterIds.size,
        totalCount = displayedChapterItems.count { it is ChapterListItem.Chapter },
        onSelectAll = {
            onSelectedChapterIdsChange(
                displayedChapterItems.mapNotNull { (it as? ChapterListItem.Chapter)?.chapter?.id }.toSet(),
            )
        },
        onInvertSelection = {
            val allIds = displayedChapterItems.mapNotNull { (it as? ChapterListItem.Chapter)?.chapter?.id }.toSet()
            onSelectedChapterIdsChange(allIds - selectedChapterIds)
        },
        onBookmarkClicked = { onBatchBookmarkChapters(selectedChapterIds, true) },
        onRemoveBookmarkClicked = { onBatchBookmarkChapters(selectedChapterIds, false) },
        onMarkAsReadClicked = { onBatchMarkChaptersRead(selectedChapterIds, true) },
        onMarkAsUnreadClicked = { onBatchMarkChaptersRead(selectedChapterIds, false) },
        onMarkPreviousAsReadClicked = if (selectedChapterIds.size == 1) {
            { onMarkPreviousRead(selectedChapterIds.first()) }
        } else {
            null
        },
        onDownloadClicked = { onBatchDownloadChapters(selectedChapterIds) },
        onDeleteClicked = if (selectedChapterIds.any { downloadedChapterIds.contains(it) }) {
            { onBatchDeleteDownloads(selectedChapterIds) }
        } else {
            null
        },
        onCloseClicked = onCloseSelection,
        modifier = modifier,
    )
}
