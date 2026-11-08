package mihon.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.NavRailState
import mihon.desktop.ui.common.ProvideContentDensity
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.tasks.DownloadsNavBadge

@Composable
internal fun DesktopShell(
    state: DesktopShellState,
    actions: DesktopShellActions,
) {
    val strings = LocalStrings.current
    var isCookieManagerOpen by remember { mutableStateOf(false) }
    val primary = RAIL_PRIMARY_DESTINATIONS
    val secondary = RAIL_SECONDARY_DESTINATIONS
    val railSelected = railSelectionFor(state.railSection)
    val contentDensity = state.contentDensity
    val railCollapsedPref = state.railCollapsed
    var narrowOverlayOpen by remember { mutableStateOf(false) }
    val downloadBadge = state.downloadsBadge
    val snackbarHostState = LocalSnackbarHostState.current

    ShellFrame(contentDensity) {
        val narrow = maxWidth < NavRailState.AUTO_COLLAPSE_WIDTH
        val railState = NavRailState.resolve(railCollapsedPref, narrow, narrowOverlayOpen)
        val toggleRail = {
            if (narrow) {
                narrowOverlayOpen = !narrowOverlayOpen
            } else {
                actions.onRailCollapsedChange(!railCollapsedPref)
            }
        }
        val toggleRequests = state.railToggleRequests
        val seenToggleRequests = remember { mutableIntStateOf(toggleRequests) }
        LaunchedEffect(toggleRequests) {
            if (toggleRequests != seenToggleRequests.intValue) {
                seenToggleRequests.intValue = toggleRequests
                toggleRail()
            }
        }
        Row {
            DesktopRail(
                expanded = railState == NavRailState.Expanded,
                shellSelected = state.selected,
                railSelected = railSelected,
                updatesUnreadCount = state.updatesUnreadCount,
                downloadsBadge = downloadBadge,
                onToggle = toggleRail,
                onDestinationSelected = actions.onDestinationSelected,
                onLockNow = actions.onLockNow,
            )
            Column(modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.pagePadding)) {
                if (state.libraryBatchState.running || state.libraryBatchState.error != null) {
                    Surface(
                        color = if (state.libraryBatchState.error != null) {
                            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("library-batch-progress"),
                    ) {
                        Text(
                            state.libraryBatchState.error ?: strings.text(
                                UiText.Processing,
                                state.libraryBatchState.processed,
                                state.libraryBatchState.total,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (state.trackSyncService != null && state.trackerManager != null) {
                    mihon.desktop.ui.track.TrackingRecoveryNotice(state.trackSyncService, state.trackerManager)
                }
                if (state.incognitoMode) {
                    Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("incognito-banner"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.incognitoBannerText,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            androidx.compose.material3.Button(
                                onClick = actions.onToggleIncognito,
                                modifier = Modifier.testTag("incognito-disable-button"),
                            ) {
                                Text(strings.incognitoDisable)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    when (state.selected) {
                        DesktopDestination.Library -> Column {
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
                        DesktopDestination.History -> Column {
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
                        DesktopDestination.Downloads -> {
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
                        DesktopDestination.Updates -> {
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
                        DesktopDestination.Settings, DesktopDestination.About -> {
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
                                        onOpenCookieManager = { isCookieManagerOpen = true },
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
                                        initialSection = if (state.selected == DesktopDestination.About) {
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
                                    text = strings.destinationLabel(state.selected),
                                    modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                        DesktopDestination.Stats -> Column {
                            mihon.desktop.ui.stats.StatsScreen(
                                data = state.statsData,
                                onRefresh = actions.onRefreshStats,
                                onOpenHistory = { actions.onDestinationSelected(DesktopDestination.History) },
                            )
                        }
                        DesktopDestination.Browse -> {
                            actions.browseContent?.invoke() ?: Text(
                                text = strings.destinationLabel(state.selected),
                                modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }

                    if (isCookieManagerOpen && state.cookieStore != null) {
                        mihon.desktop.ui.network.CookieManagerDialog(
                            cookieStore = state.cookieStore,
                            onDismissRequest = { isCookieManagerOpen = false },
                        )
                    }

                    if (snackbarHostState != null) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .widthIn(max = 560.dp)
                                .semantics {
                                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                                },
                        )
                    }
                }
            }
        }
        if (railState == NavRailState.OverlayExpanded) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                    )
                    .clickable { narrowOverlayOpen = false }
                    .testTag(DESKTOP_RAIL_SCRIM_TEST_TAG),
            )
            DesktopRail(
                expanded = true,
                shellSelected = state.selected,
                railSelected = railSelected,
                updatesUnreadCount = state.updatesUnreadCount,
                downloadsBadge = downloadBadge,
                onToggle = toggleRail,
                onDestinationSelected = { destination ->
                    narrowOverlayOpen = false
                    actions.onDestinationSelected(destination)
                },
                onLockNow = actions.onLockNow,
                tonalElevation = 6.dp,
            )
        }
    }
}
