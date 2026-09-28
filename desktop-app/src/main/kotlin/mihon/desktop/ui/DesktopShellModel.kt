package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import mihon.desktop.backup.DesktopBackupScheduler
import mihon.desktop.diagnostics.DiagnosticBundleService
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadCacheCleaner
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.history.DesktopHistoryGroup
import mihon.desktop.library.update.LibraryUpdateProgress
import mihon.desktop.library.update.LibraryUpdateRunState
import mihon.desktop.library.update.LibraryUpdateScheduler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.platform.WindowsBackgroundScheduler
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.stats.DesktopStatsData
import mihon.desktop.sync.DesktopSyncScheduler
import mihon.desktop.sync.DesktopSyncServerManager
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.TrackOnReadSyncService
import mihon.desktop.ui.library.ChapterDisplayMode
import mihon.desktop.ui.library.ChapterFilterState
import mihon.desktop.ui.library.ChapterSortState
import mihon.desktop.ui.library.LibraryBatchState
import mihon.desktop.ui.library.LibraryDisplayMode
import mihon.desktop.ui.library.LibraryFilterState
import mihon.desktop.ui.library.LibrarySortState
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.updates.LibraryUpdateResult
import mihon.desktop.updates.UpdatedChapterItem
import java.nio.file.Path

@Immutable
data class DesktopShellState(
    val selected: DesktopDestination = DesktopDestination.Library,
    val libraryState: LibraryUiState = LibraryUiState(),
    val libraryBatchState: LibraryBatchState = LibraryBatchState(),
    val mangaDetailState: MangaDetailUiState = MangaDetailUiState(),
    val standaloneMangaDetails: Boolean = false,
    val isMangaLibraryActionRunning: Boolean = false,
    val isMangaSourceRefreshing: Boolean = false,
    val downloadsQueue: List<DesktopDownload> = emptyList(),
    val isDownloaderRunning: Boolean = false,
    val downloadSpeedBytesPerSec: Double = 0.0,
    val downloadRecoveryMessage: String? = null,
    val downloadStorageError: String? = null,
    val updatedChapters: List<UpdatedChapterItem> = emptyList(),
    val updatesUnreadCount: Int = 0,
    val isUpdatingLibrary: Boolean = false,
    val isRepairingCovers: Boolean = false,
    val lastUpdateResult: LibraryUpdateResult? = null,
    val updateRunState: LibraryUpdateRunState? = null,
    val updateProgress: LibraryUpdateProgress? = null,
    val isUpcomingOpen: Boolean = false,
    val historyGroups: List<DesktopHistoryGroup> = emptyList(),
    val historyQuery: String = "",
    val statsData: DesktopStatsData = DesktopStatsData(),
    val incognitoMode: Boolean = false,
    val preferenceStore: DesktopPreferenceStore? = null,
    val readerSettingsStore: DesktopReaderSettingsStore? = null,
    val diagnosticService: DiagnosticBundleService? = null,
    val trackerManager: DesktopTrackerManager? = null,
    val trackSyncService: TrackOnReadSyncService? = null,
    val backupScheduler: DesktopBackupScheduler? = null,
    val syncScheduler: DesktopSyncScheduler? = null,
    val syncServerManager: DesktopSyncServerManager? = null,
    val backgroundScheduler: WindowsBackgroundScheduler? = null,
    val updateScheduler: LibraryUpdateScheduler? = null,
    val cookieStore: DesktopCookieStore? = null,
    val downloadCacheCleaner: DownloadCacheCleaner? = null,
    val downloadsDir: Path? = null,
    val diskCacheDir: Path? = null,
    val appLockController: DesktopAppLockController? = null,
    val sourceNameFor: (Long) -> String = { "Source #$it" },
    val sourceBaseUrlFor: (Long) -> String? = { null },
)

