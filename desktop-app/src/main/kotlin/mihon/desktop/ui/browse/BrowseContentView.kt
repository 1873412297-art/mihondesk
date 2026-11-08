package mihon.desktop.ui.browse

import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.SourceState
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

sealed interface BrowseNavigationState {
    data object Home : BrowseNavigationState
    data class SourceView(
        val source: SourceDescriptor,
        val mode: SourceListingMode = SourceListingMode.Popular,
    ) : BrowseNavigationState
    data class MangaDetail(
        val source: SourceDescriptor,
        val manga: SManga,
    ) : BrowseNavigationState
    data class ExtensionDetails(
        val pkg: String,
    ) : BrowseNavigationState
    data class SourcePreferences(
        val sourceId: Long,
        val returnToExtensionPkg: String? = null,
    ) : BrowseNavigationState
}

@Composable
fun BrowseContentView(
    runtime: DesktopRuntime,
    browseRequests: BrowseRequests = remember { BrowseRequests() },
    detailState: MangaDetailUiState = MangaDetailUiState(),
    detailActions: MangaDetailActions = MangaDetailActions(),
    onReadChapter: (Long) -> Unit = detailActions.onReadChapter,
    onOpenMangaDetail: (Long) -> Unit = {},
    onCloseMangaDetail: () -> Unit = {},
    onRetryMangaDetail: () -> Unit = {},
    onImportLocal: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var navState by remember { mutableStateOf<BrowseNavigationState>(BrowseNavigationState.Home) }
    val pendingBrowseRequest by browseRequests.pending.collectAsState()
    LaunchedEffect(pendingBrowseRequest) {
        pendingBrowseRequest?.let { request ->
            navState = request
            browseRequests.consume()
        }
    }
    mihon.desktop.navigation.DesktopBackHandler(enabled = navState != BrowseNavigationState.Home) {
        when (val current = navState) {
            is BrowseNavigationState.SourcePreferences -> {
                navState = if (current.returnToExtensionPkg != null) {
                    BrowseNavigationState.ExtensionDetails(current.returnToExtensionPkg)
                } else {
                    BrowseNavigationState.Home
                }
                true
            }
            is BrowseNavigationState.ExtensionDetails,
            is BrowseNavigationState.MangaDetail,
            is BrowseNavigationState.SourceView,
            -> {
                navState = BrowseNavigationState.Home
                true
            }
            BrowseNavigationState.Home -> false
        }
    }

    val presenter = remember(runtime) {
        BrowsePresenter(
            sourceManager = runtime.sourceManager,
            installer = runtime.extensionInstaller,
            storeService = runtime.extensionStoreService,
            libraryRepository = runtime.library,
            preferenceStore = runtime.preferences,
            scope = scope,
            onExtensionUpdatesAvailable = { count ->
                runtime.notificationService?.notifyExtensionUpdatePending(count)
            },
            iconFetcher = { url -> runtime.imageLoader.fetchBytes(url) },
            activityCenter = runtime.activityCenter,
        )
    }
    val browseState by presenter.state.collectAsState()
    val pendingInstall by browseRequests.pendingInstall.collectAsState()
    LaunchedEffect(pendingInstall) {
        pendingInstall?.let { item ->
            browseRequests.consumeInstall()
            presenter.installExtension(item)
        }
    }
    val pendingMigration by browseRequests.pendingMigration.collectAsState()
    LaunchedEffect(pendingMigration) {
        pendingMigration?.let { source ->
            browseRequests.consumeMigration()
            navState = BrowseNavigationState.Home
            presenter.setTab(BrowseTab.Migration)
            presenter.selectMigrationSource(source)
        }
    }

    val importActions = remember(runtime) {
        val importController = LibraryImportController(
            backupImporter = runtime.backupImporter,
            localImporter = runtime.localImporter,
            localLibraryRoot = runtime.localLibraryRoot,
        )
        LibraryImportActions(
            importBackup = importController::importBackup,
            importLocal = importController::importLocal,
        )
    }

    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(Unit) {
        presenter.reloadPreferences()
    }
    LaunchedEffect(presenter) {
        presenter.snackbarEvents.collect { message ->
            if (message.startsWith("HIDDEN:")) {
                val parts = message.split(":", limit = 3)
                val sourceId = parts.getOrNull(1)?.toLongOrNull()
                val sourceName = parts.getOrNull(2) ?: ""
                val strings = mihon.desktop.i18n.DesktopStrings.resolve(runtime.preferences.load().language)
                val result = snackbarHostState?.showSnackbar(
                    message = strings.browseSourceHidden(sourceName),
                    actionLabel = strings.text(UiText.Undo),
                )
                if (result == SnackbarResult.ActionPerformed && sourceId != null) {
                    presenter.unhideSource(sourceId)
                }
            } else {
                snackbarHostState?.showSnackbar(message)
            }
        }
    }

    when (val nav = navState) {
        BrowseNavigationState.Home -> {
            BrowseScreen(
                state = browseState,
                onTabSelected = presenter::setTab,
                onSearchQueryChange = presenter::setSearchQuery,
                onSourceSelected = { source, mode ->
                    navState = BrowseNavigationState.SourceView(source, mode)
                },
                onTogglePinSource = presenter::togglePinSource,
                onInstallExtension = presenter::installExtension,
                onInstallFromFile = presenter::installFromFile,
                onCancelInstallation = presenter::cancelInstallation,
                onUninstallExtension = presenter::uninstallExtension,
                onToggleExtensionEnabled = presenter::toggleExtensionEnabled,
                onExtensionSelected = { extension ->
                    presenter.setTab(BrowseTab.Extensions)
                    navState = BrowseNavigationState.ExtensionDetails(extension.pkg)
                },
                onAddRepository = presenter::addRepository,
                onRemoveRepository = presenter::removeRepository,
                onUpdateAllPending = presenter::updateAllPending,
                onRefresh = presenter::refresh,
                onOpenGlobalSearch = presenter::openGlobalSearch,
                onCloseGlobalSearch = presenter::closeGlobalSearch,
                onGlobalSearchQueryChange = presenter::setGlobalSearchQuery,
                onPerformGlobalSearch = presenter::performGlobalSearch,
                onToggleGlobalSearchOnlyPinned = presenter::setGlobalSearchOnlyPinned,
                onGlobalMangaSelected = { source, manga ->
                    navState = BrowseNavigationState.MangaDetail(source, manga)
                },
                onHideSource = presenter::hideSource,
                onUnhideSource = presenter::unhideSource,
                onSourceLanguageFilterChange = presenter::setSourceLanguageFilter,
                onSelectMigrationSource = presenter::selectMigrationSource,
                onSearchTargetMigrationSource = presenter::searchTargetMigrationSource,
                onAutoMatchTargetSource = presenter::autoMatchTargetSource,
                onPerformMigration = presenter::performMigration,
                onStartBatchMigration = presenter::startBatchMigration,
                onCancelBatchMigration = presenter::cancelBatchMigration,
                onDismissBatchReport = presenter::dismissBatchMigrationReport,
                trustStore = runtime.extensionInstaller.trustStore,
            )
        }
        is BrowseNavigationState.SourceView -> SourceViewContent(
            runtime = runtime,
            source = nav.source,
            mode = nav.mode,
            importActions = importActions,
            onOpenManga = { source, manga -> navState = BrowseNavigationState.MangaDetail(source, manga) },
            onBack = { navState = BrowseNavigationState.Home },
        )
        is BrowseNavigationState.MangaDetail -> MangaDetailBrowseContent(
            runtime = runtime,
            source = nav.source,
            manga = nav.manga,
            detailState = detailState,
            detailActions = detailActions,
            onReadChapter = onReadChapter,
            onOpenMangaDetail = onOpenMangaDetail,
            onCloseMangaDetail = onCloseMangaDetail,
            onRetryMangaDetail = onRetryMangaDetail,
            onBackToSource = { navState = BrowseNavigationState.SourceView(nav.source) },
        )
        is BrowseNavigationState.ExtensionDetails -> {
            val extension = browseState.installedExtensions.find { it.pkg == nav.pkg }
            if (extension == null) {
                LaunchedEffect(nav.pkg) {
                    navState = BrowseNavigationState.Home
                }
            } else {
                val sourceStates = browseState.extensionSourceStates[extension.pkg]
                    ?: extension.manifest.sources.map { source ->
                        SourceState(source = source, extensionPackage = extension.pkg)
                    }
                val updateItem = browseState.availableExtensions
                    .filter { it.pkg == extension.pkg && it.versionCode > extension.manifest.versionCode }
                    .maxByOrNull { it.versionCode }

                ExtensionDetailsScreen(
                    extension = extension,
                    sources = sourceStates,
                    isExtensionIncognito = extension.pkg in browseState.incognitoExtensionPackages,
                    updateItem = updateItem,
                    onBack = { navState = BrowseNavigationState.Home },
                    onToggleEnabled = { enabled ->
                        presenter.toggleExtensionEnabled(extension.pkg, enabled)
                    },
                    onUpdate = { item -> presenter.installExtension(item) },
                    onUninstall = {
                        presenter.uninstallExtension(extension.pkg)
                        navState = BrowseNavigationState.Home
                    },
                    onClearCookies = { presenter.clearExtensionCookies(extension.pkg) },
                    onToggleIncognito = { incognito ->
                        presenter.setExtensionIncognito(extension.pkg, incognito)
                    },
                    onToggleSourceEnabled = presenter::setSourceEnabled,
                    onToggleSourceIncognito = presenter::setSourceIncognito,
                    onOpenSourcePreferences = { sourceId ->
                        navState = BrowseNavigationState.SourcePreferences(sourceId, extension.pkg)
                    },
                    onOpenSource = { source ->
                        navState = BrowseNavigationState.SourceView(source)
                    },
                    trustStore = runtime.extensionInstaller.trustStore,
                )
            }
        }
        is BrowseNavigationState.SourcePreferences -> {
            val sourceState = browseState.sourceStates.find { it.source.id == nav.sourceId }
                ?: browseState.extensionSourceStates.values.flatten()
                    .find { it.source.id == nav.sourceId }

            if (sourceState == null) {
                LaunchedEffect(nav.sourceId) {
                    navState = BrowseNavigationState.Home
                }
            } else {
                SourcePreferencesScreen(
                    source = sourceState.source,
                    manager = runtime.sourceManager,
                    definitions = browseState.sourcePreferenceDefinitions[nav.sourceId].orEmpty(),
                    values = browseState.sourcePreferenceValues[nav.sourceId].orEmpty(),
                    onBack = {
                        navState = nav.returnToExtensionPkg
                            ?.let { pkg -> BrowseNavigationState.ExtensionDetails(pkg) }
                            ?: BrowseNavigationState.Home
                    },
                    onValueChange = { key, value ->
                        presenter.setSourcePreferenceValue(nav.sourceId, key, value)
                    },
                )
            }
        }
    }
}
