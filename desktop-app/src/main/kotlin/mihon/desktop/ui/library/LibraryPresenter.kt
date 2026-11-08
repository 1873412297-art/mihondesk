package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPresenter(
    private val repository: LibraryRepository,
    scope: CoroutineScope,
    private val readerLibrary: ReaderLibraryPort? = repository as? ReaderLibraryPort,
    private val mutationPort: LibraryMutationPort? = repository as? LibraryMutationPort,
    private val preferences: DesktopPreferenceStore? = null,
    private val downloader: DesktopDownloader? = null,
    private val sourceManager: DesktopSourceManager? = null,
    private val extensionStoreService: ExtensionStoreService? = null,
    private val mangaRefreshHandler: (suspend (Long) -> Unit)? = null,
    private val coverFailures: mihon.desktop.image.CoverFailureRegistry = mihon.desktop.image.CoverFailureRegistry(),
) : AutoCloseable {
    var mangaRefreshDelegate: (suspend (Long) -> Unit)? = mangaRefreshHandler

    private val presenterJob = SupervisorJob(scope.coroutineContext[Job])
    private val presenterScope = CoroutineScope(scope.coroutineContext + presenterJob)
    private val query = MutableStateFlow("")
    private val _batchState = MutableStateFlow(LibraryBatchState())
    val batchState: StateFlow<LibraryBatchState> = _batchState
    private val selectedMangaId = MutableStateFlow<Long?>(null)
    private val detailMangaId = MutableStateFlow<Long?>(null)
    private val selectedCategoryId = MutableStateFlow(SYSTEM_ALL_CATEGORY.id)
    private val retryRequest = MutableStateFlow(0L)
    private val detailRetryRequest = MutableStateFlow(0L)

    private val isFilterDialogOpenState = MutableStateFlow(false)
    private val isBatchCategoryDialogOpenState = MutableStateFlow(false)
    private val batches = LibraryBatchOperations(
        scope = presenterScope,
        repository = repository,
        mutationPort = mutationPort,
        downloader = downloader,
        selected = { selection.state.value.selectedMangaIds },
        batchState = _batchState,
        onSelectionCleared = { clearSelection() },
        onBatchFinished = { retry() },
    )

    private val detailActions = MangaDetailMutations(
        scope = presenterScope,
        repository = repository,
        mutationPort = mutationPort,
        downloader = downloader,
        detailMangaId = { detailMangaId.value },
        detailManga = { detailState.value.manga },
        detailChapters = { detailState.value.allChapters },
        onChanged = { detailRetryRequest.value = System.currentTimeMillis() },
        onEditInfoClosed = { isEditInfoDialogOpenState.value = false },
        onLibraryChanged = { retry() },
    )

    private val duplicates = DuplicateMangaWatcher(
        scope = presenterScope,
        repository = repository,
        mutationPort = mutationPort,
        selectedCategoryId = selectedCategoryId,
        onMigrated = { retry() },
    )
    private val selection = LibrarySelection()

    private val displaySettings = LibraryDisplayPreferences(preferences)

    private val gridState = LibraryGridState(
        repository = repository,
        retryRequest = retryRequest,
        query = query,
        selectedCategoryId = selectedCategoryId,
        selectedMangaId = selectedMangaId,
        detailMangaId = detailMangaId,
        onItemsLoaded = { duplicates.observe(it) },
        filters = displaySettings.filterState,
        sort = displaySettings.sortState,
        displayMode = displaySettings.displayMode,
        gridSize = displaySettings.gridSize,
        showUnreadBadge = displaySettings.showUnreadBadge,
        showDownloadedBadge = displaySettings.showDownloadedBadge,
        selection = selection.state,
        isFilterDialogOpen = isFilterDialogOpenState,
        duplicateDialog = duplicates.dialogState,
        isBatchCategoryDialogOpen = isBatchCategoryDialogOpenState,
        scope = presenterScope,
    )

    val state: StateFlow<LibraryUiState> = gridState.state

    private val commands = LibraryCommands(
        scope = presenterScope,
        repository = repository,
        query = query,
        selectedMangaId = selectedMangaId,
        detailMangaId = detailMangaId,
        selectedCategoryId = selectedCategoryId,
        retryRequest = retryRequest,
        detailRetryRequest = detailRetryRequest,
        items = { state.value.items },
    )

    private val isEditInfoDialogOpenState = MutableStateFlow(false)
    private val isChapterSettingsDialogOpenState = MutableStateFlow(false)
    private val isReadingSettingsDialogOpenState = MutableStateFlow(false)
    private val ignoredMissingSourceMangaIds = MutableStateFlow<Set<Long>>(emptySet())
    private val cachedAvailableExtensions = MutableStateFlow<List<ExtensionStoreItem>>(emptyList())
    private val chapterSettings = ChapterSettingsCoordinator(
        scope = presenterScope,
        repository = repository,
        mutationPort = mutationPort,
        preferences = preferences,
        detailMangaId = { detailMangaId.value },
        detailManga = { detailState.value.manga },
        onPersisted = { detailRetryRequest.value = System.currentTimeMillis() },
    )

    private val detailStateFlow = MangaDetailState(
        scope = presenterScope,
        repository = repository,
        readerLibrary = readerLibrary,
        downloader = downloader,
        sourceManager = sourceManager,
        extensionStoreService = extensionStoreService,
        preferences = preferences,
        chapterSettings = chapterSettings,
        detailMangaId = detailMangaId,
        selectedMangaId = selectedMangaId,
        detailRetryRequest = detailRetryRequest,
        ignoredMissingSourceMangaIds = ignoredMissingSourceMangaIds,
        cachedAvailableExtensions = cachedAvailableExtensions,
        isEditInfoDialogOpen = isEditInfoDialogOpenState,
        isChapterSettingsDialogOpen = isChapterSettingsDialogOpenState,
        isReadingSettingsDialogOpen = isReadingSettingsDialogOpenState,
    )

    val detailState: StateFlow<MangaDetailUiState> = detailStateFlow.detailState

    private val coverRepair = CoverRepair(
        scope = presenterScope,
        repository = repository,
        sourceManager = sourceManager,
        detailManga = { detailState.value.manga },
        refreshDetails = { refreshDetails() },
        refreshDelegate = { mangaRefreshDelegate },
        coverFailures = coverFailures,
    )

    val isRepairingCovers: StateFlow<Boolean> = coverRepair.isRepairingCovers

    fun ignoreMissingSource(mangaId: Long) {
        ignoredMissingSourceMangaIds.update { it + mangaId }
    }

    fun refreshDetails() {
        retryDetail()
    }

    fun onCoverLoadFailed(code: Int, targetMangaId: Long? = null) = coverRepair.onCoverLoadFailed(code, targetMangaId)

    fun repairBrokenCovers() = coverRepair.repairBrokenCovers()

    fun cancelRepairBrokenCovers() = coverRepair.cancel()

    fun setAvailableExtensions(extensions: List<ExtensionStoreItem>) {
        cachedAvailableExtensions.value = extensions
    }

    fun setChapterFilter(filter: ChapterFilterState) {
        chapterSettings.updateCurrent { current ->
            current.copy(
                unreadFilter = filter.unread,
                downloadedFilter = filter.downloaded,
                bookmarkedFilter = filter.bookmarked,
            )
        }
    }

    fun setChapterSort(sort: ChapterSortState) {
        chapterSettings.updateCurrent { current ->
            current.copy(sortMode = sort.mode, sortAscending = sort.ascending)
        }
    }

    fun setChapterDisplayMode(mode: ChapterDisplayMode) {
        chapterSettings.updateCurrent { it.copy(displayMode = mode) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        chapterSettings.updateCurrent { it.copy(excludedScanlators = excludedScanlators) }
    }

    fun setShowMissingChapters(show: Boolean) {
        chapterSettings.updateCurrent { it.copy(showMissingChapters = show) }
    }

    fun setChapterSettingsDialogOpen(open: Boolean) {
        isChapterSettingsDialogOpenState.value = open
    }

    fun setReadingSettingsDialogOpen(open: Boolean) {
        isReadingSettingsDialogOpenState.value = open
    }

    fun saveReadingSettingsOverride(override: MangaReaderSettingsOverride) = chapterSettings.saveOverride(override)

    fun clearReadingSettingsOverride() = chapterSettings.clearOverride()

    fun setChapterSettingsAsDefault(applyToExisting: Boolean) = chapterSettings.setAsDefault(applyToExisting)

    fun resetChapterSettingsToDefault() = chapterSettings.resetToDefault()

    fun dismissDuplicateDialog() = duplicates.advance(markDismissed = true)

    fun addDuplicateAnyway() = duplicates.advance(markDismissed = true)

    fun openDuplicateManga(mangaId: Long) {
        duplicates.advance(markDismissed = true)
        // Duplicate candidates come from the full library, so make sure a filtered category
        // cannot immediately clear the requested selection.
        selectedCategoryId.value = SYSTEM_ALL_CATEGORY.id
        selectedMangaId.value = mangaId
        detailMangaId.value = mangaId
    }

    fun migrateDuplicateTo(existingMangaId: Long) = duplicates.migrateTo(existingMangaId)

    fun setEditInfoDialogOpen(open: Boolean) {
        isEditInfoDialogOpenState.value = open
    }

    fun toggleChapterBookmark(chapterId: Long) = detailActions.toggleChapterBookmark(chapterId)

    fun toggleChapterRead(chapterId: Long) = detailActions.toggleChapterRead(chapterId)

    fun batchBookmarkChapters(chapterIds: Set<Long>, bookmark: Boolean) =
        detailActions.batchBookmarkChapters(chapterIds, bookmark)

    fun batchMarkChaptersRead(chapterIds: Set<Long>, read: Boolean) =
        detailActions.batchMarkChaptersRead(chapterIds, read)

    fun batchDownloadChapters(chapterIds: Set<Long>) = detailActions.batchDownloadChapters(chapterIds)

    fun batchDeleteChapterDownloads(chapterIds: Set<Long>) =
        detailActions.batchDeleteChapterDownloads(chapterIds)

    fun markPreviousChaptersRead(chapterId: Long) = detailActions.markPreviousChaptersRead(chapterId)

    fun downloadChapter(chapterId: Long) = detailActions.downloadChapter(chapterId)

    fun downloadChapters(chapters: List<LibraryChapter>) = detailActions.downloadChapters(chapters)

    fun downloadNextChapters(amount: Int?, unreadOnly: Boolean = true) =
        detailActions.downloadNextChapters(amount, unreadOnly)

    fun deleteChapterDownload(chapterId: Long) = detailActions.deleteChapterDownload(chapterId)

    /** Marks a chapter read from surfaces that do not have the detail page open (e.g. Updates). */
    fun markChapterRead(mangaId: Long, chapterId: Long) = detailActions.markChapterRead(mangaId, chapterId)

    /** Enqueues a chapter download from surfaces that do not have the detail page open. */
    fun enqueueChapterDownload(mangaId: Long, chapterId: Long) =
        detailActions.enqueueChapterDownload(mangaId, chapterId)

    fun updateMangaInfo(
        mangaId: Long,
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) = detailActions.updateMangaInfo(mangaId, title, author, artist, description, genres, status, notes)

    fun updateSelectedMangaInfo(
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) = detailActions.updateSelectedMangaInfo(title, author, artist, description, genres, status, notes)

    fun resetSelectedMangaInfo() = detailActions.resetSelectedMangaInfo()

    fun setDetailFavorite(favorite: Boolean, onResult: (Boolean) -> Unit = {}) =
        detailActions.setDetailFavorite(favorite, onResult)

    fun setQuery(value: String) = commands.setQuery(value)

    fun continueReading(mangaId: Long, openChapter: (Long) -> Unit) =
        commands.continueReading(mangaId, openChapter)

    fun selectManga(id: Long?) = commands.selectManga(id)

    fun openMangaDetail(id: Long) = commands.openMangaDetail(id)

    fun selectCategory(categoryId: Long) = commands.selectCategory(categoryId)

    fun retry() = commands.retry()

    fun retryDetail() = commands.retryDetail()

    // Phase 11: Display Mode, Grid Size, Filtering & Sorting Actions
    fun setDisplayMode(mode: LibraryDisplayMode) = displaySettings.setDisplayMode(mode)

    fun setGridSize(size: Float) = displaySettings.setGridSize(size)

    fun setUnreadBadgeVisible(visible: Boolean) = displaySettings.setUnreadBadgeVisible(visible)

    fun setDownloadedBadgeVisible(visible: Boolean) = displaySettings.setDownloadedBadgeVisible(visible)

    fun setFilterState(filters: LibraryFilterState) = displaySettings.setFilterState(filters)

    fun setSortState(sort: LibrarySortState) = displaySettings.setSortState(sort)

    fun setFilterDialogOpen(open: Boolean) {
        isFilterDialogOpenState.value = open
    }

    fun setBatchCategoryDialogOpen(open: Boolean) {
        isBatchCategoryDialogOpenState.value = open
    }

    fun toggleSelectionMode(enabled: Boolean) = selection.toggleMode(enabled)

    fun toggleMangaSelection(mangaId: Long) = selection.toggle(mangaId)

    fun selectAll() = selection.selectAll(state.value.items.map { it.id }.toSet())

    fun clearSelection() = selection.clear()

    fun batchSetCategories(categoryIds: List<Long>) {
        batches.setCategories(categoryIds)
        isBatchCategoryDialogOpenState.value = false
    }

    fun batchMarkRead(read: Boolean) = batches.markRead(read)

    fun batchDownload(chaptersCount: Int) = batches.download(chaptersCount)

    fun batchRemoveFromLibrary(onRemoved: ((Set<Long>) -> Unit)? = null) = batches.removeFromLibrary(onRemoved)

    fun removeMangaFromLibrary(mangaId: Long, onRemoved: (() -> Unit)? = null) = batches.removeOne(mangaId, onRemoved)

    fun restoreLibraryManga(mangaIds: Set<Long>) = batches.restore(mangaIds)

    fun toggleMangaRead(mangaId: Long, read: Boolean) = batches.toggleRead(mangaId, read)

    fun downloadMangaChapters(mangaId: Long, amount: Int = 1) = batches.downloadMangaChapters(mangaId, amount)

    suspend fun shutdown() {
        close()
        presenterJob.join()
    }

    override fun close() {
        chapterSettings.close()
        coverRepair.close()
        duplicates.close()
        presenterScope.cancel()
    }
}
