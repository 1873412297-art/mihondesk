package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.extension.getLatest
import mihon.desktop.extension.getPopular
import mihon.desktop.extension.loadFilterList
import mihon.desktop.extension.searchManga
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.ui.library.ImportActionState
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.findNetworkFailure
import mihon.extension.ipc.toFilterList
import mihon.extension.ipc.toFilterListDto
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

/** Paging, filtering and library toggles of one source listing. */
internal class BrowseSourceController(
    private val source: SourceDescriptor,
    private val initialMode: SourceListingMode,
    private val sourceManager: DesktopSourceManager,
    private val library: LibraryRepository,
    private val onlineSync: OnlineMangaSyncService,
    private val scope: CoroutineScope,
    private val strings: DesktopStrings,
    private val showSnackbar: suspend (String) -> Unit,
) {
    private val _state = MutableStateFlow(
        BrowseSourceUiState(
            source = source,
            mode = initialMode,
            isLoading = true,
            filterList = FilterList(),
        ),
    )
    val state: StateFlow<BrowseSourceUiState> = _state
    private var pageJob: Job? = null
    private var lastRequest: SourcePageRequest? = null

    fun start() {
        loadFilters()
        loadPage(initialMode, 1, "")
    }

    fun cancel() {
        pageJob?.cancel()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun openFilters() {
        _state.update { it.copy(isFilterDialogOpen = true) }
    }

    fun closeFilters() {
        _state.update { it.copy(isFilterDialogOpen = false) }
    }

    fun applyFilters(applied: FilterList) {
        _state.update { it.copy(filterList = applied, isFilterDialogOpen = false) }
        loadPage(SourceListingMode.Search, 1, _state.value.query, applied)
    }

    fun changeMode(mode: SourceListingMode) {
        loadPage(mode, 1, _state.value.query)
    }

    fun search() {
        val mode = if (_state.value.query.isBlank() && _state.value.activeFilterCount == 0) {
            SourceListingMode.Popular
        } else {
            SourceListingMode.Search
        }
        loadPage(mode, 1, _state.value.query)
    }

    fun changePage(newPage: Int) {
        if (newPage > _state.value.page) {
            loadMore()
        } else {
            lastRequest?.let { loadPage(it.mode, newPage, it.query, it.filters) }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasNextPage) return
        val request = lastRequest ?: return
        loadPage(request.mode, current.page + 1, request.query, request.filters, append = true)
    }

    fun retry() {
        val request = lastRequest ?: return
        loadPage(request.mode, request.page, request.query, request.filters, request.append)
    }

    fun toggleLibrary(manga: SManga) {
        val current = _state.value
        if (manga.url in current.libraryPendingUrls) return
        val adding = manga.url !in current.inLibraryUrls
        _state.update { it.copy(libraryPendingUrls = it.libraryPendingUrls + manga.url) }
        scope.launch {
            val outcome = try {
                if (adding) {
                    onlineSync.addOrUpdateOnlineManga(source.id, manga)
                } else {
                    onlineSync.removeFromLibrary(source.id, manga.url)
                }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                e
            }
            val urls = _state.value.inLibraryUrls
            _state.update {
                it.copy(
                    libraryPendingUrls = it.libraryPendingUrls - manga.url,
                    inLibraryUrls = when {
                        outcome != null -> urls
                        adding -> urls + manga.url
                        else -> urls - manga.url
                    },
                )
            }
            val message = when {
                outcome != null -> strings.text(
                    UiText.BrowseLibraryActionFailed,
                    outcome.message ?: outcome::class.simpleName.orEmpty(),
                )
                adding -> strings.text(UiText.BrowseAddToLibraryDone, manga.title)
                else -> strings.text(UiText.BrowseRemoveFromLibraryDone, manga.title)
            }
            showSnackbar(message)
        }
    }

    fun importLocal(chooseAndImport: suspend () -> ImportActionState) {
        scope.launch {
            when (val result = chooseAndImport()) {
                is ImportActionState.Completed -> loadPage(SourceListingMode.Popular, 1, "")
                is ImportActionState.Failed -> _state.update { it.copy(errorMessage = result.message) }
                is ImportActionState.Rejected ->
                    _state.update { it.copy(errorMessage = "Import rejected: ${result.category}") }
                ImportActionState.Idle, ImportActionState.Running -> Unit
            }
        }
    }

    private fun loadFilters() {
        scope.launch {
            val filters = sourceManager.loadFilterList(source.id)
            if (filters.isNotEmpty()) {
                _state.update {
                    it.copy(filterList = filters, defaultFilterList = filters.toFilterListDto().toFilterList())
                }
            }
        }
    }

    private fun loadPage(
        mode: SourceListingMode,
        page: Int,
        query: String,
        filters: FilterList = _state.value.filterList,
        append: Boolean = false,
    ) {
        pageJob?.cancel()
        lastRequest = SourcePageRequest(mode, page, query, filters, append)
        _state.update {
            it.copy(
                isLoading = !append,
                isLoadingMore = append,
                errorMessage = null,
                networkFailure = null,
                unsupportedMode = null,
                mode = mode,
                page = if (append) it.page else page,
                hasNextPage = append && it.hasNextPage,
            )
        }
        pageJob = scope.launch {
            try {
                loadPageInto(mode, page, query, filters, append)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                handleLoadFailure(e, mode)
            }
        }
    }

    private suspend fun loadPageInto(
        mode: SourceListingMode,
        page: Int,
        query: String,
        filters: FilterList,
        append: Boolean,
    ) {
        val mangasPage = when (mode) {
            SourceListingMode.Popular -> sourceManager.getPopular(source.id, page)
            SourceListingMode.Latest -> sourceManager.getLatest(source.id, page)
            SourceListingMode.Search -> sourceManager.searchManga(source.id, page, query, filters)
        }
        val (inLibrary, chapterCounts) = withContext(Dispatchers.IO) {
            val sourceMangas = library.librarySnapshot(null).filter { it.sourceId == source.id }
            val inLib = sourceMangas.map { it.url }.toSet()
            val counts = if (source.isLocalSource()) {
                sourceMangas.associate { it.url to it.chapterCount }
            } else {
                emptyMap()
            }
            inLib to counts
        }
        currentCoroutineContext().ensureActive()
        _state.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                page = page,
                mangas = (if (append) it.mangas + mangasPage.mangas else mangasPage.mangas)
                    .distinctBy { manga -> manga.url },
                hasNextPage = mangasPage.hasNextPage,
                inLibraryUrls = inLibrary,
                chapterCounts = if (append) it.chapterCounts + chapterCounts else chapterCounts,
            )
        }
    }

    private fun handleLoadFailure(e: Exception, mode: SourceListingMode) {
        val isUnsupported = generateSequence<Throwable>(e) { it.cause }.any {
            it is UnsupportedOperationException ||
                (it as? IpcException)?.errorKind == "UnsupportedOperationException" ||
                it.message?.startsWith("UnsupportedOperationException") == true
        }
        _state.update {
            if (isUnsupported) {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    unsupportedMode = mode,
                    errorMessage = null,
                    networkFailure = null,
                )
            } else {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    unsupportedMode = null,
                    errorMessage = e.message ?: "Failed to load source page",
                    networkFailure = e.findNetworkFailure(),
                )
            }
        }
    }
}

/** The last page request, so paging and retry repeat the same query and filters. */
internal data class SourcePageRequest(
    val mode: SourceListingMode,
    val page: Int,
    val query: String,
    val filters: FilterList,
    val append: Boolean,
)
