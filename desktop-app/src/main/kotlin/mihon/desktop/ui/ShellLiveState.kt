package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.desktop.DesktopRuntime
import mihon.desktop.activity.MissingSourceAlerts
import mihon.desktop.category.DesktopCategory
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadRecoveryReport
import mihon.desktop.extension.getSources
import mihon.desktop.history.HistoryUiState
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.update.LibraryUpdateProgress
import mihon.desktop.library.update.LibraryUpdateRunState
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.stats.DesktopStatsData
import mihon.desktop.ui.browse.BrowseRequests
import mihon.desktop.ui.browse.SourceWithMangaCount
import mihon.desktop.ui.library.LibraryBatchState
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.tasks.DownloadProgressInfo
import mihon.desktop.ui.tasks.DownloadsNavBadge

/** Snapshot of the shell's observed flows for one composition of [MainShellRoute]. */
internal data class ShellLiveState(
    val libraryState: LibraryUiState,
    val libraryBatchState: LibraryBatchState,
    val mangaDetailState: MangaDetailUiState,
    val isRepairingCovers: Boolean,
    val sourceNames: Map<Long, String>,
    val sourceBaseUrls: Map<Long, String?>,
    val sourceNameFor: (Long) -> String,
    val sourceBaseUrlFor: (Long) -> String?,
    val downloadsQueue: () -> List<DesktopDownload>,
    val downloadsBadge: DownloadsNavBadge?,
    val isDownloaderRunning: Boolean,
    val downloadSpeed: () -> Double,
    val downloadStorageError: String?,
    val downloadRecovery: DownloadRecoveryReport?,
    val updateRunState: LibraryUpdateRunState,
    val updateProgress: LibraryUpdateProgress?,
    val isUpdatingLibrary: Boolean,
    val updatesFeed: UpdatesFeed,
    val historyState: HistoryUiState,
    val statsData: DesktopStatsData,
    val categories: List<DesktopCategory>,
    val downloadProgressOf: (Long) -> DownloadProgressInfo = { DownloadProgressInfo(0f, 0, 0) },
)

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>?.collectOr(initial: T): T =
    (this ?: remember { MutableStateFlow(initial) }).collectAsState().value

@Composable
internal fun rememberShellLiveState(app: AppSession, runtime: DesktopRuntime): ShellLiveState {
    val libraryState by app.library.state.collectAsState()
    val libraryBatchState by app.library.batchState.collectAsState()
    val repositoryDetail by app.library.detailState.collectAsState()
    val downloader = runtime.downloader
    val downloadLive = app.downloadLive.liveState()
    val running = downloadLive.isRunning.value
    val detail = remember(repositoryDetail, running, downloadLive.chapterProgressOf) {
        repositoryDetail.copy(
            downloadProgressOf = downloadLive.chapterProgressOf,
            downloadsRunning = running,
        )
    }
    val sources = remember(runtime.sourceManager, libraryState.items) {
        runtime.sourceManager.getSources()
    }
    val names = remember(sources) { sources.associate { it.id to it.name } }
    val urls = remember(sources) { sources.associate { it.id to it.baseUrl } }
    val strings = LocalStrings.current
    val sourceNameFor: (Long) -> String = remember(names, strings) {
        { sourceId -> names[sourceId] ?: strings.text(UiText.SourceFallback, sourceId) }
    }
    val sourceBaseUrlFor: (Long) -> String? = remember(urls) { { sourceId -> urls[sourceId] } }
    val updates = runtime.libraryUpdateScheduler
    val lastReport = updates?.lastReport.collectOr(null)
    val feed = rememberUpdatesFeed(runtime, lastReport, libraryState.items, app.updates)
    val history = runtime.historyService?.state.collectOr(HistoryUiState())
    val stats = runtime.statsService?.stats.collectOr(DesktopStatsData())
    val categories = runtime.categoryService?.categories.collectOr(emptyList())
    return ShellLiveState(
        libraryState = libraryState,
        libraryBatchState = libraryBatchState,
        mangaDetailState = detail,
        isRepairingCovers = app.library.isRepairingCovers.collectAsState().value,
        sourceNames = names,
        sourceBaseUrls = urls,
        sourceNameFor = sourceNameFor,
        sourceBaseUrlFor = sourceBaseUrlFor,
        downloadsQueue = downloadLive.queue,
        downloadsBadge = downloadLive.badge.value,
        isDownloaderRunning = running,
        downloadSpeed = downloadLive.speedBytesPerSec,
        downloadStorageError = downloader?.storageError.collectOr(null),
        downloadRecovery = runtime.downloadStore?.recoveryReport.collectOr(null),
        updateRunState = updates?.runState.collectOr(LibraryUpdateRunState()),
        updateProgress = updates?.currentProgress.collectOr(null),
        isUpdatingLibrary = updates?.isUpdating.collectOr(false),
        updatesFeed = feed,
        historyState = history,
        statsData = stats,
        categories = categories,
        downloadProgressOf = downloadLive.progressOf,
    )
}

@Composable
internal fun ShellActivityEffects(app: AppSession, runtime: DesktopRuntime, window: MainWindowController) {
    val navigator = app.navigator
    val destination = navigator.current
    LaunchedEffect(Unit) {
        runtime.missingSourceAlerts.requests.collect { request ->
            when (request) {
                is MissingSourceAlerts.Request.Install -> app.browseRequests.installExtension(request.item)
                is MissingSourceAlerts.Request.Migrate -> app.browseRequests.openMigration(
                    SourceWithMangaCount(request.sourceId, request.sourceName, request.mangaCount),
                )
            }
            navigator.navigate(DesktopDestination.Browse)
        }
    }
    LaunchedEffect(runtime.libraryUpdateAlerts) {
        val alerts = runtime.libraryUpdateAlerts ?: return@LaunchedEffect
        alerts.clicks.collect { target ->
            target ?: return@collect
            window.showMainWindow()
            when (target) {
                is mihon.desktop.notification.LibraryUpdateTarget.Chapter ->
                    navigator.navigate(DesktopDestination.Reader(target.chapterId))
                mihon.desktop.notification.LibraryUpdateTarget.Updates ->
                    navigator.navigate(DesktopDestination.Updates)
            }
            alerts.clickHandled()
            alerts.markSeen()
        }
    }
    LaunchedEffect(runtime.libraryUpdateAlerts, destination) {
        if (destination == DesktopDestination.Updates) runtime.libraryUpdateAlerts?.markSeen()
        if (destination == DesktopDestination.Stats) runtime.statsService?.refresh()
        if (destination != DesktopDestination.Settings) app.state.settingsSectionRequest = null
    }
}
