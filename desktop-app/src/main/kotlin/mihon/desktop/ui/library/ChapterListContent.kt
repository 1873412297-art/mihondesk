package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings

/** The search-empty placeholder, the chapter rows and the bottom spacer of the detail list. */
internal fun LazyListScope.chapterListItems(
    displayedChapterItems: List<ChapterListItem>,
    chapterSearchQuery: String,
    state: MangaDetailUiState,
    strings: DesktopStrings,
    isSelectionMode: Boolean,
    selectedChapterIds: Set<Long>,
    selectionAnchorId: Long?,
    onSelectedChapterIdsChange: (Set<Long>) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionAnchorChange: (Long?) -> Unit,
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
    onMarkPreviousRead: (Long) -> Unit,
    onDownloadChapter: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onOpenChapterInWebView: ((Long) -> Unit)?,
) {
    if (displayedChapterItems.isEmpty() && chapterSearchQuery.isNotBlank()) {
        item(key = "detail-search-empty") { ChapterSearchEmpty(strings) }
    } else {
        items(displayedChapterItems, key = { it.key }) { item ->
            ChapterListItemView(
                item = item,
                state = state,
                strings = strings,
                allChapterItems = displayedChapterItems,
                isSelectionMode = isSelectionMode,
                selectedChapterIds = selectedChapterIds,
                selectionAnchorId = selectionAnchorId,
                onSelectedChapterIdsChange = onSelectedChapterIdsChange,
                onSelectionModeChange = onSelectionModeChange,
                onSelectionAnchorChange = onSelectionAnchorChange,
                onReadChapter = onReadChapter,
                onToggleBookmark = onToggleBookmark,
                onToggleRead = onToggleRead,
                onMarkPreviousRead = onMarkPreviousRead,
                onDownloadChapter = onDownloadChapter,
                onDeleteDownload = onDeleteDownload,
                onOpenChapterInWebView = onOpenChapterInWebView,
            )
        }
    }
    item(key = "detail-bottom-space") {
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun ChapterSearchEmpty(strings: DesktopStrings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("chapter-search-empty"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = strings.readerChapterDrawerNoResults,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ChapterListItemView(
    item: ChapterListItem,
    state: MangaDetailUiState,
    strings: DesktopStrings,
    allChapterItems: List<ChapterListItem>,
    isSelectionMode: Boolean,
    selectedChapterIds: Set<Long>,
    selectionAnchorId: Long?,
    onSelectedChapterIdsChange: (Set<Long>) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionAnchorChange: (Long?) -> Unit,
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
    onMarkPreviousRead: (Long) -> Unit,
    onDownloadChapter: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onOpenChapterInWebView: ((Long) -> Unit)?,
) {
    when (item) {
        is ChapterListItem.MissingCount -> MissingChapterIndicator(count = item.count)
        is ChapterListItem.VolumeHeader -> VolumeHeaderRow(item)
        is ChapterListItem.Chapter -> ChapterListItemRow(
            item = item,
            state = state,
            strings = strings,
            allChapterItems = allChapterItems,
            isSelectionMode = isSelectionMode,
            selectedChapterIds = selectedChapterIds,
            selectionAnchorId = selectionAnchorId,
            onSelectedChapterIdsChange = onSelectedChapterIdsChange,
            onSelectionModeChange = onSelectionModeChange,
            onSelectionAnchorChange = onSelectionAnchorChange,
            onReadChapter = onReadChapter,
            onToggleBookmark = onToggleBookmark,
            onToggleRead = onToggleRead,
            onMarkPreviousRead = onMarkPreviousRead,
            onDownloadChapter = onDownloadChapter,
            onDeleteDownload = onDeleteDownload,
            onOpenChapterInWebView = onOpenChapterInWebView,
        )
    }
}

@Composable
private fun ChapterListItemRow(
    item: ChapterListItem.Chapter,
    state: MangaDetailUiState,
    strings: DesktopStrings,
    allChapterItems: List<ChapterListItem>,
    isSelectionMode: Boolean,
    selectedChapterIds: Set<Long>,
    selectionAnchorId: Long?,
    onSelectedChapterIdsChange: (Set<Long>) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectionAnchorChange: (Long?) -> Unit,
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
    onMarkPreviousRead: (Long) -> Unit,
    onDownloadChapter: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onOpenChapterInWebView: ((Long) -> Unit)?,
) {
    val chapter = item.chapter
    ChapterRow(
        chapter = chapter,
        displayName = chapterDisplayLabel(chapter, state.chapterSettings.displayMode, strings),
        availability = state.readerAvailability[chapter.id] ?: ChapterReaderAvailability.RemoteOnly,
        isDownloaded = state.downloadedChapterIds.contains(chapter.id),
        downloadOf = state.downloadProgressOf,
        downloadsRunning = state.downloadsRunning,
        isSelectionMode = isSelectionMode,
        isSelected = selectedChapterIds.contains(chapter.id),
        onToggleSelection = {
            onSelectedChapterIdsChange(
                if (selectedChapterIds.contains(chapter.id)) {
                    selectedChapterIds - chapter.id
                } else {
                    selectedChapterIds + chapter.id
                },
            )
            onSelectionAnchorChange(chapter.id)
        },
        onRangeSelect = {
            onSelectionModeChange(true)
            onSelectedChapterIdsChange(
                chapterRangeSelection(
                    allChapterItems.filterIsInstance<ChapterListItem.Chapter>().map { it.chapter.id },
                    selectionAnchorId,
                    chapter.id,
                    selectedChapterIds,
                ),
            )
            onSelectionAnchorChange(chapter.id)
        },
        onReadChapter = {
            if (isSelectionMode) {
                onSelectedChapterIdsChange(
                    if (selectedChapterIds.contains(it)) selectedChapterIds - it else selectedChapterIds + it,
                )
                onSelectionAnchorChange(it)
            } else {
                onReadChapter(it)
            }
        },
        onToggleBookmark = { onToggleBookmark(chapter.id) },
        onToggleRead = { onToggleRead(chapter.id) },
        onMarkPreviousRead = { onMarkPreviousRead(chapter.id) },
        onDownloadChapter = { onDownloadChapter(chapter.id) },
        onDeleteDownload = { onDeleteDownload(chapter.id) },
        onOpenInWebView = onOpenChapterInWebView?.let { handler -> { handler(chapter.id) } },
    )
}

/** Filters chapter rows by title, scanlator and number; headers and missing-count rows never match. */
internal fun filterChapterItems(items: List<ChapterListItem>, query: String): List<ChapterListItem> {
    if (query.isBlank()) return items
    return items.filter { item ->
        when (item) {
            is ChapterListItem.Chapter ->
                item.label.contains(query, ignoreCase = true) ||
                    item.chapter.name.contains(query, ignoreCase = true) ||
                    item.chapter.scanlator?.contains(query, ignoreCase = true) == true ||
                    item.chapter.chapterNumber.toString().contains(query)
            is ChapterListItem.MissingCount -> false
            is ChapterListItem.VolumeHeader -> false
        }
    }
}