@Immutable
data class DesktopShellActions(
    val onDestinationSelected: (DesktopDestination) -> Unit = {},
    val appUpdateContent: (@Composable () -> Unit)? = null,
    val upcomingContent: (@Composable () -> Unit)? = null,
    val browseContent: (@Composable () -> Unit)? = null,
    val onLibraryQueryChange: (String) -> Unit = {},
    val onMangaSelected: (Long) -> Unit = {},
    val onBackFromMangaDetail: () -> Unit = {},
    val onReadChapter: (Long) -> Unit = {},
    val onMangaDetailRetry: () -> Unit = {},
    val onImportBackup: () -> Unit = {},
    val onImportLocal: () -> Unit = {},
    val onLibraryRetry: () -> Unit = {},
    val onCategorySelected: (Long) -> Unit = {},
    val onManageCategories: () -> Unit = {},
    val onEditMangaCategories: () -> Unit = {},
    val onOpenTracking: () -> Unit = {},
    val onDisplayModeChange: (LibraryDisplayMode) -> Unit = {},
    val onGridSizeChange: (Float) -> Unit = {},
    val onUnreadBadgeChange: (Boolean) -> Unit = {},
    val onDownloadedBadgeChange: (Boolean) -> Unit = {},
    val onOpenFilterDialog: () -> Unit = {},
    val onCloseFilterDialog: () -> Unit = {},
    val onFilterChange: (LibraryFilterState) -> Unit = {},
    val onSortChange: (LibrarySortState) -> Unit = {},
    val onToggleSelectionMode: (Boolean) -> Unit = {},
    val onToggleMangaSelection: (Long) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onDeselectAll: () -> Unit = {},
    val onBatchChangeCategories: () -> Unit = {},
    val onBatchSetCategories: (List<Long>) -> Unit = {},
    val onBatchCloseCategoryDialog: () -> Unit = {},
    val onBatchMarkRead: (Boolean) -> Unit = {},
    val onBatchDownload: (Int) -> Unit = {},
    val onBatchRemoveFromLibrary: () -> Unit = {},
    val onToggleMangaLibrary: (() -> Unit)? = null,
    val onRefreshMangaSource: (() -> Unit)? = null,
    val onEditInfo: () -> Unit = {},
    val onDismissEditInfo: () -> Unit = {},
    val onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    val onResetMangaInfo: () -> Unit = {},
    val onChapterFilterChange: (ChapterFilterState) -> Unit = {},
    val onChapterSortChange: (ChapterSortState) -> Unit = {},
    val onToggleBookmark: (Long) -> Unit = {},
    val onToggleRead: (Long) -> Unit = {},
    val onMarkPreviousRead: (Long) -> Unit = {},
    val onDownloadChapter: (Long) -> Unit = {},
    val onDeleteDownload: (Long) -> Unit = {},
    val onDownloadBatch: (Int?) -> Unit = {},
    val onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleBookmark) },
    val onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleRead) },
    val onBatchDownloadChapters: (Set<Long>) -> Unit = { ids -> ids.forEach(onDownloadChapter) },
    val onBatchDeleteDownloads: (Set<Long>) -> Unit = { ids -> ids.forEach(onDeleteDownload) },
    val onOpenChapterSettings: () -> Unit = {},
    val onDismissChapterSettings: () -> Unit = {},
    val onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = {},
    val onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    val onShowMissingChaptersChange: (Boolean) -> Unit = {},
    val onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    val onResetChapterSettingsToDefault: () -> Unit = {},
    val onDuplicateOpenManga: (Long) -> Unit = {},
    val onDuplicateMigrate: (Long) -> Unit = {},
    val onDuplicateAddAnyway: () -> Unit = {},
    val onDuplicateDismiss: () -> Unit = {},
    val onOpenChapterInWebView: ((Long) -> Unit)? = null,
    val onOpenRandomManga: () -> Unit = {},
    val mangaDetailActions: MangaDetailActions? = null,
    val onPauseAllDownloads: () -> Unit = {},
    val onResumeAllDownloads: () -> Unit = {},
    val onClearCompletedDownloads: () -> Unit = {},
    val onCancelDownload: (Long) -> Unit = {},
    val onPauseDownload: (Long) -> Unit = {},
    val onResumeDownload: (Long) -> Unit = {},
    val onMoveUpDownload: (Long) -> Unit = {},
    val onMoveDownDownload: (Long) -> Unit = {},
    val onRetryDownload: (Long) -> Unit = {},
    val onRetryAllFailedDownloads: () -> Unit = {},
    val onReadDownloadedChapter: (Long, Long) -> Unit = { _, chapterId -> onReadChapter(chapterId) },
    val onRepairBrokenCovers: (() -> Unit)? = null,
    val onCheckForUpdates: () -> Unit = {},
    val onCancelLibraryUpdate: () -> Unit = {},
    val onMarkUpdatesChapterRead: (Long, Long) -> Unit = { _, _ -> },
    val onDownloadUpdatesChapter: (Long, Long) -> Unit = { _, _ -> },
    val onUpdateLibrary: () -> Unit = {},
    val onOpenUpcoming: () -> Unit = {},
    val onCloseUpcoming: () -> Unit = {},
    val onHistoryQueryChange: (String) -> Unit = {},
    val onDeleteHistoryItem: (Long) -> Unit = {},
    val onClearAllHistory: () -> Unit = {},
    val onRefreshStats: () -> Unit = {},
    val onToggleIncognito: () -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onOpenOnboarding: () -> Unit = {},
    val onPreferencesChanged: ((DesktopPreferences) -> Unit)? = null,
    val onLockNow: (() -> Unit)? = null,
)
