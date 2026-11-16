package mihon.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.motion.LocalReducedMotion
import mihon.desktop.ui.motion.fadeThrough

/**
 * The page for the selected top-level destination. Switching pages fades through: the old page
 * fades out, the new one rises into place. Settings and About are one page (About is a section),
 * so moving between them does not animate.
 */
@Composable
internal fun ShellDestinationContent(
    state: DesktopShellState,
    actions: DesktopShellActions,
    onOpenCookieManager: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state.selected,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeThrough(reduced) },
        contentKey = { if (it == DesktopDestination.About) DesktopDestination.Settings else it },
        label = "destination",
    ) { selected ->
        DestinationPage(selected, state, actions, onOpenCookieManager)
    }
}

/** [selected] is the page being drawn; while a page fades out it differs from `state.selected`. */
@Composable
private fun DestinationPage(
    selected: DesktopDestination,
    state: DesktopShellState,
    actions: DesktopShellActions,
    onOpenCookieManager: () -> Unit,
) {
    when (selected) {
        DesktopDestination.Library -> LibraryDestination(state, actions)
        DesktopDestination.History -> HistoryDestination(state, actions)
        DesktopDestination.Downloads -> DownloadsDestination(state, actions)
        DesktopDestination.Updates -> UpdatesDestination(state, actions)
        DesktopDestination.Settings -> SettingsDestination(selected, state, actions, onOpenCookieManager)
        DesktopDestination.About -> SettingsDestination(selected, state, actions, onOpenCookieManager)
        DesktopDestination.Stats -> StatsDestination(state, actions)
        DesktopDestination.Browse -> BrowseDestination(selected, state, actions)
    }
}

@Composable
private fun LibraryDestination(state: DesktopShellState, actions: DesktopShellActions) {
    Column {
        LibraryScreen(
            state = state.libraryState,
            detailState = state.mangaDetailState,
            standaloneDetails = state.standaloneMangaDetails,
            onQueryChange = actions.library.query,
            onMangaSelected = actions.library.selectManga,
            onBackFromDetail = actions.onBackFromMangaDetail,
            detailPaneWidthDp = state.libraryDetailPaneWidth,
            onDetailPaneWidthChange = actions.library.paneWidth,
            onReadChapter = actions.onReadChapter,
            onDetailRetry = actions.library.retryDetail,
            onImportBackup = actions.onImportBackup,
            onImportLocal = actions.onImportLocal,
            onPairPhoneSync = actions.onPairPhoneSync,
            onRetry = actions.library.retry,
            onCategorySelected = actions.library.category,
            onManageCategories = actions.library.manageCategories,
            onEditMangaCategories = actions.library.editCategories,
            onOpenTracking = actions.library.tracking,
            onDisplayModeChange = actions.library.displayMode,
            onGridSizeChange = actions.library.gridSize,
            onUnreadBadgeChange = actions.library.unreadBadge,
            onDownloadedBadgeChange = actions.library.downloadedBadge,
            onOpenFilterDialog = actions.library.openFilter,
            onCloseFilterDialog = actions.library.closeFilter,
            onFilterChange = actions.library.filter,
            onSortChange = actions.library.sort,
            onToggleSelectionMode = actions.library.selectionMode,
            onToggleMangaSelection = actions.library.selectMangaToggle,
            onSelectAll = actions.library.selectAll,
            onDeselectAll = actions.library.deselectAll,
            onBatchChangeCategories = actions.library.batchChangeCategories,
            onBatchSetCategories = actions.library.batchSetCategories,
            onBatchCloseCategoryDialog = actions.library.batchCloseCategories,
            onBatchMarkRead = actions.library.batchRead,
            onBatchDownload = actions.library.batchDownload,
            onBatchRemoveFromLibrary = actions.library.batchRemove,
            isUpdatingLibrary = state.isUpdatingLibrary,
            onUpdateLibrary = actions.onUpdateLibrary,
            isRepairingCovers = state.isRepairingCovers,
            onRepairBrokenCovers = actions.onRepairBrokenCovers,
            onEditInfo = actions.onEditInfo,
            onDismissEditInfo = actions.onDismissEditInfo,
            onSaveMangaInfo = actions.onSaveMangaInfo,
            onResetMangaInfo = actions.onResetMangaInfo,
            onChapterFilterChange = actions.onChapterFilterChange,
            onChapterSortChange = actions.onChapterSortChange,
            onToggleBookmark = actions.onToggleBookmark,
            onToggleRead = actions.onToggleRead,
            onMarkPreviousRead = actions.onMarkPreviousRead,
            onDownloadChapter = actions.onDownloadChapter,
            onDeleteDownload = actions.onDeleteDownload,
            onDownloadBatch = actions.onDownloadBatch,
            onBatchBookmarkChapters = actions.onBatchBookmarkChapters,
            onBatchMarkChaptersRead = actions.onBatchMarkChaptersRead,
            onBatchDownloadChapters = actions.onBatchDownloadChapters,
            onBatchDeleteDownloads = actions.onBatchDeleteDownloads,
            onOpenChapterSettings = actions.onOpenChapterSettings,
            onDismissChapterSettings = actions.onDismissChapterSettings,
            onChapterDisplayModeChange = actions.onChapterDisplayModeChange,
            onExcludedScanlatorsChange = actions.onExcludedScanlatorsChange,
            onShowMissingChaptersChange = actions.onShowMissingChaptersChange,
            onSetChapterSettingsAsDefault = actions.onSetChapterSettingsAsDefault,
            onResetChapterSettingsToDefault = actions.onResetChapterSettingsToDefault,
            mangaDetailActions = actions.mangaDetailActions,
            onToggleMangaLibrary = actions.onToggleMangaLibrary,
            onRefreshMangaSource = actions.onRefreshMangaSource,
            isMangaLibraryActionRunning = state.isMangaLibraryActionRunning,
            isMangaSourceRefreshing = state.isMangaSourceRefreshing,
            onDuplicateOpenManga = actions.onDuplicateOpenManga,
            onDuplicateMigrate = actions.onDuplicateMigrate,
            onDuplicateAddAnyway = actions.onDuplicateAddAnyway,
            onDuplicateDismiss = actions.onDuplicateDismiss,
            onOpenChapterInWebView = actions.onOpenChapterInWebView,
            onOpenRandomManga = actions.onOpenRandomManga,
            onContinueReading = actions.onContinueReading,
            sourceNameFor = state.sourceNameFor,
            sourceBaseUrlFor = state.sourceBaseUrlFor,
        )
    }
}

