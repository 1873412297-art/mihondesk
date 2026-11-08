package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.category.DesktopCategory
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryManga

@Composable
internal fun LibraryDisplayOptionsDialog(
    showUnreadBadge: Boolean,
    showDownloadedBadge: Boolean,
    gridSize: Float,
    showGridSize: Boolean,
    onUnreadBadgeChange: (Boolean) -> Unit,
    onDownloadedBadgeChange: (Boolean) -> Unit,
    onGridSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("library-display-options-dialog"),
        title = { Text(strings.text(UiText.LibraryDisplayOptions)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showUnreadBadge,
                        onCheckedChange = onUnreadBadgeChange,
                        modifier = Modifier.testTag("library-badge-unread"),
                    )
                    Text(strings.text(UiText.LibraryBadgeUnread))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showDownloadedBadge,
                        onCheckedChange = onDownloadedBadgeChange,
                        modifier = Modifier.testTag("library-badge-downloaded"),
                    )
                    Text(strings.text(UiText.LibraryBadgeDownloaded))
                }
                if (showGridSize) {
                    HorizontalDivider()
                    Text(strings.libraryGridSize, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${gridSize.toInt()}dp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = gridSize,
                        onValueChange = onGridSizeChange,
                        valueRange = 120f..280f,
                        modifier = Modifier.fillMaxWidth().testTag("library-grid-slider"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("library-display-options-close"),
            ) {
                Text(strings.dialogDone)
            }
        },
    )
}

@Composable
internal fun LibraryMangaContextMenu(
    manga: LibraryManga,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenDetails: () -> Unit,
    onResumeReading: (() -> Unit)? = null,
    onToggleRead: () -> Unit,
    onDownload: () -> Unit,
    onEditCategories: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
) {
    if (!expanded) return
    val strings = LocalStrings.current
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = { Text(strings.text(UiText.ContextOpenDetails)) },
            onClick = {
                onDismissRequest()
                onOpenDetails()
            },
        )
        if (onResumeReading != null && manga.chapterCount > 0) {
            DropdownMenuItem(
                text = { Text(strings.text(UiText.ContextResumeReading)) },
                onClick = {
                    onDismissRequest()
                    onResumeReading()
                },
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    if (manga.unreadCount > 0) {
                        strings.text(UiText.ContextMarkAsRead)
                    } else {
                        strings.text(UiText.ContextMarkAsUnread)
                    },
                )
            },
            onClick = {
                onDismissRequest()
                onToggleRead()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.text(UiText.ContextDownload)) },
            onClick = {
                onDismissRequest()
                onDownload()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.text(UiText.ContextEditCategories)) },
            onClick = {
                onDismissRequest()
                onEditCategories()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.text(UiText.ContextRemoveFromLibrary)) },
            onClick = {
                onDismissRequest()
                onRemoveFromLibrary()
            },
        )
    }
}

@Composable
internal fun BatchCategorySelectionDialog(
    categories: List<DesktopCategory>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.libraryBatchChangeCategory) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (categories.isEmpty()) {
                    Text(strings.categoryNoneCreated)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategoryIds = if (selectedCategoryIds.contains(cat.id)) {
                                            selectedCategoryIds - cat.id
                                        } else {
                                            selectedCategoryIds + cat.id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedCategoryIds.contains(cat.id),
                                    onCheckedChange = {
                                        selectedCategoryIds = if (it) {
                                            selectedCategoryIds + cat.id
                                        } else {
                                            selectedCategoryIds - cat.id
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategoryIds.toList()) },
                modifier = Modifier.testTag("batch-category-confirm"),
            ) {
                Text(strings.dialogDone)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogCancel)
            }
        },
    )
}
