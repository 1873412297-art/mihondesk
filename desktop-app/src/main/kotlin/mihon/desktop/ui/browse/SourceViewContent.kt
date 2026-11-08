package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

/** One source's listing: paging, search, filters and library toggles. */
@Composable
internal fun SourceViewContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    mode: SourceListingMode,
    importActions: LibraryImportActions,
    onOpenManga: (SourceDescriptor, SManga) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val snackbarHostState = LocalSnackbarHostState.current
    val controller = remember(source.id, mode) {
        BrowseSourceController(
            source = source,
            initialMode = mode,
            sourceManager = runtime.sourceManager,
            library = runtime.library,
            onlineSync = runtime.onlineMangaSyncService,
            scope = scope,
            strings = strings,
            showSnackbar = { message -> snackbarHostState?.showSnackbar(message) },
        )
    }
    val state by controller.state.collectAsState()
    var webPageOpen by remember(source.id) { mutableStateOf(false) }
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    LaunchedEffect(controller) {
        controller.start()
    }
    BrowseSourceScreen(
        state = state,
        onOpenWebPage = if (source.isLocalSource()) null else ({ webPageOpen = true }),
        onBack = onBack,
        onModeChange = controller::changeMode,
        onQueryChange = controller::setQuery,
        onSearch = controller::search,
        onPageChange = controller::changePage,
        onLoadMore = controller::loadMore,
        onMangaSelected = { manga -> onOpenManga(source, manga) },
        onToggleLibrary = controller::toggleLibrary,
        onRetry = controller::retry,
        onOpenFilters = controller::openFilters,
        onCloseFilters = controller::closeFilters,
        onApplyFilters = controller::applyFilters,
        onImportLocal = { controller.importLocal(importActions::chooseAndImportLocal) },
    )
    if (webPageOpen) {
        SourceWebPageDialog(
            runtime,
            source,
            onDismiss = { webPageOpen = false },
            onRetry = controller::retry,
        )
    }
}
