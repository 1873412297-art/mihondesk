package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.image.CoverFailureRegistry
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus

@Composable
internal fun LibraryPane(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onMangaSelected: (Long) -> Unit,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onPairPhoneSync: (() -> Unit)?,
    onRetry: () -> Unit,
    onCategorySelected: (Long) -> Unit,
    onManageCategories: () -> Unit,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onGridSizeChange: (Float) -> Unit,
    onUnreadBadgeChange: (Boolean) -> Unit,
    onDownloadedBadgeChange: (Boolean) -> Unit,
    onOpenFilterDialog: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onToggleMangaSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBatchChangeCategories: () -> Unit,
    onBatchMarkRead: (Boolean) -> Unit,
    onBatchDownload: (Int) -> Unit,
    onBatchRemoveFromLibrary: () -> Unit,
    isUpdatingLibrary: Boolean = false,
    onUpdateLibrary: (() -> Unit)? = null,
    isRepairingCovers: Boolean = false,
    onRepairBrokenCovers: (() -> Unit)? = null,
    onOpenRandomManga: () -> Unit = {},
    onContinueReading: ((Long) -> Unit)? = null,
    sourceBaseUrlFor: (Long) -> String? = { null },
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    val coverFailures = mihon.desktop.image.LocalImageLoader.current?.coverFailures
    val failedCovers by (coverFailures?.failuresFlow ?: kotlinx.coroutines.flow.flowOf(emptyMap()))
        .collectAsState(emptyMap())
    var showDisplayOptions by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val useNarrowControls = maxWidth < 900.dp
        val headerActions: @Composable () -> Unit = {
            LibraryHeaderActions(
                state = state,
                strings = strings,
                failedCovers = failedCovers,
                isUpdatingLibrary = isUpdatingLibrary,
                isRepairingCovers = isRepairingCovers,
                onUpdateLibrary = onUpdateLibrary,
                onRepairBrokenCovers = onRepairBrokenCovers,
                onImportBackup = onImportBackup,
                onImportLocal = onImportLocal,
                onOpenRandomManga = onOpenRandomManga,
                onToggleSelectionMode = onToggleSelectionMode,
            )
        }
        val displayModeControls: @Composable () -> Unit = {
            LibraryDisplayModeControls(state, strings, onDisplayModeChange)
        }
        val libraryActionControls: @Composable () -> Unit = {
            LibraryActionControls(state, strings, { showDisplayOptions = true }, onOpenFilterDialog)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (useNarrowControls) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.libraryTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        headerActions()
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.libraryTitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    headerActions()
                }
            }

            if (useNarrowControls) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    displayModeControls()
                    libraryActionControls()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        displayModeControls()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        libraryActionControls()
                    }
                }
            }

            // Category Chips Row
            Row(
                modifier = Modifier.fillMaxWidth().testTag("library-categories-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(state.categories, key = { it.id }) { cat ->
                        val label = if (cat.id == SYSTEM_ALL_CATEGORY.id) strings.libraryAllCategory else cat.name
                        val isSelected = cat.id == state.selectedCategoryId
                        val totalCount = if (isSelected) state.items.size else null
                        val unreadCount = if (isSelected) state.items.sumOf { it.unreadCount }.toInt() else 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(cat.id) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(label)
                                    if (isSelected && totalCount != null && totalCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (unreadCount >
                                                0
                                            ) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (unreadCount >
                                                0
                                            ) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        ) {
                                            Text(
                                                text = if (unreadCount > 0) "$unreadCount" else "$totalCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("library-category-chip-${cat.id}"),
                        )
                    }
                }
                OutlinedButton(
                    onClick = onManageCategories,
                    modifier = Modifier.testTag("library-manage-categories-button"),
                ) {
                    Text(strings.libraryManageCategories)
                }
            }

            // Search Box
            val searchFocusRequester = LocalSearchFocusRequester.current
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (searchFocusRequester != null) Modifier.focusRequester(searchFocusRequester) else Modifier)
                    .trackTextInputFocus()
                    .testTag("library-search"),
                label = { Text(strings.librarySearchPlaceholder) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        DesktopTooltipBox(text = strings.text(UiText.ClearSearch)) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.text(UiText.ClearSearch))
                            }
                        }
                    }
                },
                singleLine = true,
            )

            // Shift+click in selection mode adds every title between the last clicked one and this one.
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            var selectionAnchorId by remember { mutableStateOf<Long?>(null) }
            androidx.compose.runtime.LaunchedEffect(state.selectionState.isSelectionMode) {
                if (!state.selectionState.isSelectionMode) selectionAnchorId = null
            }
            val toggleWithRange: (Long) -> Unit = { id ->
                if (windowInfo.keyboardModifiers.isShiftPressed) {
                    val selected = state.selectionState.selectedMangaIds
                    chapterRangeSelection(state.items.map { it.id }, selectionAnchorId, id, selected)
                        .minus(selected)
                        .forEach(onToggleMangaSelection)
                } else {
                    onToggleMangaSelection(id)
                }
                selectionAnchorId = id
            }

            // Manga Content Grid / List
            LibraryContent(
                state = state,
                // Desktop convention: Ctrl/Shift+click starts or extends a selection without a mode switch.
                onMangaSelected = { id ->
                    val modifiers = windowInfo.keyboardModifiers
                    if (modifiers.isCtrlPressed || modifiers.isMetaPressed || modifiers.isShiftPressed) {
                        toggleWithRange(id)
                    } else {
                        onMangaSelected(id)
                    }
                },
                onToggleMangaSelection = toggleWithRange,
                onRetry = onRetry,
                onClearFilters = onClearFilters,
                onImportBackup = onImportBackup,
                onImportLocal = onImportLocal,
                onPairPhoneSync = onPairPhoneSync,
                sourceBaseUrlFor = sourceBaseUrlFor,
                onContinueReading = onContinueReading,
                onMangaToggleRead = { id, read ->
                    onToggleSelectionMode(false)
                    onToggleMangaSelection(id)
                    onBatchMarkRead(read)
                },
                onMangaDownload = { id ->
                    onToggleSelectionMode(false)
                    onToggleMangaSelection(id)
                    onBatchDownload(1)
                },
                onMangaEditCategories = { id ->
                    onToggleSelectionMode(false)
                    onToggleMangaSelection(id)
                    onBatchChangeCategories()
                },
                onMangaRemoveFromLibrary = { id ->
                    onToggleSelectionMode(false)
                    onToggleMangaSelection(id)
                    onBatchRemoveFromLibrary()
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // Display Options Dialog
        if (showDisplayOptions) {
            LibraryDisplayOptionsDialog(
                showUnreadBadge = state.showUnreadBadge,
                showDownloadedBadge = state.showDownloadedBadge,
                gridSize = state.gridSize,
                showGridSize = state.displayMode != LibraryDisplayMode.List,
                onUnreadBadgeChange = onUnreadBadgeChange,
                onDownloadedBadgeChange = onDownloadedBadgeChange,
                onGridSizeChange = onGridSizeChange,
                onDismiss = { showDisplayOptions = false },
            )
        }

        // Floating Batch Action Bar at bottom
        if (state.selectionState.isSelectionMode || state.selectionState.isAnySelected) {
            LibraryBatchActionBar(
                selectedCount = state.selectionState.count,
                totalCount = state.items.size,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onChangeCategories = onBatchChangeCategories,
                onMarkRead = onBatchMarkRead,
                onDownloadChapters = onBatchDownload,
                onRemoveFromLibrary = onBatchRemoveFromLibrary,
                onExitSelection = { onToggleSelectionMode(false) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}
