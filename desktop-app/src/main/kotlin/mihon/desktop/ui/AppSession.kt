package mihon.desktop.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.MissingSourceResolver
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.extension.findSourceDescriptor
import mihon.desktop.extension.getInstalledSourceIds
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.platform.ForwardedFileKind
import mihon.desktop.platform.SingleInstanceRequest
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.ui.browse.BrowseRequests
import mihon.desktop.ui.library.BackupRestorePresenter
import mihon.desktop.ui.library.BackupRestoreState
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.splash.FirstRunSplash
import mihon.desktop.ui.splash.StartupHandoff
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.extension.source.model.SManga
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** The presenters and input helpers whose lifetime is the main window's. */
internal class AppSession(
    val state: AppSessionState,
    val scope: CoroutineScope,
    val lock: DesktopAppLockController,
    val library: LibraryPresenter,
    val backup: BackupRestorePresenter,
    val upcoming: UpcomingPresenter,
    val importController: LibraryImportController,
    val imports: LibraryImportActions,
    val snackbar: SnackbarHostState,
    val searchFocus: FocusRequester,
    val textInputFocused: AtomicBoolean,
    val navigator: mihon.desktop.navigation.DesktopNavigator,
    val updates: UpdatesRowOverrides,
    val browseRequests: BrowseRequests,
    val downloadLive: DownloadLivePresenter,
)