@Composable
private fun HistoryDestination(state: DesktopShellState, actions: DesktopShellActions) {
    Column {
        mihon.desktop.ui.history.HistoryScreen(
            groups = state.historyGroups,
            onOpenStats = { actions.onDestinationSelected(DesktopDestination.Stats) },
            query = state.historyQuery,
            onQueryChange = actions.onHistoryQueryChange,
            onReadChapter = actions.onReadChapter,
            onDeleteItem = actions.onDeleteHistoryItem,
            onClearAll = actions.onClearAllHistory,
        )
    }
}

@Composable
private fun DownloadsDestination(state: DesktopShellState, actions: DesktopShellActions) {
    mihon.desktop.ui.tasks.DownloadsDestination(
        queue = state.downloadsQueue,
        speedBytesPerSec = state.downloadSpeed,
        isRunning = state.isDownloaderRunning,
        progressOf = state.downloadProgressOf,
        recoveryMessage = state.downloadRecoveryMessage,
        storageError = state.downloadStorageError,
        actions = actions.downloadActions,
    )
}

@Composable
private fun UpdatesDestination(state: DesktopShellState, actions: DesktopShellActions) {
    if (state.isUpcomingOpen || actions.upcomingContent != null) {
        if (actions.upcomingContent != null) {
            actions.upcomingContent.invoke()
        } else {
            mihon.desktop.ui.upcoming.UpcomingScreen(
                state = mihon.desktop.ui.upcoming.UpcomingUiState(loading = false),
                onBack = actions.onCloseUpcoming,
            )
        }
    } else {
        mihon.desktop.ui.updates.UpdatesScreen(
            updatedChapters = state.updatedChapters,
            isUpdating = state.isUpdatingLibrary,
            lastResult = state.lastUpdateResult,
            onCheckForUpdates = actions.onCheckForUpdates,
            onReadChapter = actions.onReadChapter,
            onOpenUpcoming = actions.onOpenUpcoming,
            runState = state.updateRunState,
            progress = state.updateProgress,
            sourceNameFor = state.sourceNameFor,
            onCancelUpdate = actions.onCancelLibraryUpdate,
            onMarkChapterRead = actions.onMarkUpdatesChapterRead,
            onDownloadChapter = actions.onDownloadUpdatesChapter,
        )
    }
}

@Composable
private fun SettingsDestination(
    selected: DesktopDestination,
    state: DesktopShellState,
    actions: DesktopShellActions,
    onOpenCookieManager: () -> Unit,
) {
    val strings = LocalStrings.current
    if (state.preferenceStore != null && state.readerSettingsStore != null) {
        Column {
            mihon.desktop.ui.settings.SettingsScreen(
                preferenceStore = state.preferenceStore,
                readerSettingsStore = state.readerSettingsStore,
                diagnosticService = state.diagnosticService,
                trackerManager = state.trackerManager,
                backupScheduler = state.backupScheduler,
                syncScheduler = state.syncScheduler,
                backgroundScheduler = state.backgroundScheduler,
                updateScheduler = state.updateScheduler,
                onOpenCookieManager = { onOpenCookieManager() },
                onImportBackup = actions.onImportBackup,
                onExportBackup = actions.onExportBackup,
                onOpenOnboarding = actions.onOpenOnboarding,
                onPreferencesChanged = actions.onPreferencesChanged,
                downloadCacheCleaner = state.downloadCacheCleaner,
                downloadsDir = state.downloadsDir,
                diskCacheDir = state.diskCacheDir,
                profileRoot = state.profileRoot,
                databaseCleaner = state.databaseCleaner,
                downloadCategories = state.libraryState.categories,
                appLockController = state.appLockController,
                initialSection = if (selected == DesktopDestination.About) {
                    mihon.desktop.ui.settings.SettingsSection.About
                } else {
                    state.settingsInitialSection
                },
                aboutContent = {
                    mihon.desktop.ui.settings.AboutScreen(
                        updateContent = actions.appUpdateContent,
                    )
                },
                contentDensity = state.contentDensity,
                onContentDensityChange = actions.onContentDensityChange,
            )
        }
    } else {
        Text(
            text = strings.destinationLabel(selected),
            modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun StatsDestination(state: DesktopShellState, actions: DesktopShellActions) {
    Column {
        mihon.desktop.ui.stats.StatsScreen(
            data = state.statsData,
            onRefresh = actions.onRefreshStats,
            onOpenHistory = { actions.onDestinationSelected(DesktopDestination.History) },
        )
    }
}

@Composable
private fun BrowseDestination(selected: DesktopDestination, state: DesktopShellState, actions: DesktopShellActions) {
    val strings = LocalStrings.current
    actions.browseContent?.invoke() ?: Text(
        text = strings.destinationLabel(selected),
        modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
    )
}
