package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.repository.LibraryDatabaseCleaner
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopRoute
import mihon.desktop.reader.DesktopReaderSettingsStore

@Composable
internal fun shellState(ctx: AppShellContext, destination: DesktopRoute, live: ShellLiveState): DesktopShellState {
    val runtime = ctx.runtime
    val strings = LocalStrings.current
    return DesktopShellState(
        selected = shellSection(destination, ctx.navigator.currentDestination),
        railSection = ctx.navigator.currentDestination,
        standaloneMangaDetails = destination is DesktopDestination.MangaDetails,
        libraryState = live.libraryState,
        libraryBatchState = live.libraryBatchState,
        mangaDetailState = live.mangaDetailState,
        isMangaLibraryActionRunning = ctx.session.isMangaLibraryActionRunning,
        isMangaSourceRefreshing = ctx.session.isMangaSourceRefreshing,
        downloadsQueue = live.downloadsQueue,
        downloadsBadge = live.downloadsBadge,
        downloadProgressOf = live.downloadProgressOf,
        isDownloaderRunning = live.isDownloaderRunning,
        downloadSpeed = live.downloadSpeed,
        downloadRecoveryMessage = live.downloadRecovery?.message,
        downloadStorageError = live.downloadStorageError,
        updatedChapters = live.updatesFeed.updatedChapters,
        homeToday = live.updatesFeed.homeToday,
        libraryDetailPaneWidth = ctx.session.preferences.libraryDetailPaneWidth,
        updatesUnreadCount = live.updatesFeed.unreadCount,
        isUpdatingLibrary = live.isUpdatingLibrary,
        isRepairingCovers = live.isRepairingCovers,
        lastUpdateResult = live.updatesFeed.lastUpdateResult,
        updateRunState = live.updateRunState,
        updateProgress = live.updateProgress,
        isUpcomingOpen = destination == DesktopDestination.Upcoming,
        historyGroups = live.historyState.groups,
        historyQuery = live.historyState.query,
        statsData = live.statsData,
        incognitoMode = ctx.session.preferences.incognitoMode,
        preferenceStore = runtime.preferences,
        contentDensity = ctx.layoutPrefs.contentDensity,
        railCollapsed = ctx.layoutPrefs.railCollapsed,
        railToggleRequests = ctx.layoutPrefs.railToggleRequests,
        readerSettingsStore = remember(runtime.preferences) { DesktopReaderSettingsStore(runtime.preferences) },
        diagnosticService = runtime.diagnosticService,
        trackerManager = runtime.trackerManager,
        trackSyncService = runtime.trackSyncService,
        backupScheduler = runtime.backupScheduler,
        syncScheduler = runtime.syncScheduler,
        backgroundScheduler = runtime.backgroundScheduler,
        updateScheduler = runtime.libraryUpdateScheduler,
        cookieStore = runtime.cookieStore,
        downloadCacheCleaner = runtime.downloadCacheCleaner,
        downloadsDir = runtime.downloader?.diskProvider?.downloadsDir
            ?: runtime.directories.root.resolve("media").resolve("downloads"),
        diskCacheDir = runtime.directories.cache,
        profileRoot = runtime.directories.root,
        databaseCleaner = runtime.library as? LibraryDatabaseCleaner,
        appLockController = ctx.appLockController,
        sourceNameFor = live.sourceNameFor,
        sourceBaseUrlFor = live.sourceBaseUrlFor,
        settingsInitialSection = ctx.session.settingsSectionRequest,
    )
}

/**
 * Manga details opened from the command palette while another section is showing still have to render
 * the library detail pane; the route itself is not a shell section.
 */
private fun shellSection(destination: DesktopRoute, current: DesktopDestination): DesktopDestination =
    if (destination is DesktopDestination.MangaDetails) DesktopDestination.Library else current
