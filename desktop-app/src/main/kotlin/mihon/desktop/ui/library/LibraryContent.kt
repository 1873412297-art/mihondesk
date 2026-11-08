package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.onSecondaryClick

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onMangaSelected: (Long) -> Unit,
    onToggleMangaSelection: (Long) -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onImportBackup: () -> Unit = {},
    onImportLocal: () -> Unit = {},
    onPairPhoneSync: (() -> Unit)? = null,
    sourceBaseUrlFor: (Long) -> String? = { null },
    onContinueReading: ((Long) -> Unit)? = null,
    onMangaToggleRead: (Long, Boolean) -> Unit = { _, _ -> },
    onMangaDownload: (Long) -> Unit = {},
    onMangaEditCategories: (Long) -> Unit = {},
    onMangaRemoveFromLibrary: (Long) -> Unit = {},
    modifier: Modifier,
) {
    when {
        state.loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag("library-loading"))
        }
        state.errorMessage != null -> ErrorState(state.errorMessage, onRetry, modifier)
        state.items.isEmpty() -> EmptyState(
            state = state,
            onClearFilters = onClearFilters,
            onImportBackup = onImportBackup,
            onImportLocal = onImportLocal,
            onPairPhoneSync = onPairPhoneSync,
            modifier = modifier,
        )
        else -> BoxWithConstraints(modifier = modifier) {
            when (state.displayMode) {
                LibraryDisplayMode.ComfortableGrid -> {
                    val spacing = LocalSpacing.current.gridGap
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        state = LocalLibraryScrollStates.current?.grid ?: rememberLazyGridState(),
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-comfortable"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.gridRowGap),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            var showContextMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.onSecondaryClick { showContextMenu = true }) {
                                ComfortableMangaCard(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                    showUnreadBadge = state.showUnreadBadge,
                                    showDownloadedBadge = state.showDownloadedBadge,
                                    sourceBaseUrl = sourceBaseUrlFor(manga.sourceId),
                                    onClick = {
                                        if (state.selectionState.isSelectionMode) {
                                            onToggleMangaSelection(manga.id)
                                        } else {
                                            onMangaSelected(manga.id)
                                        }
                                    },
                                )
                                ContinueReadingButton(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    onContinueReading = onContinueReading,
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                                )
                                LibraryMangaContextMenu(
                                    manga = manga,
                                    expanded = showContextMenu,
                                    onDismissRequest = { showContextMenu = false },
                                    onOpenDetails = { onMangaSelected(manga.id) },
                                    onResumeReading = onContinueReading?.let { open -> { open(manga.id) } },
                                    onToggleRead = { onMangaToggleRead(manga.id, manga.unreadCount > 0) },
                                    onDownload = { onMangaDownload(manga.id) },
                                    onEditCategories = { onMangaEditCategories(manga.id) },
                                    onRemoveFromLibrary = { onMangaRemoveFromLibrary(manga.id) },
                                )
                            }
                        }
                    }
                }
                LibraryDisplayMode.CompactGrid -> {
                    val spacing = (12 * LocalSpacing.current.gridScale).dp
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        state = LocalLibraryScrollStates.current?.grid ?: rememberLazyGridState(),
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-compact"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            var showContextMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.onSecondaryClick { showContextMenu = true }) {
                                CompactMangaCard(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                    showUnreadBadge = state.showUnreadBadge,
                                    showDownloadedBadge = state.showDownloadedBadge,
                                    sourceBaseUrl = sourceBaseUrlFor(manga.sourceId),
                                    onClick = {
                                        if (state.selectionState.isSelectionMode) {
                                            onToggleMangaSelection(manga.id)
                                        } else {
                                            onMangaSelected(manga.id)
                                        }
                                    },
                                )
                                ContinueReadingButton(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    onContinueReading = onContinueReading,
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                                )
                                LibraryMangaContextMenu(
                                    manga = manga,
                                    expanded = showContextMenu,
                                    onDismissRequest = { showContextMenu = false },
                                    onOpenDetails = { onMangaSelected(manga.id) },
                                    onResumeReading = onContinueReading?.let { open -> { open(manga.id) } },
                                    onToggleRead = { onMangaToggleRead(manga.id, manga.unreadCount > 0) },
                                    onDownload = { onMangaDownload(manga.id) },
                                    onEditCategories = { onMangaEditCategories(manga.id) },
                                    onRemoveFromLibrary = { onMangaRemoveFromLibrary(manga.id) },
                                )
                            }
                        }
                    }
                }
                LibraryDisplayMode.CoverOnly -> {
                    val spacing = (10 * LocalSpacing.current.gridScale).dp
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        state = LocalLibraryScrollStates.current?.grid ?: rememberLazyGridState(),
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-coveronly"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            var showContextMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.onSecondaryClick { showContextMenu = true }) {
                                CoverOnlyMangaCard(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                    showUnreadBadge = state.showUnreadBadge,
                                    showDownloadedBadge = state.showDownloadedBadge,
                                    sourceBaseUrl = sourceBaseUrlFor(manga.sourceId),
                                    onClick = {
                                        if (state.selectionState.isSelectionMode) {
                                            onToggleMangaSelection(manga.id)
                                        } else {
                                            onMangaSelected(manga.id)
                                        }
                                    },
                                )
                                ContinueReadingButton(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    onContinueReading = onContinueReading,
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                                )
                                LibraryMangaContextMenu(
                                    manga = manga,
                                    expanded = showContextMenu,
                                    onDismissRequest = { showContextMenu = false },
                                    onOpenDetails = { onMangaSelected(manga.id) },
                                    onResumeReading = onContinueReading?.let { open -> { open(manga.id) } },
                                    onToggleRead = { onMangaToggleRead(manga.id, manga.unreadCount > 0) },
                                    onDownload = { onMangaDownload(manga.id) },
                                    onEditCategories = { onMangaEditCategories(manga.id) },
                                    onRemoveFromLibrary = { onMangaRemoveFromLibrary(manga.id) },
                                )
                            }
                        }
                    }
                }
                LibraryDisplayMode.List -> {
                    LazyColumn(
                        state = LocalLibraryScrollStates.current?.list ?: rememberLazyListState(),
                        modifier = Modifier.fillMaxSize().testTag("library-view-list"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.listRowGap),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            var showContextMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.onSecondaryClick { showContextMenu = true }) {
                                ListMangaItem(
                                    manga = manga,
                                    isSelectionMode = state.selectionState.isSelectionMode,
                                    isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                    showUnreadBadge = state.showUnreadBadge,
                                    showDownloadedBadge = state.showDownloadedBadge,
                                    sourceBaseUrl = sourceBaseUrlFor(manga.sourceId),
                                    onClick = {
                                        if (state.selectionState.isSelectionMode) {
                                            onToggleMangaSelection(manga.id)
                                        } else {
                                            onMangaSelected(manga.id)
                                        }
                                    },
                                )
                                LibraryMangaContextMenu(
                                    manga = manga,
                                    expanded = showContextMenu,
                                    onDismissRequest = { showContextMenu = false },
                                    onOpenDetails = { onMangaSelected(manga.id) },
                                    onResumeReading = onContinueReading?.let { open -> { open(manga.id) } },
                                    onToggleRead = { onMangaToggleRead(manga.id, manga.unreadCount > 0) },
                                    onDownload = { onMangaDownload(manga.id) },
                                    onEditCategories = { onMangaEditCategories(manga.id) },
                                    onRemoveFromLibrary = { onMangaRemoveFromLibrary(manga.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
