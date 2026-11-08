package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox

/** Chapter count plus the download/sort/settings/search/selection controls. */
@Composable
internal fun MangaChapterToolbar(
    state: MangaDetailUiState,
    strings: DesktopStrings,
    isSearchingChapters: Boolean,
    chapterSearchQuery: String,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onDownloadBatch: (Int?) -> Unit,
    onChapterSortChange: (ChapterSortState) -> Unit,
    onOpenChapterSettings: () -> Unit,
    onOpenReadingSettings: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val countText = if (state.chapters.size == state.allChapters.size) {
            "${strings.chapters} (${state.chapters.size})"
        } else {
            "${strings.chapters} (${state.chapters.size}/${state.allChapters.size})"
        }
        Text(countText, style = MaterialTheme.typography.titleLarge)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterDownloadMenu(strings, onDownloadBatch)
            ChapterSortMenu(state.chapterSortState, strings, onChapterSortChange)
            ChapterSettingsButton(strings, onOpenChapterSettings)
            ChapterReadingSettingsButton(strings, onOpenReadingSettings)
            ChapterSearchButton(strings, isSearchingChapters, chapterSearchQuery, onToggleSearch)
            ChapterSelectionButton(strings, isSelectionMode, selectedCount, onToggleSelection)
        }
    }
}

@Composable
private fun ChapterDownloadMenu(strings: DesktopStrings, onDownloadBatch: (Int?) -> Unit) {
    var downloadMenuExpanded by remember { mutableStateOf(false) }
    Box {
        DesktopTooltipBox(text = strings.downloadChapter) {
            IconButton(
                onClick = { downloadMenuExpanded = true },
                modifier = Modifier.testTag("manga-detail-download-menu-button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = strings.downloadChapter,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = downloadMenuExpanded,
            onDismissRequest = { downloadMenuExpanded = false },
        ) {
            DownloadMenuItem("next-1", strings.downloadNext1, 1, onDownloadBatch) { downloadMenuExpanded = false }
            DownloadMenuItem("next-5", strings.downloadNext5, 5, onDownloadBatch) { downloadMenuExpanded = false }
            DownloadMenuItem("next-10", strings.downloadNext10, 10, onDownloadBatch) { downloadMenuExpanded = false }
            DownloadMenuItem("next-25", strings.downloadNext25, 25, onDownloadBatch) { downloadMenuExpanded = false }
            DownloadMenuItem("unread", strings.downloadUnread, null, onDownloadBatch) { downloadMenuExpanded = false }
            DownloadMenuItem("all", strings.downloadAll, -1, onDownloadBatch) { downloadMenuExpanded = false }
        }
    }
}

@Composable
private fun DownloadMenuItem(
    tag: String,
    label: String,
    amount: Int?,
    onDownloadBatch: (Int?) -> Unit,
    onPicked: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onDownloadBatch(amount)
            onPicked()
        },
        modifier = Modifier.testTag("manga-detail-download-$tag"),
    )
}

@Composable
private fun ChapterSortMenu(
    sortState: ChapterSortState,
    strings: DesktopStrings,
    onChapterSortChange: (ChapterSortState) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { sortMenuExpanded = true },
            modifier = Modifier.testTag("manga-detail-sort-button"),
        ) {
            val dirArrow = if (sortState.ascending) "▲" else "▼"
            Text("${sortState.mode.label(strings)} $dirArrow")
        }
        DropdownMenu(
            expanded = sortMenuExpanded,
            onDismissRequest = { sortMenuExpanded = false },
        ) {
            ChapterSortMode.entries.forEach { mode ->
                val isCurrentMode = sortState.mode == mode
                DropdownMenuItem(
                    text = { Text((if (isCurrentMode) "✓ " else "") + mode.label(strings)) },
                    onClick = {
                        if (isCurrentMode) {
                            onChapterSortChange(sortState.copy(ascending = !sortState.ascending))
                        } else {
                            onChapterSortChange(sortState.copy(mode = mode))
                        }
                        sortMenuExpanded = false
                    },
                )
            }
        }
    }
}

private fun ChapterSortMode.label(strings: DesktopStrings): String = when (this) {
    ChapterSortMode.SourceOrder -> strings.sortSourceOrder
    ChapterSortMode.ChapterNumber -> strings.sortChapterNumber
    ChapterSortMode.UploadDate -> strings.sortUploadDate
}

@Composable
private fun ChapterSettingsButton(strings: DesktopStrings, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("manga-detail-chapter-settings-button")
            .clickable(onClick = onClick),
    ) {
        DesktopTooltipBox(text = strings.text(UiText.ChapterSettings)) {
            IconButton(onClick = onClick, modifier = Modifier.testTag("chapter-settings-button")) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.text(UiText.ChapterSettings),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChapterReadingSettingsButton(strings: DesktopStrings, onClick: () -> Unit) {
    val description = strings.text(UiText.ReadingSettingsOverride)
    Box(
        modifier = Modifier
            .testTag("manga-detail-reading-settings-button")
            .clickable(onClick = onClick),
    ) {
        DesktopTooltipBox(text = description) {
            IconButton(onClick = onClick, modifier = Modifier.testTag("reading-settings-override-button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ChromeReaderMode,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChapterSearchButton(
    strings: DesktopStrings,
    isSearching: Boolean,
    query: String,
    onToggle: () -> Unit,
) {
    DesktopTooltipBox(text = strings.text(UiText.SearchChapters)) {
        IconButton(onClick = onToggle, modifier = Modifier.testTag("chapter-search-toggle-button")) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = strings.text(UiText.SearchChapters),
                tint = if (isSearching || query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ChapterSelectionButton(
    strings: DesktopStrings,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onToggle: () -> Unit,
) {
    DesktopTooltipBox(text = strings.chapterBatchSelect) {
        IconButton(onClick = onToggle, modifier = Modifier.testTag("chapter-selection-toggle-button")) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                contentDescription = strings.chapterBatchSelect,
                tint = if (isSelectionMode || selectedCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
