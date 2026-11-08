package mihon.desktop.ui.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.ui.closeOnMouseBack
import mihon.desktop.ui.common.LocalSpacing
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    detailState: MangaDetailUiState = MangaDetailUiState(),
    standaloneDetails: Boolean = false,
    onQueryChange: (String) -> Unit,
    onMangaSelected: (Long) -> Unit,
    onBackFromDetail: () -> Unit = {},
    onReadChapter: (Long) -> Unit = {},
    onDetailRetry: () -> Unit = {},
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onPairPhoneSync: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    onCategorySelected: (Long) -> Unit = {},
    onManageCategories: () -> Unit = {},
    onEditMangaCategories: () -> Unit = {},
    onOpenTracking: () -> Unit = {},
    // Phase 11: Display mode, grid zoom, filter & sort, selection callbacks
    onDisplayModeChange: (LibraryDisplayMode) -> Unit = {},
    onGridSizeChange: (Float) -> Unit = {},
    onUnreadBadgeChange: (Boolean) -> Unit = {},
    onDownloadedBadgeChange: (Boolean) -> Unit = {},
    onOpenFilterDialog: () -> Unit = {},
    onCloseFilterDialog: () -> Unit = {},
    onFilterChange: (LibraryFilterState) -> Unit = {},
    onSortChange: (LibrarySortState) -> Unit = {},
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onToggleMangaSelection: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onBatchChangeCategories: () -> Unit = {},
    onBatchSetCategories: (List<Long>) -> Unit = {},
    onBatchCloseCategoryDialog: () -> Unit = {},
    onBatchMarkRead: (Boolean) -> Unit = {},
    onBatchDownload: (Int) -> Unit = {},
    onBatchRemoveFromLibrary: () -> Unit = {},
    onMangaToggleRead: ((Long, Boolean) -> Unit)? = null,
    onMangaDownload: ((Long) -> Unit)? = null,
    onMangaRemoveFromLibrary: ((Long) -> Unit)? = null,
    isUpdatingLibrary: Boolean = false,
    onUpdateLibrary: (() -> Unit)? = null,
    isRepairingCovers: Boolean = false,
    onRepairBrokenCovers: (() -> Unit)? = null,
    onCoverLoadFailed: ((Int) -> Unit)? = null,
    // Phase 16: Edit info, chapter filter/sort & actions
    onEditInfo: () -> Unit = {},
    onDismissEditInfo: () -> Unit = {},
    onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onResetMangaInfo: () -> Unit = {},
    onChapterFilterChange: (ChapterFilterState) -> Unit = {},
    onChapterSortChange: (ChapterSortState) -> Unit = {},
    onToggleBookmark: (Long) -> Unit = {},
    onToggleRead: (Long) -> Unit = {},
    onMarkPreviousRead: (Long) -> Unit = {},
    onDownloadChapter: (Long) -> Unit = {},
    onDeleteDownload: (Long) -> Unit = {},
    onDownloadBatch: (Int?) -> Unit = {},
    onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleBookmark) },
    onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleRead) },
    onBatchDownloadChapters: (Set<Long>) -> Unit = { ids -> ids.forEach(onDownloadChapter) },
    onBatchDeleteDownloads: (Set<Long>) -> Unit = { ids -> ids.forEach(onDeleteDownload) },
    onOpenChapterSettings: () -> Unit = {},
    onDismissChapterSettings: () -> Unit = {},
    onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = {},
    onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    onShowMissingChaptersChange: (Boolean) -> Unit = {},
    onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    onResetChapterSettingsToDefault: () -> Unit = {},
    mangaDetailActions: MangaDetailActions? = null,
    onToggleMangaLibrary: (() -> Unit)? = null,
    onRefreshMangaSource: (() -> Unit)? = null,
    isMangaLibraryActionRunning: Boolean = false,
    isMangaSourceRefreshing: Boolean = false,
    onDuplicateOpenManga: (Long) -> Unit = {},
    onDuplicateMigrate: (Long) -> Unit = {},
    onDuplicateAddAnyway: () -> Unit = {},
    onDuplicateDismiss: () -> Unit = {},
    onOpenChapterInWebView: ((Long) -> Unit)? = null,
    onOpenRandomManga: () -> Unit = {},
    onContinueReading: ((Long) -> Unit)? = null,
    sourceNameFor: (Long) -> String = { "Source #$it" },
    sourceBaseUrlFor: (Long) -> String? = { null },
    /** Remembered side-pane width in dp; 0 means the default share of the window. */
    detailPaneWidthDp: Int = 0,
    onDetailPaneWidthChange: (Int) -> Unit = {},
) {
    val resolvedMangaDetailActions = mangaDetailActions ?: MangaDetailActions(
        onReadChapter = onReadChapter,
        onEditCategories = onEditMangaCategories,
        onOpenTracking = onOpenTracking,
        onEditInfo = onEditInfo,
        onDismissEditInfo = onDismissEditInfo,
        onSaveMangaInfo = onSaveMangaInfo,
        onResetMangaInfo = onResetMangaInfo,
        onChapterFilterChange = onChapterFilterChange,
        onChapterSortChange = onChapterSortChange,
        onToggleBookmark = onToggleBookmark,
        onToggleRead = onToggleRead,
        onMarkPreviousRead = onMarkPreviousRead,
        onDownloadChapter = onDownloadChapter,
        onDeleteDownload = onDeleteDownload,
        onDownloadBatch = onDownloadBatch,
        onBatchBookmarkChapters = onBatchBookmarkChapters,
        onBatchMarkChaptersRead = onBatchMarkChaptersRead,
        onBatchDownloadChapters = onBatchDownloadChapters,
        onBatchDeleteDownloads = onBatchDeleteDownloads,
        onOpenChapterSettings = onOpenChapterSettings,
        onDismissChapterSettings = onDismissChapterSettings,
        onChapterDisplayModeChange = onChapterDisplayModeChange,
        onExcludedScanlatorsChange = onExcludedScanlatorsChange,
        onShowMissingChaptersChange = onShowMissingChaptersChange,
        onSetChapterSettingsAsDefault = onSetChapterSettingsAsDefault,
        onResetChapterSettingsToDefault = onResetChapterSettingsToDefault,
        onCoverLoadFailed = onCoverLoadFailed,
    )
    val scrollStates = remember { LibraryScrollStates() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("library-screen")) {
        val selected = state.selectedMangaId != null
        val density = androidx.compose.ui.platform.LocalDensity.current
        val defaultPane = maxWidth * DETAIL_PANE_DEFAULT_FRACTION
        var rawPaneWidth by remember(detailPaneWidthDp) {
            mutableStateOf(if (detailPaneWidthDp > 0) detailPaneWidthDp.dp else defaultPane)
        }
        val maxPane = (maxWidth * DETAIL_PANE_MAX_FRACTION).coerceAtLeast(DETAIL_PANE_MIN_WIDTH)
        val paneWidth = rawPaneWidth.coerceIn(DETAIL_PANE_MIN_WIDTH, maxPane)
        val gridWidth = maxWidth - paneWidth - DETAIL_PANE_GAP
        // Live drag width is read in the layout phase only, so dragging a divider relayouts the pane
        // instead of recomposing the grid and the detail pane on every pointer move.
        val livePaneWidthPx = remember { mutableFloatStateOf(Float.NaN) }
        // Side by side only while the grid still fits two columns; otherwise details open as a full page.
        val sideBySide = selected && !standaloneDetails &&
            gridFitsTwoColumns(state.displayMode, gridWidth, state.gridSize, LocalSpacing.current.gridGap)
        androidx.compose.runtime.CompositionLocalProvider(LocalLibraryScrollStates provides scrollStates) {
            if ((selected && !sideBySide) || standaloneDetails) {
                MangaDetailScreen(
                    state = detailState,
                    sourceBaseUrl = detailState.manga?.sourceId?.let(sourceBaseUrlFor),
                    onBack = onBackFromDetail,
                    actions = resolvedMangaDetailActions,
                    onRetry = onDetailRetry,
                    onToggleLibrary = onToggleMangaLibrary,
                    onRefreshSource = onRefreshMangaSource,
                    isLibraryActionRunning = isMangaLibraryActionRunning,
                    isRefreshingSource = isMangaSourceRefreshing,
                    onOpenChapterInWebView = onOpenChapterInWebView,
                    showBack = true,
                    modifier = Modifier.fillMaxSize().closeOnMouseBack(onBackFromDetail),
                )
            } else {
                Row(modifier = Modifier.fillMaxSize().closeOnMouseBack(if (sideBySide) onBackFromDetail else null)) {
                    LibraryPane(
                        state = state,
                        onQueryChange = onQueryChange,
                        onMangaSelected = { id ->
                            // Clicking the open book again closes its pane.
                            if (sideBySide && id == state.selectedMangaId) onBackFromDetail() else onMangaSelected(id)
                        },
                        onImportBackup = onImportBackup,
                        onImportLocal = onImportLocal,
                        onPairPhoneSync = onPairPhoneSync,
                        onUpdateLibrary = onUpdateLibrary,
                        isUpdatingLibrary = isUpdatingLibrary,
                        isRepairingCovers = isRepairingCovers,
                        onRepairBrokenCovers = onRepairBrokenCovers,
                        onRetry = onRetry,
                        onCategorySelected = onCategorySelected,
                        onManageCategories = onManageCategories,
                        onDisplayModeChange = onDisplayModeChange,
                        onGridSizeChange = onGridSizeChange,
                        onUnreadBadgeChange = onUnreadBadgeChange,
                        onDownloadedBadgeChange = onDownloadedBadgeChange,
                        onOpenFilterDialog = onOpenFilterDialog,
                        onClearFilters = {
                            onQueryChange("")
                            onFilterChange(LibraryFilterState())
                            onCategorySelected(SYSTEM_ALL_CATEGORY.id)
                        },
                        onToggleSelectionMode = onToggleSelectionMode,
                        onToggleMangaSelection = onToggleMangaSelection,
                        onSelectAll = onSelectAll,
                        onDeselectAll = onDeselectAll,
                        onBatchChangeCategories = onBatchChangeCategories,
                        onBatchMarkRead = onBatchMarkRead,
                        onBatchDownload = onBatchDownload,
                        onBatchRemoveFromLibrary = onBatchRemoveFromLibrary,
                        onOpenRandomManga = onOpenRandomManga,
                        onContinueReading = onContinueReading,
                        sourceBaseUrlFor = sourceBaseUrlFor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (sideBySide) Modifier.closeOnEmptyTap(onBackFromDetail) else Modifier)
                            .testTag("library-grid-pane"),
                    )

                    if (sideBySide) {
                        DetailPaneDivider(
                            onDrag = { deltaPx ->
                                val base = livePaneWidthPx.floatValue.takeUnless(Float::isNaN)
                                    ?: with(density) { paneWidth.toPx() }
                                livePaneWidthPx.floatValue = base - deltaPx
                            },
                            onDragEnd = {
                                val finalPx = livePaneWidthPx.floatValue.takeUnless(Float::isNaN)
                                    ?: with(density) { paneWidth.toPx() }
                                val finalWidth = with(density) { finalPx.toDp() }
                                livePaneWidthPx.floatValue = Float.NaN
                                if (finalWidth < DETAIL_PANE_MIN_WIDTH - DETAIL_PANE_CLOSE_SLACK) {
                                    // Dragged to the far right: close and keep the last good width.
                                    rawPaneWidth = if (detailPaneWidthDp > 0) detailPaneWidthDp.dp else defaultPane
                                    onBackFromDetail()
                                } else {
                                    rawPaneWidth = finalWidth.coerceIn(DETAIL_PANE_MIN_WIDTH, maxPane)
                                    onDetailPaneWidthChange(rawPaneWidth.value.toInt())
                                }
                            },
                        )
                        DetailPaneCard(
                            selectedMangaId = state.selectedMangaId,
                            onClose = onBackFromDetail,
                            modifier = Modifier
                                .livePaneWidth(livePaneWidthPx, paneWidth, DETAIL_PANE_MIN_WIDTH, maxPane)
                                .fillMaxHeight()
                                .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
                        ) {
                            MangaDetailScreen(
                                state = detailState,
                                sourceBaseUrl = detailState.manga?.sourceId?.let(sourceBaseUrlFor),
                                onBack = onBackFromDetail,
                                actions = resolvedMangaDetailActions,
                                onRetry = onDetailRetry,
                                onToggleLibrary = onToggleMangaLibrary,
                                onRefreshSource = onRefreshMangaSource,
                                isLibraryActionRunning = isMangaLibraryActionRunning,
                                isRefreshingSource = isMangaSourceRefreshing,
                                onOpenChapterInWebView = onOpenChapterInWebView,
                                showBack = false,
                                onClose = onBackFromDetail,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // Filter & Sort Dialog
        if (state.isFilterDialogOpen) {
            LibraryFilterDialog(
                filterState = state.filterState,
                sortState = state.sortState,
                onFilterChange = onFilterChange,
                onSortChange = onSortChange,
                onDismiss = onCloseFilterDialog,
            )
        }

        // Batch Category Dialog
        if (state.isBatchCategoryDialogOpen) {
            BatchCategorySelectionDialog(
                categories = state.categories.filter { it.id != SYSTEM_ALL_CATEGORY.id },
                onDismiss = onBatchCloseCategoryDialog,
                onConfirm = onBatchSetCategories,
            )
        }

        state.duplicateDialog?.let { duplicateDialog ->
            DuplicateMangaDialog(
                state = duplicateDialog,
                sourceNameFor = sourceNameFor,
                onDismissRequest = onDuplicateDismiss,
                onAddAnyway = onDuplicateAddAnyway,
                onOpenManga = onDuplicateOpenManga,
                onMigrate = onDuplicateMigrate,
            )
        }
    }
}

/** Reads the live divider width in the layout phase so pointer moves do not recompose the screen. */
private fun Modifier.livePaneWidth(
    livePx: androidx.compose.runtime.MutableFloatState,
    settled: Dp,
    min: Dp,
    max: Dp,
): Modifier = layout { measurable, constraints ->
    val live = livePx.floatValue
    val width = (if (live.isNaN()) settled.roundToPx() else live.roundToInt())
        .coerceIn(min.roundToPx(), max.roundToPx())
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, placeable.height) { placeable.place(0, 0) }
}
