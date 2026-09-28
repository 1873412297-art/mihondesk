package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.unit.sp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState

internal const val DESKTOP_MAIN_HEADLINE_TEST_TAG = "desktop-main-headline"
internal const val DESKTOP_NAVIGATION_RAIL_TEST_TAG = "desktop-navigation-rail"
const val DESKTOP_LOCK_NOW_BUTTON_TEST_TAG = "desktop-lock-now-button"

@Composable
fun DesktopShell(
    state: DesktopShellState,
    actions: DesktopShellActions,
) {
    val strings = LocalStrings.current
    var isCookieManagerOpen by remember { mutableStateOf(false) }
    val primary = DesktopDestination.entries.take(5)
    val secondary = DesktopDestination.entries.drop(5)
    val snackbarHostState = LocalSnackbarHostState.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail(
                modifier = Modifier.fillMaxHeight()
                    .width(80.dp)
                    .testTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        primary.forEach { destination ->
                            DestinationItem(
                                destination = destination,
                                selected = state.selected,
                                onDestinationSelected = actions.onDestinationSelected,
                                alwaysShowLabel = true,
                                badgeCount = if (destination == DesktopDestination.Updates) {
                                    state.updatesUnreadCount
                                } else {
                                    0
                                },
                            )
                        }
                    }
                    Column {
                        HorizontalDivider()
                        secondary.forEach { destination ->
                            DestinationItem(
                                destination = destination,
                                selected = state.selected,
                                onDestinationSelected = actions.onDestinationSelected,
                                alwaysShowLabel = false,
                                badgeCount = if (destination == DesktopDestination.Updates) {
                                    state.updatesUnreadCount
                                } else {
                                    0
                                },
                            )
                        }
                        if (actions.onLockNow != null) {
                            HorizontalDivider()
                            DesktopTooltipBox(text = strings.text(UiText.LockApp)) {
                                IconButton(
                                    onClick = actions.onLockNow,
                                    modifier = Modifier.testTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = strings.text(UiText.LockApp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                if (state.libraryBatchState.running || state.libraryBatchState.error != null) {
                    Surface(
                        color = if (state.libraryBatchState.error != null) {
                            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("library-batch-progress"),
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
                        DesktopDestination.Library -> {
                            LibraryScreen(
                                state = state.libraryState,
                                detailState = state.mangaDetailState,
                                standaloneDetails = state.standaloneMangaDetails,
                                onQueryChange = actions.onLibraryQueryChange,
                                onMangaSelected = actions.onMangaSelected,
                                onBackFromDetail = actions.onBackFromMangaDetail,
                                onReadChapter = actions.onReadChapter,
                                onDetailRetry = actions.onMangaDetailRetry,
                                onImportBackup = actions.onImportBackup,
                                onImportLocal = actions.onImportLocal,
                                onRetry = actions.onLibraryRetry,
                                onCategorySelected = actions.onCategorySelected,
                                onManageCategories = actions.onManageCategories,
                                onEditMangaCategories = actions.onEditMangaCategories,
                                onOpenTracking = actions.onOpenTracking,
                                onDisplayModeChange = actions.onDisplayModeChange,
                                onGridSizeChange = actions.onGridSizeChange,
                                onUnreadBadgeChange = actions.onUnreadBadgeChange,
                                onDownloadedBadgeChange = actions.onDownloadedBadgeChange,
                                onOpenFilterDialog = actions.onOpenFilterDialog,
                                onCloseFilterDialog = actions.onCloseFilterDialog,
                                onFilterChange = actions.onFilterChange,
                                onSortChange = actions.onSortChange,
                                onToggleSelectionMode = actions.onToggleSelectionMode,
                                onToggleMangaSelection = actions.onToggleMangaSelection,
                                onSelectAll = actions.onSelectAll,
                                onDeselectAll = actions.onDeselectAll,
                                onBatchChangeCategories = actions.onBatchChangeCategories,
                                onBatchSetCategories = actions.onBatchSetCategories,
                                onBatchCloseCategoryDialog = actions.onBatchCloseCategoryDialog,
                                onBatchMarkRead = actions.onBatchMarkRead,
                                onBatchDownload = actions.onBatchDownload,
                                onBatchRemoveFromLibrary = actions.onBatchRemoveFromLibrary,
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
                                sourceNameFor = state.sourceNameFor,
                                sourceBaseUrlFor = state.sourceBaseUrlFor,
                            )
                        }
                        DesktopDestination.History -> {
                            mihon.desktop.ui.history.HistoryScreen(
                                groups = state.historyGroups,
                                query = state.historyQuery,
                                onQueryChange = actions.onHistoryQueryChange,
                                onReadChapter = actions.onReadChapter,
                                onDeleteItem = actions.onDeleteHistoryItem,
                                onClearAll = actions.onClearAllHistory,
                            )
                        }
                        DesktopDestination.Downloads -> {
                            mihon.desktop.ui.tasks.DownloadsScreen(
                                queue = state.downloadsQueue,
                                isRunning = state.isDownloaderRunning,
                                speedBytesPerSec = state.downloadSpeedBytesPerSec,
                                onPauseAll = actions.onPauseAllDownloads,
                                onResumeAll = actions.onResumeAllDownloads,
                                onClearCompleted = actions.onClearCompletedDownloads,
                                onCancel = actions.onCancelDownload,
                                onPause = actions.onPauseDownload,
                                onResume = actions.onResumeDownload,
                                onMoveUp = actions.onMoveUpDownload,
                                onMoveDown = actions.onMoveDownDownload,
                                onRetry = actions.onRetryDownload,
                                onRetryAllFailed = actions.onRetryAllFailedDownloads,
                                onReadChapter = actions.onReadDownloadedChapter,
                                recoveryMessage = state.downloadRecoveryMessage,
                                storageError = state.downloadStorageError,
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
                        DesktopDestination.Settings -> {
                            if (state.preferenceStore != null && state.readerSettingsStore != null) {
                                mihon.desktop.ui.settings.SettingsScreen(
                                    preferenceStore = state.preferenceStore,
                                    readerSettingsStore = state.readerSettingsStore,
                                    diagnosticService = state.diagnosticService,
                                    trackerManager = state.trackerManager,
                                    backupScheduler = state.backupScheduler,
                                    syncScheduler = state.syncScheduler,
                                    syncServerManager = state.syncServerManager,
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
                                    appLockController = state.appLockController,
                                )
                            } else {
                                Text(
                                    text = strings.destinationLabel(state.selected),
                                    modifier = Modifier.testTag(DESKTOP_MAIN_HEADLINE_TEST_TAG),
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                        DesktopDestination.Stats -> {
                            mihon.desktop.ui.stats.StatsScreen(
                                data = state.statsData,
                                onRefresh = actions.onRefreshStats,
                            )
                        }
                        DesktopDestination.About -> {
                            mihon.desktop.ui.settings.AboutScreen(updateContent = actions.appUpdateContent)
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
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopShell(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    appUpdateContent: (@Composable () -> Unit)? = null,
    libraryState: LibraryUiState = LibraryUiState(),
    libraryBatchState: mihon.desktop.ui.library.LibraryBatchState = mihon.desktop.ui.library.LibraryBatchState(),
    mangaDetailState: MangaDetailUiState = MangaDetailUiState(),
    standaloneMangaDetails: Boolean = false,
    mangaDetailActions: MangaDetailActions? = null,
    onToggleMangaLibrary: (() -> Unit)? = null,
    onRefreshMangaSource: (() -> Unit)? = null,
    isMangaLibraryActionRunning: Boolean = false,
    isMangaSourceRefreshing: Boolean = false,
    onLibraryQueryChange: (String) -> Unit = {},
    onMangaSelected: (Long) -> Unit = {},
    onBackFromMangaDetail: () -> Unit = {},
    onReadChapter: (Long) -> Unit = {},
    onMangaDetailRetry: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onImportLocal: () -> Unit = {},
    onLibraryRetry: () -> Unit = {},
    // Downloads
    downloadsQueue: List<mihon.desktop.download.DesktopDownload> = emptyList(),
    isDownloaderRunning: Boolean = false,
    downloadSpeedBytesPerSec: Double = 0.0,
    downloadRecoveryMessage: String? = null,
    downloadStorageError: String? = null,
    onPauseAllDownloads: () -> Unit = {},
    onResumeAllDownloads: () -> Unit = {},
    onClearCompletedDownloads: () -> Unit = {},
    onCancelDownload: (Long) -> Unit = {},
    onPauseDownload: (Long) -> Unit = {},
    onResumeDownload: (Long) -> Unit = {},
    onMoveUpDownload: (Long) -> Unit = {},
    onMoveDownDownload: (Long) -> Unit = {},
    onRetryDownload: (Long) -> Unit = {},
    onRetryAllFailedDownloads: () -> Unit = {},
    onReadDownloadedChapter: (mangaId: Long, chapterId: Long) -> Unit = { _, chapterId -> onReadChapter(chapterId) },
    // Updates
    updatedChapters: List<mihon.desktop.updates.UpdatedChapterItem> = emptyList(),
    updatesUnreadCount: Int = 0,
    isUpdatingLibrary: Boolean = false,
    isRepairingCovers: Boolean = false,
    onRepairBrokenCovers: (() -> Unit)? = null,
    lastUpdateResult: mihon.desktop.updates.LibraryUpdateResult? = null,
    onCheckForUpdates: () -> Unit = {},
    updateRunState: mihon.desktop.library.update.LibraryUpdateRunState? = null,
    updateProgress: mihon.desktop.library.update.LibraryUpdateProgress? = null,
    onCancelLibraryUpdate: () -> Unit = {},
    onMarkUpdatesChapterRead: (Long, Long) -> Unit = { _, _ -> },
    onDownloadUpdatesChapter: (Long, Long) -> Unit = { _, _ -> },
    // Upcoming calendar (transient view opened from Updates)
    isUpcomingOpen: Boolean = false,
    upcomingContent: (@Composable () -> Unit)? = null,
    onOpenUpcoming: () -> Unit = {},
    onCloseUpcoming: () -> Unit = {},
    // Browse
    browseContent: (@Composable () -> Unit)? = null,
    // History
    historyGroups: List<mihon.desktop.history.DesktopHistoryGroup> = emptyList(),
    historyQuery: String = "",
    onHistoryQueryChange: (String) -> Unit = {},
    onDeleteHistoryItem: (Long) -> Unit = {},
    onClearAllHistory: () -> Unit = {},
    // Category & Tracking
    onCategorySelected: (Long) -> Unit = {},
    onManageCategories: () -> Unit = {},
    onEditMangaCategories: () -> Unit = {},
    onOpenTracking: () -> Unit = {},
    // Library Phase 11 callbacks
    onDisplayModeChange: (mihon.desktop.ui.library.LibraryDisplayMode) -> Unit = {},
    onGridSizeChange: (Float) -> Unit = {},
    onUnreadBadgeChange: (Boolean) -> Unit = {},
    onDownloadedBadgeChange: (Boolean) -> Unit = {},
    onOpenFilterDialog: () -> Unit = {},
    onCloseFilterDialog: () -> Unit = {},
    onFilterChange: (mihon.desktop.ui.library.LibraryFilterState) -> Unit = {},
    onSortChange: (mihon.desktop.ui.library.LibrarySortState) -> Unit = {},
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
    // Settings & Diagnostics
    preferenceStore: mihon.desktop.preferences.DesktopPreferenceStore? = null,
    readerSettingsStore: mihon.desktop.reader.DesktopReaderSettingsStore? = null,
    diagnosticService: mihon.desktop.diagnostics.DiagnosticBundleService? = null,
    trackerManager: mihon.desktop.track.DesktopTrackerManager? = null,
    trackSyncService: mihon.desktop.track.TrackOnReadSyncService? = null,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler? = null,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    syncServerManager: mihon.desktop.sync.DesktopSyncServerManager? = null,
    backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler? = null,
    onExportBackup: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onPreferencesChanged: ((mihon.desktop.preferences.DesktopPreferences) -> Unit)? = null,
    // Phase 14: Stats & Incognito
    statsData: mihon.desktop.stats.DesktopStatsData = mihon.desktop.stats.DesktopStatsData(),
    onRefreshStats: () -> Unit = {},
    incognitoMode: Boolean = false,
    onToggleIncognito: () -> Unit = {},
    // Phase 15: Library update & Cookie management
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler? = null,
    cookieStore: mihon.desktop.extension.DesktopCookieStore? = null,
    onUpdateLibrary: () -> Unit = {},
    // Phase 16: Manga edit info, chapter filter/sort & actions, storage cleaner
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
    onChapterFilterChange: (mihon.desktop.ui.library.ChapterFilterState) -> Unit = {},
    onChapterSortChange: (mihon.desktop.ui.library.ChapterSortState) -> Unit = {},
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
    onChapterDisplayModeChange: (mihon.desktop.ui.library.ChapterDisplayMode) -> Unit = {},
    onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    onShowMissingChaptersChange: (Boolean) -> Unit = {},
    onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    onResetChapterSettingsToDefault: () -> Unit = {},
    onDuplicateOpenManga: (Long) -> Unit = {},
    onDuplicateMigrate: (Long) -> Unit = {},
    onDuplicateAddAnyway: () -> Unit = {},
    onDuplicateDismiss: () -> Unit = {},
    onOpenChapterInWebView: ((Long) -> Unit)? = null,
    onOpenRandomManga: () -> Unit = {},
    sourceNameFor: (Long) -> String = { "Source #$it" },
    sourceBaseUrlFor: (Long) -> String? = { null },
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: java.nio.file.Path? = null,
    diskCacheDir: java.nio.file.Path? = null,
    appLockController: DesktopAppLockController? = null,
    onLockNow: (() -> Unit)? = null,
) {
    val state = DesktopShellState(
        selected = selected,
        libraryState = libraryState,
        libraryBatchState = libraryBatchState,
        mangaDetailState = mangaDetailState,
        standaloneMangaDetails = standaloneMangaDetails,
        isMangaLibraryActionRunning = isMangaLibraryActionRunning,
        isMangaSourceRefreshing = isMangaSourceRefreshing,
        downloadsQueue = downloadsQueue,
        isDownloaderRunning = isDownloaderRunning,
        downloadSpeedBytesPerSec = downloadSpeedBytesPerSec,
        downloadRecoveryMessage = downloadRecoveryMessage,
        downloadStorageError = downloadStorageError,
        updatedChapters = updatedChapters,
        updatesUnreadCount = updatesUnreadCount,
        isUpdatingLibrary = isUpdatingLibrary,
        isRepairingCovers = isRepairingCovers,
        lastUpdateResult = lastUpdateResult,
        updateRunState = updateRunState,
        updateProgress = updateProgress,
        isUpcomingOpen = isUpcomingOpen,
        historyGroups = historyGroups,
        historyQuery = historyQuery,
        statsData = statsData,
        incognitoMode = incognitoMode,
        preferenceStore = preferenceStore,
        readerSettingsStore = readerSettingsStore,
        diagnosticService = diagnosticService,
        trackerManager = trackerManager,
        trackSyncService = trackSyncService,
        backupScheduler = backupScheduler,
        syncScheduler = syncScheduler,
        syncServerManager = syncServerManager,
        backgroundScheduler = backgroundScheduler,
        updateScheduler = updateScheduler,
        cookieStore = cookieStore,
        downloadCacheCleaner = downloadCacheCleaner,
        downloadsDir = downloadsDir,
        diskCacheDir = diskCacheDir,
        appLockController = appLockController,
        sourceNameFor = sourceNameFor,
        sourceBaseUrlFor = sourceBaseUrlFor,
    )
    val actions = remember(
        onDestinationSelected,
        appUpdateContent,
        upcomingContent,
        browseContent,
        onLibraryQueryChange,
        onMangaSelected,
        onBackFromMangaDetail,
        onReadChapter,
        onMangaDetailRetry,
        onImportBackup,
        onImportLocal,
        onLibraryRetry,
        onCategorySelected,
        onManageCategories,
        onEditMangaCategories,
        onOpenTracking,
        onDisplayModeChange,
        onGridSizeChange,
        onUnreadBadgeChange,
        onDownloadedBadgeChange,
        onOpenFilterDialog,
        onCloseFilterDialog,
        onFilterChange,
        onSortChange,
        onToggleSelectionMode,
        onToggleMangaSelection,
        onSelectAll,
        onDeselectAll,
        onBatchChangeCategories,
        onBatchSetCategories,
        onBatchCloseCategoryDialog,
        onBatchMarkRead,
        onBatchDownload,
        onBatchRemoveFromLibrary,
        onToggleMangaLibrary,
        onRefreshMangaSource,
        onEditInfo,
        onDismissEditInfo,
        onSaveMangaInfo,
        onResetMangaInfo,
        onChapterFilterChange,
        onChapterSortChange,
        onToggleBookmark,
        onToggleRead,
        onMarkPreviousRead,
        onDownloadChapter,
        onDeleteDownload,
        onDownloadBatch,
        onBatchBookmarkChapters,
        onBatchMarkChaptersRead,
        onBatchDownloadChapters,
        onBatchDeleteDownloads,
        onOpenChapterSettings,
        onDismissChapterSettings,
        onChapterDisplayModeChange,
        onExcludedScanlatorsChange,
        onShowMissingChaptersChange,
        onSetChapterSettingsAsDefault,
        onResetChapterSettingsToDefault,
        onDuplicateOpenManga,
        onDuplicateMigrate,
        onDuplicateAddAnyway,
        onDuplicateDismiss,
        onOpenChapterInWebView,
        onOpenRandomManga,
        mangaDetailActions,
        onPauseAllDownloads,
        onResumeAllDownloads,
        onClearCompletedDownloads,
        onCancelDownload,
        onRetryDownload,
        onRetryAllFailedDownloads,
        onReadDownloadedChapter,
        onRepairBrokenCovers,
        onCheckForUpdates,
        onCancelLibraryUpdate,
        onMarkUpdatesChapterRead,
        onDownloadUpdatesChapter,
        onUpdateLibrary,
        onOpenUpcoming,
        onCloseUpcoming,
        onHistoryQueryChange,
        onDeleteHistoryItem,
        onClearAllHistory,
        onRefreshStats,
        onToggleIncognito,
        onExportBackup,
        onPreferencesChanged,
        onLockNow,
    ) {
        DesktopShellActions(
            onDestinationSelected = onDestinationSelected,
            appUpdateContent = appUpdateContent,
            upcomingContent = upcomingContent,
            browseContent = browseContent,
            onLibraryQueryChange = onLibraryQueryChange,
            onMangaSelected = onMangaSelected,
            onBackFromMangaDetail = onBackFromMangaDetail,
            onReadChapter = onReadChapter,
            onMangaDetailRetry = onMangaDetailRetry,
            onImportBackup = onImportBackup,
            onImportLocal = onImportLocal,
            onLibraryRetry = onLibraryRetry,
            onCategorySelected = onCategorySelected,
            onManageCategories = onManageCategories,
            onEditMangaCategories = onEditMangaCategories,
            onOpenTracking = onOpenTracking,
            onDisplayModeChange = onDisplayModeChange,
            onGridSizeChange = onGridSizeChange,
            onUnreadBadgeChange = onUnreadBadgeChange,
            onDownloadedBadgeChange = onDownloadedBadgeChange,
            onOpenFilterDialog = onOpenFilterDialog,
            onCloseFilterDialog = onCloseFilterDialog,
            onFilterChange = onFilterChange,
            onSortChange = onSortChange,
            onToggleSelectionMode = onToggleSelectionMode,
            onToggleMangaSelection = onToggleMangaSelection,
            onSelectAll = onSelectAll,
            onDeselectAll = onDeselectAll,
            onBatchChangeCategories = onBatchChangeCategories,
            onBatchSetCategories = onBatchSetCategories,
            onBatchCloseCategoryDialog = onBatchCloseCategoryDialog,
            onBatchMarkRead = onBatchMarkRead,
            onBatchDownload = onBatchDownload,
            onBatchRemoveFromLibrary = onBatchRemoveFromLibrary,
            onToggleMangaLibrary = onToggleMangaLibrary,
            onRefreshMangaSource = onRefreshMangaSource,
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
            onDuplicateOpenManga = onDuplicateOpenManga,
            onDuplicateMigrate = onDuplicateMigrate,
            onDuplicateAddAnyway = onDuplicateAddAnyway,
            onDuplicateDismiss = onDuplicateDismiss,
            onOpenChapterInWebView = onOpenChapterInWebView,
            onOpenRandomManga = onOpenRandomManga,
            mangaDetailActions = mangaDetailActions,
            onPauseAllDownloads = onPauseAllDownloads,
            onResumeAllDownloads = onResumeAllDownloads,
            onClearCompletedDownloads = onClearCompletedDownloads,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onMoveUpDownload = onMoveUpDownload,
            onMoveDownDownload = onMoveDownDownload,
            onRetryDownload = onRetryDownload,
            onRetryAllFailedDownloads = onRetryAllFailedDownloads,
            onReadDownloadedChapter = onReadDownloadedChapter,
            onRepairBrokenCovers = onRepairBrokenCovers,
            onCheckForUpdates = onCheckForUpdates,
            onCancelLibraryUpdate = onCancelLibraryUpdate,
            onMarkUpdatesChapterRead = onMarkUpdatesChapterRead,
            onDownloadUpdatesChapter = onDownloadUpdatesChapter,
            onUpdateLibrary = onUpdateLibrary,
            onOpenUpcoming = onOpenUpcoming,
            onCloseUpcoming = onCloseUpcoming,
            onHistoryQueryChange = onHistoryQueryChange,
            onDeleteHistoryItem = onDeleteHistoryItem,
            onClearAllHistory = onClearAllHistory,
            onRefreshStats = onRefreshStats,
            onToggleIncognito = onToggleIncognito,
            onExportBackup = onExportBackup,
            onOpenOnboarding = onOpenOnboarding,
            onPreferencesChanged = onPreferencesChanged,
            onLockNow = onLockNow,
        )
    }
    DesktopShell(state = state, actions = actions)
}

@Composable
private fun DestinationItem(
    destination: DesktopDestination,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    alwaysShowLabel: Boolean = false,
    badgeCount: Int = 0,
) {
    val strings = LocalStrings.current
    DesktopTooltipBox(text = strings.destinationLabel(destination)) {
        NavigationRailItem(
            selected = destination == selected,
            onClick = { onDestinationSelected(destination) },
            icon = {
                if (badgeCount > 0) {
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            androidx.compose.material3.Badge(
                                modifier = Modifier.testTag("updates-unread-badge"),
                            ) {
                                Text(
                                    badgeCount.toString(),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    ) {
                        Icon(
                            imageVector = destinationIcon(destination),
                            contentDescription = strings.destinationLabel(destination),
                        )
                    }
                } else {
                    Icon(
                        imageVector = destinationIcon(destination),
                        contentDescription = strings.destinationLabel(destination),
                    )
                }
            },
            label = {
                Text(
                    text = strings.destinationLabel(destination),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            },
            alwaysShowLabel = alwaysShowLabel,
            colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
