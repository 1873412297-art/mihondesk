package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

/** Update, import and overflow actions shown in the library header. */
@Composable
internal fun LibraryHeaderActions(
    state: LibraryUiState,
    strings: DesktopStrings,
    failedCovers: Map<Long, String>,
    isUpdatingLibrary: Boolean,
    isRepairingCovers: Boolean,
    onUpdateLibrary: (() -> Unit)?,
    onRepairBrokenCovers: (() -> Unit)?,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenRandomManga: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
) {
    if (onUpdateLibrary != null) {
        LibraryUpdateButton(strings, isUpdatingLibrary, onUpdateLibrary)
    }
    // An empty library keeps the import actions visible; once it has titles they move into ⋮.
    val libraryIsEmpty = !state.loading && state.items.isEmpty() && !state.filterState.hasActiveFilters &&
        state.query.isBlank()
    if (libraryIsEmpty) {
        LibraryImportButtons(strings, onImportBackup, onImportLocal)
    }
    LibraryOverflowMenu(
        state = state,
        strings = strings,
        failedCovers = failedCovers,
        isRepairingCovers = isRepairingCovers,
        libraryIsEmpty = libraryIsEmpty,
        onOpenRandomManga = onOpenRandomManga,
        onToggleSelectionMode = onToggleSelectionMode,
        onRepairBrokenCovers = onRepairBrokenCovers,
        onImportBackup = onImportBackup,
        onImportLocal = onImportLocal,
    )
}

@Composable
private fun LibraryUpdateButton(strings: DesktopStrings, updating: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !updating, modifier = Modifier.testTag("library-update-button")) {
        if (updating) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.libraryUpdating)
        } else {
            Text(strings.libraryUpdateNow)
        }
    }
}

@Composable
private fun LibraryImportButtons(strings: DesktopStrings, onImportBackup: () -> Unit, onImportLocal: () -> Unit) {
    OutlinedButton(onClick = onImportBackup, modifier = Modifier.testTag("library-import-backup")) {
        Text(strings.libraryImportBackup)
    }
    OutlinedButton(onClick = onImportLocal, modifier = Modifier.testTag("library-import-local")) {
        Text(strings.libraryImportLocal)
    }
}

@Composable
private fun LibraryOverflowMenu(
    state: LibraryUiState,
    strings: DesktopStrings,
    failedCovers: Map<Long, String>,
    isRepairingCovers: Boolean,
    libraryIsEmpty: Boolean,
    onOpenRandomManga: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onRepairBrokenCovers: (() -> Unit)?,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    Box {
        DesktopTooltipBox(text = strings.text(UiText.MoreOptions)) {
            IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.testTag("library-overflow")) {
                BadgedBox(
                    badge = {
                        if (failedCovers.isNotEmpty() && onRepairBrokenCovers != null) {
                            Badge { Text(failedCovers.size.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = strings.text(UiText.MoreOptions))
                }
            }
        }
        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
            LibraryOverflowItems(
                state = state,
                strings = strings,
                failedCovers = failedCovers,
                isRepairingCovers = isRepairingCovers,
                libraryIsEmpty = libraryIsEmpty,
                onOpenRandomManga = onOpenRandomManga,
                onToggleSelectionMode = onToggleSelectionMode,
                onRepairBrokenCovers = onRepairBrokenCovers,
                onImportBackup = onImportBackup,
                onImportLocal = onImportLocal,
                onDismiss = { overflowExpanded = false },
            )
        }
    }
}

@Composable
private fun LibraryOverflowItems(
    state: LibraryUiState,
    strings: DesktopStrings,
    failedCovers: Map<Long, String>,
    isRepairingCovers: Boolean,
    libraryIsEmpty: Boolean,
    onOpenRandomManga: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onRepairBrokenCovers: (() -> Unit)?,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(strings.libraryOpenRandom) },
        leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
        enabled = state.items.isNotEmpty(),
        onClick = {
            onDismiss()
            onOpenRandomManga()
        },
        modifier = Modifier.testTag("library-open-random"),
    )
    DropdownMenuItem(
        text = { Text(strings.libraryBatchSelect) },
        enabled = state.items.isNotEmpty(),
        onClick = {
            onDismiss()
            onToggleSelectionMode(!state.selectionState.isSelectionMode)
        },
        modifier = Modifier.testTag("library-toggle-selection"),
    )
    if (failedCovers.isNotEmpty() && onRepairBrokenCovers != null) {
        DropdownMenuItem(
            text = {
                Text(
                    if (isRepairingCovers) {
                        strings.coverRepairRunning
                    } else {
                        strings.coverRepairAction(failedCovers.size)
                    },
                )
            },
            enabled = !isRepairingCovers,
            onClick = {
                onDismiss()
                onRepairBrokenCovers()
            },
            modifier = Modifier.testTag("library-repair-covers-button"),
        )
    }
    if (!libraryIsEmpty) {
        DropdownMenuItem(
            text = { Text(strings.libraryImportBackup) },
            onClick = {
                onDismiss()
                onImportBackup()
            },
            modifier = Modifier.testTag("library-import-backup"),
        )
        DropdownMenuItem(
            text = { Text(strings.libraryImportLocal) },
            onClick = {
                onDismiss()
                onImportLocal()
            },
            modifier = Modifier.testTag("library-import-local"),
        )
    }
}

@Composable
internal fun LibraryDisplayModeControls(
    state: LibraryUiState,
    strings: DesktopStrings,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
) {
    val modes = listOf(
        LibraryDisplayMode.ComfortableGrid to strings.libraryDisplayComfortable,
        LibraryDisplayMode.CompactGrid to strings.libraryDisplayCompact,
        LibraryDisplayMode.CoverOnly to strings.libraryDisplayCoverOnly,
        LibraryDisplayMode.List to strings.libraryDisplayList,
    )
    for ((mode, label) in modes) {
        FilterChip(
            selected = state.displayMode == mode,
            onClick = { onDisplayModeChange(mode) },
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.testTag("display-mode-${mode.name}"),
        )
    }
}

@Composable
internal fun LibraryActionControls(
    state: LibraryUiState,
    strings: DesktopStrings,
    onOpenDisplayOptions: () -> Unit,
    onOpenFilterDialog: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopTooltipBox(text = strings.text(UiText.LibraryDisplayOptions)) {
                IconButton(onClick = onOpenDisplayOptions, modifier = Modifier.testTag("library-display-options")) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = strings.text(UiText.LibraryDisplayOptions),
                    )
                }
            }
            BadgedBox(
                badge = {
                    if (state.filterState.hasActiveFilters) {
                        Badge(modifier = Modifier.testTag("filter-active-badge")) {
                            Text(state.filterState.activeCount.toString())
                        }
                    }
                },
            ) {
                DesktopTooltipBox(text = strings.libraryFilterAndSort) {
                    OutlinedButton(
                        onClick = onOpenFilterDialog,
                        modifier = Modifier.testTag("library-filter-sort-button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = strings.libraryFilterAndSort,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.libraryFilterAndSort)
                    }
                }
            }
        }
    }
}
