package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.extension.MissingSourceResolver
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.navigation.LocalDesktopNavigator
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.window.ReaderWindowController
import mihon.desktop.reader.window.ReaderWindowEscape
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.DesktopAppLockGate
import mihon.desktop.security.UnlockResult
import mihon.desktop.track.toDesktopTrackRecord
import mihon.desktop.track.toTrackingRecord
import mihon.desktop.ui.category.EditMangaCategoriesDialog
import mihon.desktop.ui.category.ManageCategoriesDialog
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalTextInputTracker
import mihon.desktop.ui.common.formatNetworkErrorMessage
import mihon.desktop.ui.library.BackupRestoreDialog
import mihon.desktop.ui.library.BackupRestorePresenter
import mihon.desktop.ui.library.BackupRestoreState
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.chapterDisplayLabel
import mihon.desktop.ui.library.withDownloadProgress
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.LibraryChapterBookmarkStore
import mihon.desktop.ui.reader.ReaderChapterTransitionChapter
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.shortcut.DesktopShortcutMatcher
import mihon.desktop.ui.shortcut.DesktopShortcutsDialog
import mihon.desktop.ui.shortcut.ShellShortcutAction
import mihon.desktop.ui.track.TrackingDialog
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import mihon.extension.source.model.SManga
import java.awt.Frame
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val appLockController = remember(runtime.preferences) {
        DesktopAppLockController(runtime.preferences)
    }
    val appLocked by appLockController.isLocked.collectAsState()
    LaunchedEffect(appLockController) {
        appLockController.onStartup()
    }
    LaunchedEffect(appLockController, appLocked) {
        while (!appLocked) {
            delay(1_000)
            appLockController.checkIdleTimeout()
        }
    }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val screen = remember { ScreenBounds(0, 0, screenSize.width, screenSize.height) }
    val savedPlacement = remember(preferences.windowPlacement) {
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false)).sanitize(screen)
    }
    val navigator = remember {
        DesktopNavigator(preferences.lastDestination) { destination ->
            preferences = runtime.preferences.updatePreferences { it.copy(lastDestination = destination) }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }
    val textInputFocused = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val textInputTracker = remember {
        mihon.desktop.ui.common.TextInputTracker { focused ->
            textInputFocused.set(focused)
        }
    }
    var showShortcutsHelp by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(
        placement = if (savedPlacement.maximized) {
            ComposeWindowPlacement.Maximized
        } else {
            ComposeWindowPlacement.Floating
        },
        position = WindowPosition(savedPlacement.x.dp, savedPlacement.y.dp),
        width = savedPlacement.width.dp,
        height = savedPlacement.height.dp,
    )
    var composeWindow: ComposeWindow? by remember { mutableStateOf(null) }
    var readerWindowMode by remember { mutableStateOf(ReaderWindowMode.NORMAL) }
    val readerWindowController = remember(savedPlacement) { ReaderWindowController(savedPlacement) }

    fun currentWindowPlacement(): WindowPlacement = composeWindow?.let { window ->
        WindowPlacement(
            x = window.x,
            y = window.y,
            width = window.width,
            height = window.height,
            maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
        )
    } ?: readerWindowController.normalBounds

    fun applyReaderWindowMode(mode: ReaderWindowMode) {
        readerWindowMode = mode
        when (mode) {
            ReaderWindowMode.FULLSCREEN -> windowState.placement = ComposeWindowPlacement.Fullscreen
            ReaderWindowMode.BORDERLESS -> windowState.placement = ComposeWindowPlacement.Floating
            ReaderWindowMode.NORMAL -> {
                val bounds = readerWindowController.normalBounds.sanitize(screen)
                windowState.placement = ComposeWindowPlacement.Floating
                windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
            }
        }
        val settingsStore = DesktopReaderSettingsStore(runtime.preferences)
        settingsStore.save(settingsStore.load().copy(lastWindowMode = mode))
    }

    fun transitionReaderWindow(mode: ReaderWindowMode) {
        readerWindowController.transition(mode, currentWindowPlacement())
        applyReaderWindowMode(mode)
    }

    fun handleReaderEscape(): Boolean = when (readerWindowController.onEscape(currentWindowPlacement())) {
        ReaderWindowEscape.ReturnedToNormal -> {
            applyReaderWindowMode(ReaderWindowMode.NORMAL)
            false
        }
        ReaderWindowEscape.CloseReader -> true
    }
    val presenterScope = rememberCoroutineScope()
    lateinit var closeApplicationRef: (Boolean) -> Unit
    val tray = remember {
        DesktopTray(
            stringsProvider = { mihon.desktop.i18n.DesktopStrings.resolve(preferences.language) },
            onShow = {
                composeWindow?.let { window ->
                    java.awt.EventQueue.invokeLater {
                        if (!window.isVisible) {
                            window.isVisible = true
                        }
                        if ((window.extendedState and Frame.ICONIFIED) != 0) {
                            window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                        }
                        window.toFront()
                        window.requestFocus()
                    }
                }
            },
            onQuit = {
                closeApplicationRef(true)
            },
        ).also { runtime.onShutdown(it::dispose) }
    }
    fun closeApplication(forceQuit: Boolean = false) {
        if (DesktopTray.shouldStayInBackground(
                enabled = preferences.runInBackgroundOnClose,
                traySupported = tray.isSupported,
                windowAvailable = composeWindow != null,
                forceQuit = forceQuit,
            )
        ) {
            composeWindow?.let {
                preferences = runtime.preferences.updatePreferences { current ->
                    current.copy(windowPlacement = currentWindowPlacement().sanitize(screen))
                }
            }
            tray.ensureInstalled()
            composeWindow?.isVisible = false
            val strings = mihon.desktop.i18n.DesktopStrings.resolve(preferences.language)
            tray.showNotificationOnce(strings.trayHiddenTitle, strings.trayHiddenHint)
            return
        }
        tray.dispose()
        composeWindow?.let {
            preferences = runtime.preferences.updatePreferences { current ->
                current.copy(windowPlacement = currentWindowPlacement().sanitize(screen))
            }
        }
        exitApplication()
    }
    closeApplicationRef = ::closeApplication
    val appUpdatePresenter = remember(runtime.appUpdateService) {
        mihon.desktop.updates.AppUpdatePresenter(
            runtime.appUpdateService,
            presenterScope,
            runtime.appUpdateInstaller,
            { closeApplication(forceQuit = true) },
        )
            .also { runtime.onShutdown(it::shutdown) }
    }
    val libraryPresenter = remember(runtime.library, runtime.sourceManager, runtime.extensionStoreService) {
        LibraryPresenter(
            repository = runtime.library,
            scope = presenterScope,
            preferences = runtime.preferences,
            downloader = runtime.downloader,
            sourceManager = runtime.sourceManager,
            extensionStoreService = runtime.extensionStoreService,
            mangaRefreshHandler = { mangaId ->
                val manga = runtime.library.allMangaSnapshot().find { it.id == mangaId }
                if (manga != null && manga.sourceId != 0L) {
                    val descriptor = runtime.sourceManager.findSourceDescriptor(manga.sourceId)
                    if (descriptor != null && !descriptor.isLocalSource()) {
                        runtime.onlineMangaSyncService.prepareOnlineMangaForReading(
                            sourceId = manga.sourceId,
                            manga = SManga(
                                url = manga.url,
                                title = manga.title,
                                thumbnailUrl = manga.thumbnailUrl,
                            ),
                            forceRefresh = true,
                        )
                    }
                }
            },
        ).also { runtime.onShutdown(it::shutdown) }
    }
    val libraryState by libraryPresenter.state.collectAsState()
    val libraryBatchState by libraryPresenter.batchState.collectAsState()
    val isRepairingCovers by libraryPresenter.isRepairingCovers.collectAsState()
    val repositoryMangaDetailState by libraryPresenter.detailState.collectAsState()
    val sourceNames = remember(runtime.sourceManager, libraryState.items) {
        runCatching {
            runtime.sourceManager.getSources().associate { it.id to it.name }
        }.getOrDefault(emptyMap())
    }
    val sourceBaseUrls = remember(runtime.sourceManager, libraryState.items) {
        runCatching {
            runtime.sourceManager.getSources().associate { it.id to it.baseUrl }
        }.getOrDefault(emptyMap())
    }
    val upcomingPresenter = remember(runtime.library) {
        UpcomingPresenter(
            repository = runtime.library,
            scope = presenterScope,
            preferences = runtime.preferences,
        )
    }
    val upcomingState by upcomingPresenter.state.collectAsState()
    DisposableEffect(upcomingPresenter) {
        onDispose(upcomingPresenter::close)
    }
    val categoryService = runtime.categoryService
    val categories by (
        categoryService?.categories ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList<DesktopCategory>())
        }
        ).collectAsState()
    val importController = remember(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot) {
        LibraryImportController(runtime.backupImporter, runtime.localImporter, runtime.localLibraryRoot)
    }
    val backupRestore = remember(importController, presenterScope) {
        BackupRestorePresenter(presenterScope, importController::importBackup)
    }
    val backupRestoreState by backupRestore.state.collectAsState()
    DisposableEffect(backupRestore) { onDispose(backupRestore::close) }
    val importActions = remember(importController, preferences.language) {
        val importStrings = mihon.desktop.i18n.DesktopStrings.resolve(preferences.language)
        LibraryImportActions(
            chooseBackup = { mihon.desktop.ui.library.chooseAndroidBackup(importStrings.libraryImportBackup) },
            chooseLocal = { mihon.desktop.ui.library.chooseLocalMangaDirectory(importStrings.libraryImportLocal) },
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }
    var importState: ImportActionState by remember { mutableStateOf(ImportActionState.Idle) }
    var isTrackingDialogOpen by remember { mutableStateOf(false) }
    var isManageCategoriesDialogOpen by remember { mutableStateOf(false) }
    var isEditMangaCategoriesDialogOpen by remember { mutableStateOf(false) }
    var isMangaLibraryActionRunning by remember { mutableStateOf(false) }
    var isMangaSourceRefreshing by remember { mutableStateOf(false) }
    var pendingMangaOrganizationAction by remember { mutableStateOf<MangaOrganizationAction?>(null) }
    var pendingMissingSources by remember { mutableStateOf<List<MissingSourceInfo>?>(null) }
    var isInstallingMissingBatch by remember { mutableStateOf(false) }

    LaunchedEffect(backupRestoreState) {
        val finished = backupRestoreState as? BackupRestoreState.Finished
        if (finished?.outcome is ImportActionState.Completed) {
            val allManga = runtime.library.allMangaSnapshot()
            val installedIds = runtime.sourceManager.getInstalledSourceIds()
            val available = MissingSourceResolver.ensureAvailableExtensions(runtime.extensionStoreService)
            if (available.isNotEmpty()) {
                val missing = MissingSourceResolver.findMissingSources(allManga, installedIds, available)
                if (missing.isNotEmpty()) {
                    pendingMissingSources = missing
                }
            }
        }
    }
    DisposableEffect(libraryPresenter) {
        onDispose(libraryPresenter::close)
    }

    val downloader = runtime.downloader
    val downloadsQueue by (
        downloader?.queueState ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }
        ).collectAsState()
    val isDownloaderRunning by (
        downloader?.isRunning ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(false)
        }
        ).collectAsState()
    val mangaDetailState = remember(repositoryMangaDetailState, downloadsQueue, isDownloaderRunning) {
        repositoryMangaDetailState.withDownloadProgress(downloadsQueue, isDownloaderRunning)
    }
    val downloadSpeed by (
        downloader?.speedBytesPerSec ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(0.0)
        }
        ).collectAsState()

    val downloadStorageError by (
        downloader?.storageError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
        ).collectAsState()
    val downloadRecovery by (
        runtime.downloadStore?.recoveryReport
            ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow<mihon.desktop.download.DownloadRecoveryReport?>(null)
            }
        ).collectAsState()
    val updateRunState by (
        runtime.libraryUpdateScheduler?.runState
            ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow(mihon.desktop.library.update.LibraryUpdateRunState())
            }
        ).collectAsState()
    val updateProgress by (
        runtime.libraryUpdateScheduler?.currentProgress
            ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow<mihon.desktop.library.update.LibraryUpdateProgress?>(null)
            }
        ).collectAsState()

    val isUpdatingLibrary by (
        runtime.libraryUpdateScheduler?.isUpdating
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    val lastReport by (
        runtime.libraryUpdateScheduler?.lastReport
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }
        ).collectAsState()
    val lastUpdateResult = remember(lastReport) {
        lastReport?.let { rep ->
            mihon.desktop.updates.LibraryUpdateResult(
                totalMangaChecked = rep.totalMangaChecked,
                mangaWithNewChapters = rep.updatedMangaCount,
                newChaptersFound = rep.newChaptersTotal,
                updatedMangaTitles = rep.results.filter { it.newChapters.isNotEmpty() }.map { it.title },
                errors = rep.errors,
            )
        }
    }
    val allMangaList = libraryState.items
    val updatedChapters = remember(lastReport, allMangaList) {
        lastReport?.results?.flatMap { res ->
            val mangaCover = allMangaList.firstOrNull { it.id == res.mangaId }?.thumbnailUrl
            res.newChapters.map { ch ->
                mihon.desktop.updates.UpdatedChapterItem(
                    mangaId = res.mangaId,
                    chapterId = ch.id,
                    mangaTitle = res.title,
                    chapterName = ch.name,
                    chapterNumber = ch.chapterNumber,
                    dateFetch = ch.dateFetch,
                    mangaThumbnailUrl = mangaCover,
                )
            }
        } ?: emptyList()
    }

    val historyService = runtime.historyService
    val historyState by (
        historyService?.state ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(mihon.desktop.history.HistoryUiState())
        }
        ).collectAsState()
    val statsService = runtime.statsService
    val statsData by (
        statsService?.stats ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow(mihon.desktop.stats.DesktopStatsData())
        }
        ).collectAsState()
    LaunchedEffect(navigator.current) {
        if (navigator.current == DesktopDestination.Stats) {
            statsService?.refresh()
        }
    }

    val appIcon = remember {
        try {
            val stream = DesktopNavigator::class.java.getResourceAsStream("/icon.png")
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
            stream?.use {
                val bytes = it.readAllBytes()
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap(),
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    val currentLanguageStrings = remember(preferences.language) {
        mihon.desktop.i18n.DesktopStrings.resolve(preferences.language)
    }
    val destination = navigator.current
    val windowTitle = remember(destination, mangaDetailState, currentLanguageStrings) {
        val prefix = "mihondesk"
        when (destination) {
            is DesktopDestination.Reader -> {
                val mTitle = mangaDetailState.manga?.title
                val cTitle = mangaDetailState.allChapters
                    .firstOrNull { it.id == destination.chapterId }
                    ?.let { chapter ->
                        chapterDisplayLabel(
                            chapter,
                            mangaDetailState.chapterSettings.displayMode,
                            currentLanguageStrings,
                        )
                    } ?: currentLanguageStrings.text(UiText.ChapterNumberLabel, destination.chapterId)
                if (mTitle != null) {
                    "$prefix — $mTitle — $cTitle"
                } else {
                    "$prefix — $cTitle"
                }
            }
            is DesktopDestination.MangaDetails -> {
                val mTitle = mangaDetailState.manga?.title
                if (mTitle != null) {
                    "$prefix — $mTitle"
                } else {
                    prefix
                }
            }
            DesktopDestination.Upcoming -> "$prefix — ${currentLanguageStrings.text(UiText.Upcoming)}"
            is DesktopDestination -> "$prefix — ${currentLanguageStrings.destinationLabel(destination)}"
        }
    }

    key(readerWindowMode == ReaderWindowMode.BORDERLESS) {
        Window(
            onCloseRequest = { closeApplication() },
            state = windowState,
            title = windowTitle,
            icon = appIcon,
            undecorated = readerWindowMode == ReaderWindowMode.BORDERLESS,
            onPreviewKeyEvent = { event ->
                appLockController.recordActivity()
                val action = DesktopShortcutMatcher.matchAction(event, textInputFocused.get())
                if (action != null) {
                    when (action) {
                        ShellShortcutAction.NavigateDestination1 -> navigator.navigate(DesktopDestination.Library)
                        ShellShortcutAction.NavigateDestination2 -> navigator.navigate(DesktopDestination.Updates)
                        ShellShortcutAction.NavigateDestination3 -> navigator.navigate(DesktopDestination.History)
                        ShellShortcutAction.NavigateDestination4 -> navigator.navigate(DesktopDestination.Browse)
                        ShellShortcutAction.NavigateDestination5 -> navigator.navigate(DesktopDestination.Downloads)
                        ShellShortcutAction.NavigateDestination6 -> navigator.navigate(DesktopDestination.Stats)
                        ShellShortcutAction.NavigateDestination7 -> navigator.navigate(DesktopDestination.Settings)
                        ShellShortcutAction.NavigateDestination8 -> navigator.navigate(DesktopDestination.About)
                        ShellShortcutAction.FocusSearch -> {
                            runCatching { searchFocusRequester.requestFocus() }
                        }
                        ShellShortcutAction.RefreshCurrentPage -> {
                            when (navigator.current) {
                                DesktopDestination.Library, DesktopDestination.Updates -> {
                                    presenterScope.launch {
                                        runtime.libraryUpdateScheduler?.triggerUpdateNow()
                                    }
                                }
                                DesktopDestination.Stats -> {
                                    presenterScope.launch { statsService?.refresh() }
                                }
                                DesktopDestination.Downloads -> {
                                    runtime.downloader?.resume()
                                }
                                is DesktopDestination.MangaDetails -> {
                                    libraryPresenter.retryDetail()
                                }
                                else -> {}
                            }
                        }
                        ShellShortcutAction.NavigateBack -> {
                            if (showShortcutsHelp) {
                                showShortcutsHelp = false
                            } else {
                                navigator.back()
                            }
                        }
                        ShellShortcutAction.ShowShortcutsHelp -> {
                            showShortcutsHelp = true
                        }
                    }
                    true
                } else {
                    false
                }
            },
        ) {
            SideEffect {
                composeWindow = window
                window.minimumSize = java.awt.Dimension(960, 640)
            }
            MihonDesktopTheme(
                themeMode = preferences.themeMode,
                appTheme = preferences.appTheme,
                isAmoled = preferences.themeDarkAmoled,
            ) {
                mihon.desktop.i18n.ProvideDesktopStrings(preferences.language) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        mihon.desktop.image.LocalImageLoader provides runtime.imageLoader,
                        mihon.desktop.image.LocalCustomCoverManager provides runtime.customCoverManager,
                        LocalDesktopNavigator provides navigator,
                        LocalSnackbarHostState provides snackbarHostState,
                        LocalSearchFocusRequester provides searchFocusRequester,
                        LocalTextInputTracker provides textInputTracker,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().pointerInput(appLockController) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        appLockController.recordActivity()
                                    }
                                }
                            },
                        ) {
                            DesktopAppLockGate(
                                isLocked = appLocked,
                                onUnlock = { pin ->
                                    appLockController.unlock(pin) == UnlockResult.Success
                                },
                                onForgotPinConfirmed = appLockController::disableLock,
                            ) {
                                val strings = mihon.desktop.i18n.LocalStrings.current
                                val destination = navigator.current
                                LaunchedEffect(destination) {
                                    if (destination is DesktopDestination.Reader &&
                                        readerWindowController.mode == ReaderWindowMode.NORMAL
                                    ) {
                                        val savedReaderMode =
                                            DesktopReaderSettingsStore(runtime.preferences).load().lastWindowMode
                                        if (savedReaderMode != ReaderWindowMode.NORMAL) {
                                            readerWindowController.restore(savedReaderMode, currentWindowPlacement())
                                            applyReaderWindowMode(savedReaderMode)
                                        }
                                    }
                                    if (destination != DesktopDestination.Library) {
                                        isManageCategoriesDialogOpen = false
                                        isEditMangaCategoriesDialogOpen = false
                                    }
                                }
                                if (destination is DesktopDestination.Reader) {
                                    val currentChapterId = destination.chapterId
                                    val navigationChapters = if (mangaDetailState.readerChapters.isNotEmpty()) {
                                        mangaDetailState.readerChapters
                                    } else {
                                        mangaDetailState.allChapters
                                    }
                                    val readableChapters = remember(
                                        navigationChapters,
                                        mangaDetailState.readerAvailability,
                                        currentChapterId,
                                    ) {
                                        val available = if (mangaDetailState.readerAvailability.isEmpty()) {
                                            navigationChapters
                                        } else {
                                            navigationChapters.filter {
                                                mangaDetailState.readerAvailability[it.id] ==
                                                    ChapterReaderAvailability.Readable
                                            }
                                        }
                                        if (mangaDetailState.readerChapters.isNotEmpty()) {
                                            available
                                        } else {
                                            available.sortedWith(
                                                compareBy<mihon.desktop.library.model.LibraryChapter> {
                                                    it.chapterNumber
                                                }.thenBy { it.sourceOrder },
                                            )
                                        }
                                    }
                                    val currentChapterIdx = readableChapters.indexOfFirst { it.id == currentChapterId }
                                    val prevChapter = if (currentChapterIdx >
                                        0
                                    ) {
                                        readableChapters[currentChapterIdx - 1]
                                    } else {
                                        null
                                    }
                                    val nextChapter = if (currentChapterIdx in 0 until readableChapters.size - 1) {
                                        readableChapters[currentChapterIdx + 1]
                                    } else {
                                        null
                                    }
                                    val sortedAllChapters = remember(navigationChapters) { navigationChapters }
                                    val transitionCatalog = remember(
                                        sortedAllChapters,
                                        mangaDetailState.readerAvailability,
                                        mangaDetailState.downloadedChapterIds,
                                        mangaDetailState.chapterSettings.displayMode,
                                        strings,
                                    ) {
                                        sortedAllChapters.map { chapter ->
                                            ReaderChapterTransitionChapter(
                                                id = chapter.id,
                                                title = chapterDisplayLabel(
                                                    chapter,
                                                    mangaDetailState.chapterSettings.displayMode,
                                                    strings,
                                                ),
                                                chapterNumber = chapter.chapterNumber,
                                                scanlator = chapter.scanlator,
                                                downloaded = mangaDetailState.downloadedChapterIds.contains(chapter.id),
                                                available = mangaDetailState.readerAvailability.isEmpty() ||
                                                    mangaDetailState.readerAvailability[chapter.id] ==
                                                    ChapterReaderAvailability.Readable,
                                                read = chapter.read,
                                            )
                                        }
                                    }
                                    val transitionCurrent = transitionCatalog.firstOrNull { it.id == currentChapterId }
                                    val transitionPrevious = prevChapter?.let { prev ->
                                        transitionCatalog.firstOrNull { it.id == prev.id }
                                    }
                                    val transitionNext = nextChapter?.let { next ->
                                        transitionCatalog.firstOrNull { it.id == next.id }
                                    }
                                    val readerBookmarkStore: ReaderChapterBookmarkStore? = remember(
                                        runtime.library,
                                        mangaDetailState.manga?.id,
                                    ) {
                                        mangaDetailState.manga?.id?.let { mangaId ->
                                            LibraryChapterBookmarkStore(
                                                findChapter = { chapterId ->
                                                    runtime.library.chapterSnapshot(mangaId)
                                                        .firstOrNull { it.id == chapterId }
                                                },
                                                mutationPort = runtime.library,
                                            )
                                        }
                                    }

                                    val readerManga = mangaDetailState.manga?.takeIf { manga ->
                                        mangaDetailState.allChapters.any {
                                            it.id == destination.chapterId && it.mangaId == manga.id
                                        }
                                    }
                                    fun leaveReader(afterClose: () -> Unit) {
                                        val chapter = mangaDetailState.allChapters.firstOrNull {
                                            it.id == destination.chapterId
                                        }
                                        val manga = readerManga
                                        if (chapter != null && manga != null) {
                                            presenterScope.launch {
                                                val prefs = runtime.preferences.load()
                                                if (!prefs.incognitoMode) {
                                                    runtime.trackSyncService?.onChapterRead(
                                                        manga.id,
                                                        chapter.chapterNumber,
                                                    )
                                                }
                                                if (prefs.downloadAhead > 0) {
                                                    runtime.downloader?.checkAndDownloadAhead(
                                                        manga = manga,
                                                        currentChapter = chapter,
                                                        allChapters = mangaDetailState.chapters,
                                                        count = prefs.downloadAhead,
                                                    )
                                                }
                                                if (prefs.deleteDownloadedRead && !prefs.incognitoMode) {
                                                    runtime.downloader?.deleteDownloadedChapter(manga, chapter)
                                                }
                                            }
                                        }
                                        transitionReaderWindow(ReaderWindowMode.NORMAL)
                                        afterClose()
                                    }

                                    DesktopBackHandler {
                                        if (handleReaderEscape()) {
                                            leaveReader { navigator.pop() }
                                        }
                                        true
                                    }

                                    ReaderDestination(
                                        destination = destination,
                                        runtime = runtime,
                                        mangaTitle = mangaDetailState.manga?.title ?: "Reader",
                                        chapterTitle = mangaDetailState.allChapters.firstOrNull {
                                            it.id == destination.chapterId
                                        }?.let { chapter ->
                                            chapterDisplayLabel(
                                                chapter,
                                                mangaDetailState.chapterSettings.displayMode,
                                                strings,
                                            )
                                        } ?: strings.text(UiText.ChapterNumberLabel, destination.chapterId),
                                        mangaId = readerManga?.id,
                                        chapterCatalog = transitionCatalog,
                                        previousChapter = transitionPrevious,
                                        nextChapter = transitionNext,
                                        currentChapter = transitionCurrent,
                                        currentChapterDownloaded =
                                        mangaDetailState.downloadedChapterIds.contains(currentChapterId),
                                        bookmarkStore = readerBookmarkStore,
                                        onBack = { leaveReader { navigator.back() } },
                                        onOpenMangaDetails = {
                                            readerManga?.let { manga ->
                                                leaveReader {
                                                    libraryPresenter.openMangaDetail(manga.id)
                                                    navigator.navigate(DesktopDestination.MangaDetails(manga.id))
                                                }
                                            }
                                        },
                                        onPreviousChapter = {
                                            if (prevChapter != null) {
                                                navigator.navigate(DesktopDestination.Reader(prevChapter.id))
                                            }
                                        },
                                        onNextChapter = {
                                            if (nextChapter != null) {
                                                navigator.navigate(DesktopDestination.Reader(nextChapter.id))
                                            }
                                        },
                                        onChapterSelected = { chapterId ->
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                        hasPreviousChapter = prevChapter != null,
                                        hasNextChapter = nextChapter != null,
                                        onFullscreen = {
                                            val mode = readerWindowController.toggleFullscreen(currentWindowPlacement())
                                            applyReaderWindowMode(mode)
                                        },
                                        onBorderless = {
                                            val mode = readerWindowController.toggleBorderless(currentWindowPlacement())
                                            applyReaderWindowMode(mode)
                                        },
                                        onEscape = ::handleReaderEscape,
                                    )
                                } else {
                                    val upcomingContent: (@Composable () -> Unit)? = if (destination ==
                                        DesktopDestination.Upcoming
                                    ) {
                                        {
                                            UpcomingScreen(
                                                state = upcomingState,
                                                onBack = { navigator.back() },
                                                onPreviousMonth = upcomingPresenter::previousMonth,
                                                onNextMonth = upcomingPresenter::nextMonth,
                                                onSelectDate = upcomingPresenter::selectDate,
                                                onOpenFilter = { upcomingPresenter.setFilterDialogOpen(true) },
                                                onDismissFilter = { upcomingPresenter.setFilterDialogOpen(false) },
                                                onCycleCategory = upcomingPresenter::cycleCategory,
                                                onClearFilters = upcomingPresenter::clearFilters,
                                                onOpenManga = { mangaId ->
                                                    navigator.navigate(DesktopDestination.Library)
                                                    libraryPresenter.selectManga(mangaId)
                                                },
                                                onRetry = upcomingPresenter::retry,
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                    val sharedMangaDetailActions = MangaDetailActions(
                                        onReadChapter = { chapterId ->
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                        onInstallMissingSource = { extItem ->
                                            presenterScope.launch {
                                                try {
                                                    runtime.extensionInstaller.downloadAndInstall(
                                                        downloadUrl = extItem.downloadUrl,
                                                        expectedSha256 = extItem.sha256,
                                                        repoUrl = extItem.repoUrl,
                                                        storeItem = extItem,
                                                    )
                                                    libraryPresenter.refreshDetails()
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(
                                                        formatNetworkErrorMessage(
                                                            e.message ?: "Failed to install extension",
                                                            strings,
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        onIgnoreMissingSource = {
                                            val manga = mangaDetailState.manga
                                            if (manga != null) {
                                                libraryPresenter.ignoreMissingSource(manga.id)
                                            }
                                        },
                                        onEditCategories = {
                                            if (mangaDetailState.manga?.favorite == true) {
                                                isEditMangaCategoriesDialogOpen = true
                                            } else {
                                                pendingMangaOrganizationAction = MangaOrganizationAction.Categories
                                            }
                                        },
                                        onOpenTracking = {
                                            if (mangaDetailState.manga?.favorite == true) {
                                                isTrackingDialogOpen = true
                                            } else {
                                                pendingMangaOrganizationAction = MangaOrganizationAction.Tracking
                                            }
                                        },
                                        onEditInfo = { libraryPresenter.setEditInfoDialogOpen(true) },
                                        onDismissEditInfo = { libraryPresenter.setEditInfoDialogOpen(false) },
                                        onSaveMangaInfo = libraryPresenter::updateSelectedMangaInfo,
                                        onResetMangaInfo = libraryPresenter::resetSelectedMangaInfo,
                                        onChapterFilterChange = libraryPresenter::setChapterFilter,
                                        onChapterSortChange = libraryPresenter::setChapterSort,
                                        onToggleBookmark = libraryPresenter::toggleChapterBookmark,
                                        onToggleRead = libraryPresenter::toggleChapterRead,
                                        onMarkPreviousRead = libraryPresenter::markPreviousChaptersRead,
                                        onDownloadChapter = libraryPresenter::downloadChapter,
                                        onDeleteDownload = { chapterId ->
                                            libraryPresenter.deleteChapterDownload(chapterId)
                                            presenterScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.text(UiText.DownloadDeleted),
                                                    actionLabel = strings.text(UiText.Undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    libraryPresenter.downloadChapter(chapterId)
                                                }
                                            }
                                        },
                                        onDownloadBatch = { amount ->
                                            if (amount == -1) {
                                                libraryPresenter.downloadNextChapters(null, unreadOnly = false)
                                            } else {
                                                libraryPresenter.downloadNextChapters(amount, unreadOnly = true)
                                            }
                                        },
                                        onBatchBookmarkChapters = libraryPresenter::batchBookmarkChapters,
                                        onBatchMarkChaptersRead = libraryPresenter::batchMarkChaptersRead,
                                        onBatchDownloadChapters = libraryPresenter::batchDownloadChapters,
                                        onBatchDeleteDownloads = { chapterIds ->
                                            libraryPresenter.batchDeleteChapterDownloads(chapterIds)
                                            presenterScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.text(UiText.DownloadDeleted),
                                                    actionLabel = strings.text(UiText.Undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    chapterIds.forEach { libraryPresenter.downloadChapter(it) }
                                                }
                                            }
                                        },
                                        onOpenChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(true)
                                        },
                                        onDismissChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(false)
                                        },
                                        onChapterDisplayModeChange = libraryPresenter::setChapterDisplayMode,
                                        onExcludedScanlatorsChange = libraryPresenter::setExcludedScanlators,
                                        onShowMissingChaptersChange = libraryPresenter::setShowMissingChapters,
                                        onSetChapterSettingsAsDefault = libraryPresenter::setChapterSettingsAsDefault,
                                        onResetChapterSettingsToDefault =
                                        libraryPresenter::resetChapterSettingsToDefault,
                                        onCoverLoadFailed = libraryPresenter::onCoverLoadFailed,
                                    )
                                    DesktopShell(
                                        appUpdateContent = {
                                            mihon.desktop.ui.settings.AppUpdatePanel(
                                                appUpdatePresenter,
                                                runtime.appUpdateService,
                                            )
                                        },
                                        selected = destination as? DesktopDestination ?: DesktopDestination.Library,
                                        standaloneMangaDetails = destination is DesktopDestination.MangaDetails,
                                        onDestinationSelected = { selectedDestination ->
                                            navigator.navigate(selectedDestination)
                                        },
                                        libraryState = libraryState,
                                        libraryBatchState = libraryBatchState,
                                        mangaDetailState = mangaDetailState,
                                        mangaDetailActions = sharedMangaDetailActions,
                                        onToggleMangaLibrary = {
                                            val manga = mangaDetailState.manga
                                            if (manga != null) {
                                                isMangaLibraryActionRunning = true
                                                val wasFavorite = manga.favorite
                                                if (wasFavorite) {
                                                    if (libraryPresenter.setDetailFavorite(false)) {
                                                        presenterScope.launch {
                                                            val result = snackbarHostState.showSnackbar(
                                                                message = strings.text(UiText.LibraryRemoved),
                                                                actionLabel = strings.text(UiText.Undo),
                                                                duration = SnackbarDuration.Short,
                                                            )
                                                            if (result == SnackbarResult.ActionPerformed) {
                                                                libraryPresenter.setDetailFavorite(true)
                                                            }
                                                        }
                                                    } else {
                                                        presenterScope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Unable to update library membership",
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    libraryPresenter.setDetailFavorite(true)
                                                }
                                                isMangaLibraryActionRunning = false
                                            }
                                        },
                                        onRefreshMangaSource = {
                                            val manga = mangaDetailState.manga
                                            if (manga != null && !isMangaSourceRefreshing) {
                                                presenterScope.launch {
                                                    isMangaSourceRefreshing = true
                                                    try {
                                                        val source = runtime.sourceManager.findSourceDescriptor(
                                                            manga.sourceId,
                                                        )
                                                        if (source == null || source.isLocalSource()) {
                                                            libraryPresenter.retryDetail()
                                                        } else {
                                                            runtime.onlineMangaSyncService.prepareOnlineMangaForReading(
                                                                sourceId = manga.sourceId,
                                                                manga = SManga(
                                                                    url = manga.url,
                                                                    title = manga.title,
                                                                    thumbnailUrl = manga.thumbnailUrl,
                                                                    initialized = false,
                                                                ),
                                                                forceRefresh = true,
                                                            )
                                                            libraryPresenter.retryDetail()
                                                        }
                                                    } catch (error: Exception) {
                                                        snackbarHostState.showSnackbar(
                                                            formatNetworkErrorMessage(
                                                                error.message ?: "Unable to refresh manga",
                                                                strings,
                                                            ),
                                                        )
                                                    } finally {
                                                        isMangaSourceRefreshing = false
                                                    }
                                                }
                                            }
                                        },
                                        isMangaLibraryActionRunning = isMangaLibraryActionRunning,
                                        isMangaSourceRefreshing = isMangaSourceRefreshing,
                                        onLibraryQueryChange = libraryPresenter::setQuery,
                                        onMangaSelected = libraryPresenter::selectManga,
                                        onBackFromMangaDetail = {
                                            libraryPresenter.selectManga(null)
                                            if (destination is DesktopDestination.MangaDetails) navigator.back()
                                        },
                                        onReadChapter = { chapterId ->
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                        onMangaDetailRetry = libraryPresenter::retryDetail,
                                        onImportBackup = {
                                            mihon.desktop.ui.library.chooseAndroidBackup(
                                                strings.libraryImportBackup,
                                            )?.let {
                                                backupRestore.start(it)
                                            }
                                        },
                                        onImportLocal = {
                                            presenterScope.launch {
                                                importState = ImportActionState.Running
                                                importState = importActions.chooseAndImportLocal()
                                            }
                                        },
                                        onLibraryRetry = libraryPresenter::retry,
                                        onCategorySelected = libraryPresenter::selectCategory,
                                        onManageCategories = { isManageCategoriesDialogOpen = true },
                                        onEditMangaCategories = { isEditMangaCategoriesDialogOpen = true },
                                        onDisplayModeChange = libraryPresenter::setDisplayMode,
                                        onGridSizeChange = libraryPresenter::setGridSize,
                                        onOpenFilterDialog = { libraryPresenter.setFilterDialogOpen(true) },
                                        onCloseFilterDialog = { libraryPresenter.setFilterDialogOpen(false) },
                                        onFilterChange = libraryPresenter::setFilterState,
                                        onSortChange = libraryPresenter::setSortState,
                                        onToggleSelectionMode = libraryPresenter::toggleSelectionMode,
                                        onToggleMangaSelection = libraryPresenter::toggleMangaSelection,
                                        onSelectAll = libraryPresenter::selectAll,
                                        onDeselectAll = libraryPresenter::clearSelection,
                                        onBatchChangeCategories = { libraryPresenter.setBatchCategoryDialogOpen(true) },
                                        onBatchSetCategories = libraryPresenter::batchSetCategories,
                                        onBatchCloseCategoryDialog = {
                                            libraryPresenter.setBatchCategoryDialogOpen(false)
                                        },
                                        onBatchMarkRead = libraryPresenter::batchMarkRead,
                                        onBatchDownload = libraryPresenter::batchDownload,
                                        onBatchRemoveFromLibrary = {
                                            val selectedIds = libraryState.selectionState.selectedMangaIds.toSet()
                                            libraryPresenter.batchRemoveFromLibrary { removedIds ->
                                                val toRestore = if (removedIds.isNotEmpty()) removedIds else selectedIds
                                                presenterScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = strings.text(UiText.LibraryRemoved),
                                                        actionLabel = strings.text(UiText.Undo),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        libraryPresenter.restoreLibraryManga(toRestore)
                                                    }
                                                }
                                            }
                                        },
                                        downloadsQueue = downloadsQueue,
                                        isDownloaderRunning = isDownloaderRunning,
                                        downloadSpeedBytesPerSec = downloadSpeed,
                                        downloadRecoveryMessage = downloadRecovery?.message,
                                        downloadStorageError = downloadStorageError,
                                        onPauseAllDownloads = { downloader?.pause() },
                                        onResumeAllDownloads = { downloader?.resume() },
                                        onClearCompletedDownloads = {
                                            val cleared = downloader?.clearCompleted() ?: emptyList()
                                            if (cleared.isNotEmpty()) {
                                                presenterScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = strings.text(UiText.CompletedDownloadsCleared),
                                                        actionLabel = strings.text(UiText.Undo),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        downloader?.restoreDownloads(cleared)
                                                    }
                                                }
                                            }
                                        },
                                        onCancelDownload = { chapterId ->
                                            val cancelled = downloader?.cancel(chapterId)
                                            if (cancelled != null) {
                                                presenterScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = strings.text(UiText.DownloadCancelled),
                                                        actionLabel = strings.text(UiText.Undo),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        downloader.restoreDownloads(listOf(cancelled))
                                                    }
                                                }
                                            }
                                        },
                                        onRetryDownload = { downloader?.retry(it) },
                                        onRetryAllFailedDownloads = { downloader?.retryAllFailed() },
                                        onReadDownloadedChapter = { mangaId, chapterId ->
                                            libraryPresenter.openMangaDetail(mangaId)
                                            navigator.navigate(DesktopDestination.Reader(chapterId))
                                        },
                                        isUpdatingLibrary = isUpdatingLibrary,
                                        isRepairingCovers = isRepairingCovers,
                                        onRepairBrokenCovers = libraryPresenter::repairBrokenCovers,
                                        lastUpdateResult = lastUpdateResult,
                                        updateRunState = updateRunState,
                                        updateProgress = updateProgress,
                                        onCancelLibraryUpdate = { runtime.libraryUpdateScheduler?.cancelUpdate() },
                                        updatedChapters = updatedChapters,
                                        onCheckForUpdates = {
                                            presenterScope.launch { runtime.libraryUpdateScheduler?.triggerUpdateNow() }
                                        },
                                        // Browse
                                        browseContent = {
                                            mihon.desktop.ui.browse.BrowseContentView(
                                                runtime = runtime,
                                                detailState = mangaDetailState,
                                                detailActions = sharedMangaDetailActions,
                                                onOpenMangaDetail = libraryPresenter::openMangaDetail,
                                                onCloseMangaDetail = { libraryPresenter.selectManga(null) },
                                                onRetryMangaDetail = libraryPresenter::retryDetail,
                                            )
                                        },
                                        // History
                                        historyGroups = historyState.groups,
                                        historyQuery = historyState.query,
                                        onHistoryQueryChange = { historyService?.setQuery(it) },
                                        onDeleteHistoryItem = { chapterId ->
                                            val item = historyState.groups.flatMap { it.items }.find {
                                                it.chapterId ==
                                                    chapterId
                                            }
                                            historyService?.deleteItem(chapterId)
                                            presenterScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.text(UiText.HistoryItemDeleted),
                                                    actionLabel = strings.text(UiText.Undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed && item != null) {
                                                    historyService?.restoreItem(item)
                                                }
                                            }
                                        },
                                        onClearAllHistory = { historyService?.clearAll() },
                                        // Settings & Diagnostics
                                        preferenceStore = runtime.preferences,
                                        readerSettingsStore = remember {
                                            DesktopReaderSettingsStore(runtime.preferences)
                                        },
                                        diagnosticService = runtime.diagnosticService,
                                        trackerManager = runtime.trackerManager,
                                        trackSyncService = runtime.trackSyncService,
                                        backupScheduler = runtime.backupScheduler,
                                        syncScheduler = runtime.syncScheduler,
                                        syncServerManager = runtime.syncServerManager,
                                        backgroundScheduler = runtime.backgroundScheduler,
                                        updateScheduler = runtime.libraryUpdateScheduler,
                                        cookieStore = runtime.cookieStore,
                                        onUpdateLibrary = {
                                            presenterScope.launch {
                                                runtime.libraryUpdateScheduler?.triggerUpdateNow()
                                            }
                                        },
                                        onEditInfo = { libraryPresenter.setEditInfoDialogOpen(true) },
                                        onDismissEditInfo = { libraryPresenter.setEditInfoDialogOpen(false) },
                                        onSaveMangaInfo = libraryPresenter::updateSelectedMangaInfo,
                                        onResetMangaInfo = libraryPresenter::resetSelectedMangaInfo,
                                        onChapterFilterChange = libraryPresenter::setChapterFilter,
                                        onChapterSortChange = libraryPresenter::setChapterSort,
                                        onToggleBookmark = libraryPresenter::toggleChapterBookmark,
                                        onToggleRead = libraryPresenter::toggleChapterRead,
                                        onMarkPreviousRead = libraryPresenter::markPreviousChaptersRead,
                                        onDownloadChapter = libraryPresenter::downloadChapter,
                                        onDeleteDownload = { chapterId ->
                                            libraryPresenter.deleteChapterDownload(chapterId)
                                            presenterScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.text(UiText.DownloadDeleted),
                                                    actionLabel = strings.text(UiText.Undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    libraryPresenter.downloadChapter(chapterId)
                                                }
                                            }
                                        },
                                        onDownloadBatch = { amount ->
                                            if (amount == -1) {
                                                libraryPresenter.downloadNextChapters(null, unreadOnly = false)
                                            } else {
                                                libraryPresenter.downloadNextChapters(amount, unreadOnly = true)
                                            }
                                        },
                                        onBatchBookmarkChapters = libraryPresenter::batchBookmarkChapters,
                                        onBatchMarkChaptersRead = libraryPresenter::batchMarkChaptersRead,
                                        onBatchDownloadChapters = libraryPresenter::batchDownloadChapters,
                                        onBatchDeleteDownloads = { chapterIds ->
                                            libraryPresenter.batchDeleteChapterDownloads(chapterIds)
                                            presenterScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.text(UiText.DownloadDeleted),
                                                    actionLabel = strings.text(UiText.Undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    chapterIds.forEach { libraryPresenter.downloadChapter(it) }
                                                }
                                            }
                                        },
                                        onOpenChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(true)
                                        },
                                        onDismissChapterSettings = {
                                            libraryPresenter.setChapterSettingsDialogOpen(false)
                                        },
                                        onChapterDisplayModeChange = libraryPresenter::setChapterDisplayMode,
                                        onExcludedScanlatorsChange = libraryPresenter::setExcludedScanlators,
                                        onShowMissingChaptersChange = libraryPresenter::setShowMissingChapters,
                                        onSetChapterSettingsAsDefault = libraryPresenter::setChapterSettingsAsDefault,
                                        onResetChapterSettingsToDefault =
                                        libraryPresenter::resetChapterSettingsToDefault,
                                        onDuplicateOpenManga = libraryPresenter::openDuplicateManga,
                                        onDuplicateMigrate = libraryPresenter::migrateDuplicateTo,
                                        onDuplicateAddAnyway = libraryPresenter::addDuplicateAnyway,
                                        onDuplicateDismiss = libraryPresenter::dismissDuplicateDialog,
                                        sourceNameFor = { sourceId ->
                                            sourceNames[sourceId] ?: strings.text(UiText.SourceFallback, sourceId)
                                        },
                                        sourceBaseUrlFor = { sourceId -> sourceBaseUrls[sourceId] },
                                        downloadCacheCleaner = runtime.downloadCacheCleaner,
                                        downloadsDir = runtime.downloader?.diskProvider?.downloadsDir
                                            ?: runtime.directories.root.resolve("media").resolve("downloads"),
                                        diskCacheDir = runtime.directories.cache,
                                        onOpenTracking = { isTrackingDialogOpen = true },
                                        onExportBackup = {
                                            presenterScope.launch {
                                                val path =
                                                    mihon.desktop.ui.library.chooseExportBackup(
                                                        strings.text(UiText.ExportBackup),
                                                    )
                                                        ?: return@launch
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        runtime.backupExporter.export(path)
                                                    }
                                                    snackbarHostState.showSnackbar(
                                                        strings.backupExportSuccess(path.toString()),
                                                    )
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(
                                                        strings.backupExportFailed(e.message ?: ""),
                                                    )
                                                }
                                            }
                                        },
                                        onPreferencesChanged = { updated ->
                                            preferences = updated
                                            appLockController.refresh()
                                        },
                                        // Phase 14: Stats & Incognito
                                        statsData = statsData,
                                        onRefreshStats = { presenterScope.launch { statsService?.refresh() } },
                                        incognitoMode = preferences.incognitoMode,
                                        onToggleIncognito = {
                                            val updated = runtime.preferences.updatePreferences {
                                                it.copy(incognitoMode = !it.incognitoMode)
                                            }
                                            preferences = updated
                                        },
                                        // Upcoming calendar (transient view opened from Updates)
                                        isUpcomingOpen = destination == DesktopDestination.Upcoming,
                                        upcomingContent = upcomingContent,
                                        onOpenUpcoming = { navigator.navigate(DesktopDestination.Upcoming) },
                                        onCloseUpcoming = { navigator.back() },
                                        // App lock (desktop security)
                                        appLockController = appLockController,
                                        onLockNow = if (preferences.appLockEnabled) {
                                            appLockController::lockNow
                                        } else {
                                            null
                                        },
                                    )
                                    if (destination is DesktopDestination.MangaDetails) {
                                        DesktopBackHandler {
                                            libraryPresenter.selectManga(null)
                                            navigator.pop()
                                        }
                                    }
                                    if (destination == DesktopDestination.Upcoming) {
                                        DesktopBackHandler {
                                            navigator.pop()
                                        }
                                    }
                                }
                                if (isManageCategoriesDialogOpen) {
                                    categoryService?.let { service ->
                                        ManageCategoriesDialog(
                                            categories = categories.sortedBy { it.order },
                                            onDismiss = { isManageCategoriesDialogOpen = false },
                                            onCreateCategory = service::createCategory,
                                            onRenameCategory = service::renameCategory,
                                            onDeleteCategory = { categoryId ->
                                                if (libraryState.selectedCategoryId == categoryId) {
                                                    libraryPresenter.selectCategory(SYSTEM_ALL_CATEGORY.id)
                                                }
                                                service.deleteCategory(categoryId)
                                            },
                                            onMoveCategory = { category, newIndex ->
                                                val ordered = categories.sortedBy { it.order }
                                                val currentIndex = ordered.indexOfFirst { it.id == category.id }
                                                if (currentIndex != -1 &&
                                                    newIndex in ordered.indices &&
                                                    currentIndex != newIndex
                                                ) {
                                                    val reordered = ordered.toMutableList()
                                                    reordered.add(newIndex, reordered.removeAt(currentIndex))
                                                    reordered.forEachIndexed { index, item ->
                                                        val newOrder = index.toLong()
                                                        if (item.order != newOrder) {
                                                            service.reorderCategory(item.id, newOrder)
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                val editManga = mangaDetailState.manga
                                if (isEditMangaCategoriesDialogOpen && editManga != null) {
                                    categoryService?.let { service ->
                                        EditMangaCategoriesDialog(
                                            allCategories = categories.sortedBy { it.order },
                                            assignedCategoryIds = editManga.categories.map { it.id }.toSet(),
                                            onDismiss = { isEditMangaCategoriesDialogOpen = false },
                                            onSave = { categoryIds ->
                                                service.setMangaCategories(editManga.id, categoryIds)
                                            },
                                        )
                                    }
                                }
                                val currentTrackingManga = mangaDetailState.manga
                                val trackerMgr = runtime.trackerManager
                                if (isTrackingDialogOpen && currentTrackingManga != null && trackerMgr != null) {
                                    val tracksFlow = remember(currentTrackingManga.id) {
                                        runtime.library.observeTracking(currentTrackingManga.id)
                                    }
                                    val tracksList by tracksFlow.collectAsState(initial = emptyList())
                                    val desktopTracks = tracksList.map { it.toDesktopTrackRecord() }

                                    TrackingDialog(
                                        mangaTitle = currentTrackingManga.title,
                                        trackers = trackerMgr.trackers,
                                        currentTracks = desktopTracks,
                                        onDismiss = { isTrackingDialogOpen = false },
                                        onSaveTrack = { track ->
                                            presenterScope.launch {
                                                val tracker = trackerMgr.get(track.trackerId)
                                                val local = track.copy(mangaId = currentTrackingManga.id)
                                                val resolved = if (tracker != null && tracker.isLoggedIn) {
                                                    try {
                                                        tracker.updateRemote(local)
                                                    } catch (_: Exception) {
                                                        runtime.trackingQueue?.enqueue(local)
                                                        local
                                                    }
                                                } else {
                                                    runtime.trackingQueue?.enqueue(local)
                                                    local
                                                }
                                                val record = resolved.toTrackingRecord()
                                                val existing = runtime.library.findTracking(
                                                    currentTrackingManga.id,
                                                    track.trackerId,
                                                )
                                                if (existing != null) {
                                                    runtime.library.updateTracking(record.copy(id = existing.id))
                                                } else {
                                                    runtime.library.insertTracking(record)
                                                }
                                            }
                                        },
                                        onUnbindTrack = { trackerId ->
                                            runtime.library.deleteTracking(currentTrackingManga.id, trackerId)
                                        },
                                        onSearchTrack = { tracker, query ->
                                            tracker.search(query)
                                        },
                                    )
                                }
                                pendingMangaOrganizationAction?.let { pendingAction ->
                                    AlertDialog(
                                        onDismissRequest = { pendingMangaOrganizationAction = null },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val added = libraryPresenter.setDetailFavorite(true)
                                                    pendingMangaOrganizationAction = null
                                                    if (added) {
                                                        when (pendingAction) {
                                                            MangaOrganizationAction.Categories ->
                                                                isEditMangaCategoriesDialogOpen = true
                                                            MangaOrganizationAction.Tracking ->
                                                                isTrackingDialogOpen =
                                                                    true
                                                        }
                                                    } else {
                                                        presenterScope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Unable to add this manga to the library",
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.testTag("manga-organization-confirm"),
                                            ) {
                                                Text(strings.mangaDetailAddToLibrary)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(
                                                onClick = { pendingMangaOrganizationAction = null },
                                                modifier = Modifier.testTag("manga-organization-cancel"),
                                            ) {
                                                Text(strings.dialogCancel)
                                            }
                                        },
                                        title = { Text(strings.mangaDetailAddToLibrary) },
                                        text = {
                                            Text(strings.mangaDetailOrganizationRequiresLibrary)
                                        },
                                        modifier = Modifier.testTag("manga-organization-confirmation"),
                                    )
                                }
                                ImportStateDialog(importState) { importState = ImportActionState.Idle }
                                BackupRestoreDialog(backupRestoreState, {
                                    backupRestore.cancel()
                                }, backupRestore::dismiss)
                                if (backupRestoreState is BackupRestoreState.Idle) {
                                    pendingMissingSources?.let { missingList ->
                                        val installable = missingList.mapNotNull { it.extension }.distinctBy { it.pkg }
                                        val missingMangaTotal = missingList.sumOf { it.mangaCount }
                                        val unresolvedMangaCount = missingList.filter {
                                            it.extension == null
                                        }.sumOf { it.mangaCount }

                                        AlertDialog(
                                            onDismissRequest = {
                                                if (!isInstallingMissingBatch) {
                                                    pendingMissingSources = null
                                                }
                                            },
                                            modifier = Modifier.testTag("missing-source-dialog"),
                                            title = {
                                                Text(
                                                    strings.missingSourceDialogTitle(
                                                        missingMangaTotal,
                                                        installable.size,
                                                    ),
                                                )
                                            },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (installable.isNotEmpty()) {
                                                        Text(installable.joinToString(", ") { it.name })
                                                    }
                                                    if (unresolvedMangaCount > 0) {
                                                        Text(
                                                            text = strings.missingSourceDialogNotFound(
                                                                unresolvedMangaCount,
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    if (isInstallingMissingBatch) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                            Text(strings.missingSourceInstalling)
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                if (installable.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = {
                                                            presenterScope.launch {
                                                                isInstallingMissingBatch = true
                                                                try {
                                                                    for (ext in installable) {
                                                                        runtime.extensionInstaller.downloadAndInstall(
                                                                            downloadUrl = ext.downloadUrl,
                                                                            expectedSha256 = ext.sha256,
                                                                            repoUrl = ext.repoUrl,
                                                                            storeItem = ext,
                                                                        )
                                                                    }
                                                                    libraryPresenter.refreshDetails()
                                                                } catch (e: Exception) {
                                                                    snackbarHostState.showSnackbar(
                                                                        e.message ?: "Failed to install extensions",
                                                                    )
                                                                } finally {
                                                                    isInstallingMissingBatch = false
                                                                    pendingMissingSources = null
                                                                }
                                                            }
                                                        },
                                                        enabled = !isInstallingMissingBatch,
                                                        modifier = Modifier.testTag(
                                                            "missing-source-dialog-install-all",
                                                        ),
                                                    ) {
                                                        Text(strings.missingSourceDialogInstallAll)
                                                    }
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(
                                                    onClick = { pendingMissingSources = null },
                                                    enabled = !isInstallingMissingBatch,
                                                    modifier = Modifier.testTag("missing-source-dialog-later"),
                                                ) {
                                                    Text(strings.missingSourceDialogLater)
                                                }
                                            },
                                        )
                                    }
                                }
                                if (showShortcutsHelp) {
                                    DesktopShortcutsDialog(
                                        onDismiss = { showShortcutsHelp = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MangaOrganizationAction {
    Categories,
    Tracking,
}

@Composable
private fun ReaderDestination(
    destination: DesktopDestination.Reader,
    runtime: DesktopRuntime,
    mangaTitle: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenMangaDetails: () -> Unit,
    onFullscreen: () -> Unit,
    onBorderless: () -> Unit,
    onEscape: () -> Boolean,
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: ((Long) -> Unit)? = null,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    mangaId: Long? = null,
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    previousChapter: ReaderChapterTransitionChapter? = null,
    nextChapter: ReaderChapterTransitionChapter? = null,
    currentChapter: ReaderChapterTransitionChapter? = null,
    currentChapterDownloaded: Boolean = false,
    bookmarkStore: ReaderChapterBookmarkStore? = null,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var readerHandle by remember(destination) { mutableStateOf<mihon.desktop.reader.DesktopReaderHandle?>(null) }
    val factory = runtime.readerFactory
    if (factory == null) {
        Text(strings.readerUnavailable)
        return
    }
    LaunchedEffect(destination) {
        val isIncognito = runtime.preferences.load().incognitoMode
        val handle = withContext(Dispatchers.Default) {
            factory.createHandle(isIncognito = isIncognito)
        }
        readerHandle = handle
        withContext(Dispatchers.Default) {
            handle.session.open(destination.chapterId)
        }
    }
    val activeHandle = readerHandle
    if (activeHandle == null) {
        Column(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(strings.readerOpeningChapter)
        }
    } else {
        val activeSession = activeHandle.session
        ReaderScreen(
            session = activeSession,
            title = mangaTitle,
            chapterTitle = chapterTitle,
            settingsStore = DesktopReaderSettingsStore(runtime.preferences),
            onBack = onBack,
            onOpenMangaDetails = onOpenMangaDetails,
            onFullscreen = onFullscreen,
            onBorderless = onBorderless,
            onEscape = onEscape,
            onPreviousChapter = onPreviousChapter,
            onNextChapter = onNextChapter,
            onChapterSelected = onChapterSelected,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            onRetryChapter = { activeSession.open(destination.chapterId) },
            mangaId = mangaId,
            chapterCatalog = chapterCatalog,
            previousChapter = previousChapter,
            nextChapter = nextChapter,
            currentChapter = currentChapter,
            currentChapterDownloaded = currentChapterDownloaded,
            bookmarkStore = bookmarkStore,
            pageContent = { page, _, modifier ->
                DecodedReaderPage(
                    content = activeHandle.content,
                    page = page,
                    modifier = modifier,
                )
            },
        )
    }
}

@Composable
internal fun ImportStateDialog(state: ImportActionState, onDismiss: () -> Unit) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    when (state) {
        ImportActionState.Idle -> Unit
        ImportActionState.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(strings.importDialogTitle) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(strings.importDialogProgress)
                }
            },
        )
        is ImportActionState.Completed -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogDone) } },
                title = { Text(strings.importDialogCompleteTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.importReportTitle(result.reportId))
                        Text(strings.importReportManga(result.counts.mangaInserted, result.counts.mangaMerged))
                        Text(
                            strings.importReportChapters(result.counts.chaptersInserted, result.counts.chaptersMerged),
                        )
                        Text(strings.importReportCategories(result.counts.categoriesLinked))
                        Text(
                            strings.importReportPreferences(
                                result.counts.preferencesImported,
                                result.counts.preferencesSkipped,
                            ),
                        )
                        if (result.skipCategories.isNotEmpty()) {
                            Text(strings.importReportSkipCategories(result.skipCategories.joinToString()))
                        }
                    }
                },
            )
        }
        is ImportActionState.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
            title = { Text(strings.importDialogRejectedTitle) },
            text = { Text(strings.importReportCategory(state.category)) },
        )
        is ImportActionState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
            title = { Text(strings.importDialogFailedTitle) },
            text = { Text(state.message) },
        )
    }
}
