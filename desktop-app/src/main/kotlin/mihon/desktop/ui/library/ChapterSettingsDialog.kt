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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

// Android-compatible chapter flag bits (kept local to the desktop module).
internal const val CHAPTER_SORT_DESC = 0x00000000L
internal const val CHAPTER_SORT_ASC = 0x00000001L
internal const val CHAPTER_SORT_DIR_MASK = 0x00000001L

internal const val CHAPTER_SHOW_UNREAD = 0x00000002L
internal const val CHAPTER_SHOW_READ = 0x00000004L
internal const val CHAPTER_UNREAD_MASK = 0x00000006L

internal const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
internal const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
internal const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

internal const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
internal const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
internal const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

internal const val CHAPTER_SORTING_SOURCE = 0x00000000L
internal const val CHAPTER_SORTING_NUMBER = 0x00000100L
internal const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
internal const val CHAPTER_SORTING_MASK = 0x00000300L

internal const val CHAPTER_DISPLAY_NAME = 0x00000000L
internal const val CHAPTER_DISPLAY_NUMBER = 0x00100000L

// Desktop extension bit. Android only reserves 0x00100000 for the display mode, so this
// remains backup-compatible while still allowing "source order" labels on desktop.
internal const val CHAPTER_DISPLAY_SOURCE_ORDER = 0x00200000L
internal const val CHAPTER_DISPLAY_MASK = 0x00300000L

internal const val CHAPTER_DEFAULT_FLAGS_KEY = "chapter.default_flags"
internal const val CHAPTER_DEFAULT_SHOW_MISSING_KEY = "chapter.default_show_missing_chapters"

enum class ChapterDisplayMode {
    Name,
    Number,
    SourceOrder,
}

@Composable
fun ChapterSettingsDialog(
    settings: ChapterSettings,
    availableScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onDisplayModeChange: (ChapterDisplayMode) -> Unit,
    onSortModeChange: (ChapterSortMode, Boolean) -> Unit,
    onShowMissingChaptersChange: (Boolean) -> Unit,
    onExcludedScanlatorsChange: (Set<String>) -> Unit,
    onSetAsDefault: (applyToExistingManga: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var showScanlatorDialog by remember { mutableStateOf(false) }
    var showSetAsDefaultDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("chapter-settings-dialog"),
        title = { Text(strings.text(UiText.ChapterSettings)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSection(strings.text(UiText.Scanlators))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-scanlators")
                        .clickable { showScanlatorDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.text(UiText.ScanlatorFilter),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (settings.excludedScanlators.isEmpty()) {
                            strings.text(UiText.All)
                        } else {
                            strings.text(UiText.ExcludedCount, settings.excludedScanlators.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                SettingsSection(strings.text(UiText.ChapterDisplay))
                ChapterDisplayMode.entries.forEach { mode ->
                    RadioOption(
                        label = when (mode) {
                            ChapterDisplayMode.Name -> strings.text(UiText.ChapterName)
                            ChapterDisplayMode.Number -> strings.text(UiText.ChapterNumber)
                            ChapterDisplayMode.SourceOrder -> strings.text(UiText.SourceOrder)
                        },
                        selected = settings.displayMode == mode,
                        onClick = { onDisplayModeChange(mode) },
                        testTag = "chapter-settings-display-${mode.name}",
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-missing")
                        .clickable { onShowMissingChaptersChange(!settings.showMissingChapters) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = settings.showMissingChapters,
                        onCheckedChange = onShowMissingChaptersChange,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.text(UiText.ShowMissingChapters), style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                SettingsSection(strings.text(UiText.Sort))
                listOf(
                    ChapterSortMode.SourceOrder to strings.sortSourceOrder,
                    ChapterSortMode.ChapterNumber to strings.sortChapterNumber,
                    ChapterSortMode.UploadDate to strings.sortUploadDate,
                ).forEach { (mode, label) ->
                    RadioOption(
                        label = label,
                        selected = settings.sortMode == mode,
                        onClick = { onSortModeChange(mode, settings.sortAscending) },
                        testTag = "chapter-settings-sort-${mode.name}",
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-sort-ascending")
                        .clickable { onSortModeChange(settings.sortMode, !settings.sortAscending) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = settings.sortAscending,
                        onCheckedChange = { onSortModeChange(settings.sortMode, it) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.librarySortAscending, style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showSetAsDefaultDialog = true },
                        modifier = Modifier.testTag("chapter-settings-set-default"),
                    ) {
                        Text(strings.text(UiText.SetDefault))
                    }
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.testTag("chapter-settings-reset"),
                    ) {
                        Text(strings.filterReset)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("chapter-settings-done"),
            ) {
                Text(strings.libraryBatchDone)
            }
        },
    )

    if (showScanlatorDialog) {
        ScanlatorFilterDialog(
            availableScanlators = availableScanlators,
            excludedScanlators = settings.excludedScanlators,
            onDismissRequest = { showScanlatorDialog = false },
            onConfirm = { excluded ->
                onExcludedScanlatorsChange(excluded)
                showScanlatorDialog = false
            },
        )
    }
    if (showSetAsDefaultDialog) {
        SetChapterSettingsAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = { applyToExisting ->
                onSetAsDefault(applyToExisting)
                showSetAsDefaultDialog = false
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
