package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.ui.library.MangaDetailScreen
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import mihon.desktop.ui.library.MangaDetailActions as DetailCallbacks

/** Local and online manga details opened from a browse source. */
@Composable
internal fun MangaDetailBrowseContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    manga: SManga,
    detailState: MangaDetailUiState,
    detailActions: DetailCallbacks,
    onReadChapter: (Long) -> Unit,
    onOpenMangaDetail: (Long) -> Unit,
    onCloseMangaDetail: () -> Unit,
    onRetryMangaDetail: () -> Unit,
    onBackToSource: () -> Unit,
) {
    if (source.isLocalSource()) {
        LocalMangaDetailContent(
            runtime = runtime,
            source = source,
            manga = manga,
            onBack = onBackToSource,
            onReadChapter = onReadChapter,
        )
    } else {
        OnlineMangaDetailContent(
            runtime = runtime,
            source = source,
            manga = manga,
            detailState = detailState,
            detailActions = detailActions,
            onReadChapter = onReadChapter,
            onOpenMangaDetail = onOpenMangaDetail,
            onCloseMangaDetail = onCloseMangaDetail,
            onRetryMangaDetail = onRetryMangaDetail,
            onBackToSource = onBackToSource,
        )
    }
}

@Composable
private fun OnlineMangaDetailContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    manga: SManga,
    detailState: MangaDetailUiState,
    detailActions: DetailCallbacks,
    onReadChapter: (Long) -> Unit,
    onOpenMangaDetail: (Long) -> Unit,
    onCloseMangaDetail: () -> Unit,
    onRetryMangaDetail: () -> Unit,
    onBackToSource: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(source.id, manga.url) {
        OnlineDetailController(
            sourceId = source.id,
            manga = manga,
            syncService = runtime.onlineMangaSyncService,
            scope = scope,
            onOpened = onOpenMangaDetail,
            onChanged = onRetryMangaDetail,
        )
    }
    val loadState by controller.state.collectAsState()
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    LaunchedEffect(controller) {
        controller.load()
    }
    val displayedState = when {
        loadState.loading -> MangaDetailUiState(loading = true)
        loadState.errorMessage != null -> MangaDetailUiState(loading = false, errorMessage = loadState.errorMessage)
        else -> detailState
    }
    MangaDetailScreen(
        state = displayedState,
        actions = detailActions.copy(onReadChapter = onReadChapter),
        sourceBaseUrl = source.baseUrl,
        onBack = {
            controller.cancel()
            onCloseMangaDetail()
            onBackToSource()
        },
        onRetry = { controller.load(forceRefresh = true) },
        onToggleLibrary = { controller.updateLibrary(isFavorite = detailState.manga?.favorite == true) },
        onRefreshSource = { controller.load(forceRefresh = true) },
        isLibraryActionRunning = loadState.syncingLibrary,
        isRefreshingSource = loadState.loading,
    )
}

internal data class OnlineDetailLoadState(
    val mangaId: Long? = null,
    val loading: Boolean = false,
    val syncingLibrary: Boolean = false,
    val errorMessage: String? = null,
)

/** Prepares an online manga for reading and keeps its library membership in sync. */
internal class OnlineDetailController(
    private val sourceId: Long,
    private val manga: SManga,
    private val syncService: OnlineMangaSyncService,
    private val scope: CoroutineScope,
    private val onOpened: (Long) -> Unit,
    private val onChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(OnlineDetailLoadState(loading = true))
    val state: StateFlow<OnlineDetailLoadState> = _state
    private var loadJob: Job? = null
    private var libraryJob: Job? = null

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, errorMessage = null) }
        loadJob = scope.launch {
            try {
                val mangaId = syncService.prepareOnlineMangaForReading(sourceId, manga, forceRefresh)
                currentCoroutineContext().ensureActive()
                onOpened(mangaId)
                onChanged()
                _state.update { it.copy(loading = false, mangaId = mangaId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _state.update {
                    it.copy(loading = false, errorMessage = e.message ?: "Failed to fetch manga details")
                }
            }
        }
    }

    fun updateLibrary(isFavorite: Boolean) {
        if (_state.value.syncingLibrary) return
        libraryJob = scope.launch {
            _state.update { it.copy(syncingLibrary = true, errorMessage = null) }
            try {
                if (!isFavorite) {
                    syncService.addOrUpdateOnlineManga(sourceId, manga)
                } else {
                    syncService.removeFromLibrary(sourceId, manga.url)
                }
                currentCoroutineContext().ensureActive()
                onChanged()
                _state.update { it.copy(syncingLibrary = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _state.update {
                    it.copy(syncingLibrary = false, errorMessage = "Failed to update library: ${e.message}")
                }
            }
        }
    }

    fun cancel() {
        loadJob?.cancel()
        libraryJob?.cancel()
    }
}