@Composable
internal fun rememberAppSession(
    runtime: DesktopRuntime,
    startupHandoff: StartupHandoff?,
): AppSession {
    val state = remember {
        AppSessionState(
            runtime.preferences.load(),
            startupHandoff != null || FirstRunSplash.shouldShow(runtime.preferences),
            startupHandoff,
        )
    }
    val scope = rememberCoroutineScope()
    val lock = remember(runtime.preferences) { DesktopAppLockController(runtime.preferences) }
    val importController = remember(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot) {
        LibraryImportController(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot)
    }
    val imports = remember(importController, state.preferences.language) {
        val strings = DesktopStrings.resolve(state.preferences.language)
        LibraryImportActions(
            chooseBackup = { mihon.desktop.ui.library.chooseAndroidBackup(strings.libraryImportBackup) },
            chooseLocal = { mihon.desktop.ui.library.chooseLocalMangaDirectory(strings.libraryImportLocal) },
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }
    val library = rememberLibraryPresenter(runtime, scope)
    val backup = remember(scope, importController) {
        BackupRestorePresenter(scope, importController::importBackup)
    }
    val upcoming = remember(runtime.library, scope) {
        UpcomingPresenter(runtime.library, scope, preferences = runtime.preferences)
    }
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val textInputFocused = remember { AtomicBoolean(false) }
    val navigator = remember {
        mihon.desktop.navigation.DesktopNavigator(state.preferences.lastDestination) { destination ->
            state.preferences = runtime.preferences.updatePreferences { it.copy(lastDestination = destination) }
        }
    }
    val updates = remember { UpdatesRowOverrides() }
    val browseRequests = remember { BrowseRequests() }
    val downloadLive = remember(runtime.downloader, scope) { DownloadLivePresenter(runtime.downloader, scope) }
    return remember(
        state, scope, lock, library, backup, upcoming, importController, imports,
        snackbar, searchFocus, textInputFocused, navigator, updates, browseRequests, downloadLive,
    ) {
        AppSession(
            state, scope, lock, library, backup, upcoming, importController, imports,
            snackbar, searchFocus, textInputFocused, navigator, updates, browseRequests, downloadLive,
        )
    }
}

@Composable
private fun rememberLibraryPresenter(runtime: DesktopRuntime, scope: CoroutineScope): LibraryPresenter =
    remember(runtime.library, runtime.sourceManager, runtime.extensionStoreService, scope) {
        LibraryPresenter(
            repository = runtime.library,
            scope = scope,
            preferences = runtime.preferences,
            downloader = runtime.downloader,
            sourceManager = runtime.sourceManager,
            extensionStoreService = runtime.extensionStoreService,
            mangaRefreshHandler = { mangaId -> scope.launch { refreshOnlineManga(runtime, mangaId) } },
            coverFailures = runtime.imageLoader.coverFailures,
        ).also { runtime.onShutdown(it::shutdown) }
    }

private suspend fun refreshOnlineManga(runtime: DesktopRuntime, mangaId: Long) {
    val manga = runtime.library.allMangaSnapshot().find { it.id == mangaId } ?: return
    if (manga.sourceId == 0L) return
    val descriptor = runtime.sourceManager.findSourceDescriptor(manga.sourceId) ?: return
    if (descriptor.isLocalSource()) return
    runtime.onlineMangaSyncService.prepareOnlineMangaForReading(
        sourceId = manga.sourceId,
        manga = SManga(url = manga.url, title = manga.title, thumbnailUrl = manga.thumbnailUrl),
        forceRefresh = true,
    )
}

@Composable
internal fun AppSessionEffects(
    app: AppSession,
    runtime: DesktopRuntime,
    windowController: MainWindowController,
) {
    val locked by app.lock.isLocked.collectAsState()
    val backupState by app.backup.state.collectAsState()
    LaunchedEffect(app.state.showFirstRunSplash) {
        if (!app.state.showFirstRunSplash) {
            mihon.desktop.StartupTrace.mark("interactive")
            runtime.startDeferredServices()
        }
    }
    LaunchedEffect(app.lock) { app.lock.onStartup() }
    LaunchedEffect(app.lock, locked) {
        while (!locked) {
            delay(1_000)
            app.lock.checkIdleTimeout()
        }
    }
    DisposableEffect(app.upcoming) { onDispose(app.upcoming::close) }
    DisposableEffect(app.backup) { onDispose(app.backup::close) }
    DisposableEffect(app.library) { onDispose(app.library::close) }
    LaunchedEffect(backupState) {
        val finished = backupState as? BackupRestoreState.Finished
        if (finished?.outcome is ImportActionState.Completed) {
            findMissingSourcesAfterRestore(app, runtime)
        }
    }
}

private suspend fun findMissingSourcesAfterRestore(app: AppSession, runtime: DesktopRuntime) {
    val allManga = runtime.library.allMangaSnapshot()
    val installed = runtime.sourceManager.getInstalledSourceIds()
    val available = MissingSourceResolver.ensureAvailableExtensions(runtime.extensionStoreService)
    if (available.isEmpty()) return
    val missing = MissingSourceResolver.findMissingSources(allManga, installed, available)
    if (missing.isNotEmpty()) app.state.pendingMissingSources = missing
}

internal fun AppSession.openExternalFile(runtime: DesktopRuntime, path: Path) {
    scope.launch {
        lock.isLocked.first { !it }
        val kind = if (java.nio.file.Files.isDirectory(path)) {
            ForwardedFileKind.LocalArchive
        } else {
            ForwardedFileKind.of(path)
        }
        when (kind) {
            ForwardedFileKind.AndroidBackup -> state.pendingImportPath = path
            ForwardedFileKind.LocalArchive -> if (state.importState != ImportActionState.Running) {
                state.importState = ImportActionState.Running
                state.importState = importController.importLocal(path)
            }
            ForwardedFileKind.Unsupported -> mihon.desktop.logging.DesktopLogger.warn(
                "SingleInstance",
                "Ignoring unsupported external file: ${path.fileName}",
            )
        }
    }
}

@Composable
internal fun ForwardedInstanceRequests(
    app: AppSession,
    runtime: DesktopRuntime,
    requests: kotlinx.coroutines.flow.Flow<SingleInstanceRequest>,
    windowController: MainWindowController,
) {
    LaunchedEffect(requests, windowController.composeWindow != null) {
        if (windowController.composeWindow == null) return@LaunchedEffect
        requests.collect { request ->
            windowController.showMainWindow(fromAnotherProcess = true)
            val path = (request as? SingleInstanceRequest.Open)?.path ?: return@collect
            app.openExternalFile(runtime, path)
        }
    }
}
