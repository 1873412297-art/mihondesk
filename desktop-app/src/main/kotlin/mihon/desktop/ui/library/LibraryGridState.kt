package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.repository.LibraryRepository

/** Assembles the library grid state from the presenter's input flows. */
internal class LibraryGridState(
    private val repository: LibraryRepository,
    private val retryRequest: StateFlow<Long>,
    private val query: StateFlow<String>,
    private val selectedCategoryId: StateFlow<Long>,

    private val detailMangaId: MutableStateFlow<Long?>,
    private val onItemsLoaded: (List<LibraryManga>) -> Unit,
    private val filters: StateFlow<LibraryFilterState>,
    private val sort: StateFlow<LibrarySortState>,
    private val selectedMangaId: MutableStateFlow<Long?>,
    private val displayMode: StateFlow<LibraryDisplayMode>,
    private val gridSize: StateFlow<Float>,
    private val showUnreadBadge: StateFlow<Boolean>,
    private val showDownloadedBadge: StateFlow<Boolean>,
    private val selection: StateFlow<LibrarySelectionState>,
    private val isFilterDialogOpen: StateFlow<Boolean>,
    private val duplicateDialog: StateFlow<DuplicateMangaDialogState?>,
    private val isBatchCategoryDialogOpen: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val categories: Flow<List<DesktopCategory>> = repository.observeCategories()
        .map { records ->
            // The Mihon schema seeds a nameless system "Default" category (_id 0); it is not a user tab.
            listOf(SYSTEM_ALL_CATEGORY) + records.filter { it.id > 0 && it.name.isNotBlank() }.map {
                DesktopCategory(id = it.id, name = it.name, order = it.sortOrder, flags = it.flags)
            }
        }
        .flowOn(Dispatchers.IO)

    private val repositoryState: Flow<RepositoryState> = combine(
        retryRequest,
        selectedCategoryId,
    ) { _, catId -> catId }
        .flatMapLatest { catId ->
            flow { emitAll(repository.observeLibrary(catId)) }
                .map<List<LibraryManga>, RepositoryState>(RepositoryState::Loaded)
                .onStart { emit(RepositoryState.Loading) }
                .catch { error ->
                    emit(RepositoryState.Failed(error.message ?: "Unable to load library"))
                }
        }
        .flowOn(Dispatchers.IO)
        .onEach { result ->
            if (result is RepositoryState.Loaded) {
                selectedMangaId.update { selected ->
                    val retained = selected?.takeIf { id -> result.items.any { it.id == id } }
                    if (selected != null && retained == null) {
                        detailMangaId.compareAndSet(selected, null)
                    }
                    retained
                }
                if (selectedCategoryId.value == SYSTEM_ALL_CATEGORY.id) {
                    onItemsLoaded(result.items)
                }
            }
        }

    private val filterAndSort = combine(filters, sort) { f, s -> Pair(f, s) }

    private val layoutExtras = combine(isFilterDialogOpen, duplicateDialog) { open, dialog ->
        open to dialog
    }

    val state: StateFlow<LibraryUiState> = combine(
        repositoryState,
        query,
        categories,
        selectedCategoryId,
        filterAndSort,
    ) { repoResult, q, categories, selectedCatId, (filters, sort) ->
        ProcessedLibraryData(repoResult, q, categories, selectedCatId, filters, sort)
    }.combine(
        combine(
            selectedMangaId,
            displayMode,
            gridSize,
            showUnreadBadge,
            showDownloadedBadge,
            selection,
            layoutExtras,
        ) { values: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val extras = values[6] as Pair<Boolean, DuplicateMangaDialogState?>
            LayoutAndSelectionData(
                selectedMangaId = values[0] as Long?,
                displayMode = values[1] as LibraryDisplayMode,
                gridSize = values[2] as Float,
                showUnreadBadge = values[3] as Boolean,
                showDownloadedBadge = values[4] as Boolean,
                selectionState = values[5] as LibrarySelectionState,
                isFilterDialogOpen = extras.first,
                duplicateDialog = extras.second,
            )
        },
    ) { processed, layout ->
        when (processed.repoResult) {
            RepositoryState.Loading -> LibraryUiState(
                loading = true,
                query = processed.query,
                categories = processed.categories,
                selectedCategoryId = processed.selectedCategoryId,
                displayMode = layout.displayMode,
                gridSize = layout.gridSize,
                showUnreadBadge = layout.showUnreadBadge,
                showDownloadedBadge = layout.showDownloadedBadge,
                filterState = processed.filters,
                sortState = processed.sort,
                selectionState = layout.selectionState,
                isFilterDialogOpen = layout.isFilterDialogOpen,
                isBatchCategoryDialogOpen = isBatchCategoryDialogOpen.value,
                duplicateDialog = layout.duplicateDialog,
            )
            is RepositoryState.Failed -> LibraryUiState(
                loading = false,
                query = processed.query,
                errorMessage = processed.repoResult.message,
                categories = processed.categories,
                selectedCategoryId = processed.selectedCategoryId,
                displayMode = layout.displayMode,
                gridSize = layout.gridSize,
                showUnreadBadge = layout.showUnreadBadge,
                showDownloadedBadge = layout.showDownloadedBadge,
                filterState = processed.filters,
                sortState = processed.sort,
                selectionState = layout.selectionState,
                isFilterDialogOpen = layout.isFilterDialogOpen,
                isBatchCategoryDialogOpen = isBatchCategoryDialogOpen.value,
                duplicateDialog = layout.duplicateDialog,
            )
            is RepositoryState.Loaded -> {
                val normalizedQuery = processed.query.trim()
                val searchedItems = if (normalizedQuery.isEmpty()) {
                    processed.repoResult.items
                } else {
                    processed.repoResult.items.filter { manga ->
                        manga.title.contains(normalizedQuery, ignoreCase = true) ||
                            manga.author?.contains(normalizedQuery, ignoreCase = true) == true
                    }
                }

                val filteredItems = searchedItems.filter { matchesFilters(it, processed.filters) }
                val sortedItems = sortManga(filteredItems, processed.sort)

                LibraryUiState(
                    loading = false,
                    query = processed.query,
                    items = sortedItems,
                    selectedMangaId = layout.selectedMangaId?.takeIf { id -> sortedItems.any { it.id == id } },
                    categories = processed.categories,
                    selectedCategoryId = processed.selectedCategoryId,
                    displayMode = layout.displayMode,
                    gridSize = layout.gridSize,
                    filterState = processed.filters,
                    sortState = processed.sort,
                    selectionState = layout.selectionState,
                    isFilterDialogOpen = layout.isFilterDialogOpen,
                    isBatchCategoryDialogOpen = isBatchCategoryDialogOpen.value,
                    duplicateDialog = layout.duplicateDialog,
                )
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = LibraryUiState(),
        )
}
