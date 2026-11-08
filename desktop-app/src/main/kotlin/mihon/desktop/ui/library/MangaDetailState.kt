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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.extension.MissingSourceResolver
import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.desktop.extension.compat.generateSourceId
import mihon.desktop.extension.get
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.readerSettingsOverride
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore

/** Assembles the manga detail state, including its shared repository subscription. */
internal class MangaDetailState(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val readerLibrary: ReaderLibraryPort?,
    private val downloader: DesktopDownloader?,
    private val sourceManager: DesktopSourceManager?,
    private val extensionStoreService: ExtensionStoreService?,
    private val preferences: DesktopPreferenceStore?,
    private val chapterSettings: ChapterSettingsCoordinator,
    private val detailMangaId: MutableStateFlow<Long?>,
    private val selectedMangaId: MutableStateFlow<Long?>,
    private val detailRetryRequest: StateFlow<Long>,
    private val ignoredMissingSourceMangaIds: MutableStateFlow<Set<Long>>,
    private val cachedAvailableExtensions: MutableStateFlow<List<ExtensionStoreItem>>,
    private val isEditInfoDialogOpen: StateFlow<Boolean>,
    private val isChapterSettingsDialogOpen: StateFlow<Boolean>,
    private val isReadingSettingsDialogOpen: StateFlow<Boolean>,
) {
    // Shared so the detail state and the missing-source detector observe ONE upstream
    // subscription - two cold collectors would race over the retry signal and starve
    // the detail flow.
    private val selectedRepositoryState: Flow<SelectedRepositoryState> = combine(
        detailMangaId,
        detailRetryRequest,
    ) { selected, _ -> selected }
        .flatMapLatest { selectedId ->
            if (selectedId == null) {
                flow<SelectedRepositoryState> {
                    emit(SelectedRepositoryState.Loaded(null, null, emptyList(), emptyMap()))
                }
            } else {
                flow<SelectedRepositoryState> {
                    val manga = repository.observeManga(selectedId)
                    val chapters = repository.observeChapters(selectedId)
                    emitAll(
                        combine(manga, chapters) { details, rows ->
                            SelectedRepositoryState.Loaded(
                                selectedId,
                                details,
                                rows,
                                rows.associate { chapter ->
                                    chapter.id to chapter.readerAvailability(readerLibrary)
                                },
                            )
                        },
                    )
                }
                    .onStart { emit(SelectedRepositoryState.Loading) }
                    .catch { error ->
                        emit(SelectedRepositoryState.Failed(error.message ?: "Unable to load manga details"))
                    }
            }
        }
        .flowOn(Dispatchers.IO)
        .onEach { result ->
            if (result is SelectedRepositoryState.Loaded && result.selectedId != null && result.manga == null) {
                detailMangaId.compareAndSet(result.selectedId, null)
                selectedMangaId.compareAndSet(result.selectedId, null)
            }
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private data class DialogStates(
        val isEditInfoOpen: Boolean,
        val isChapterSettingsOpen: Boolean,
        val isReadingSettingsOpen: Boolean,
    )

    private val dialogStates: Flow<DialogStates> = combine(
        isEditInfoDialogOpen,
        isChapterSettingsDialogOpen,
        isReadingSettingsDialogOpen,
    ) { edit, chapter, reading -> DialogStates(edit, chapter, reading) }

    private val missingSourceState: Flow<MissingSourceInfo?> = combine(
        selectedRepositoryState,
        ignoredMissingSourceMangaIds,
        cachedAvailableExtensions,
    ) { result, ignored, available ->
        val manga = (result as? SelectedRepositoryState.Loaded)?.manga
        if (manga == null || manga.sourceId == 0L || sourceManager == null) {
            null
        } else if (manga.id in ignored) {
            null
        } else if (sourceManager.get(manga.sourceId) != null) {
            null
        } else {
            if (available.isEmpty() && extensionStoreService != null) {
                scope.launch {
                    val fetched = MissingSourceResolver.ensureAvailableExtensions(extensionStoreService)
                    cachedAvailableExtensions.value = fetched
                }
            }
            val mangaCount = runCatching {
                repository.allMangaSnapshot().count { it.sourceId == manga.sourceId }
            }.getOrDefault(1).coerceAtLeast(1)
            val ext = available.firstOrNull { item ->
                item.sources.any { s ->
                    s.id == manga.sourceId ||
                        (s.id == 0L && TachiyomiExtensionConverter.generateSourceId(s.name, s.lang) == manga.sourceId)
                }
            }
            MissingSourceInfo(
                sourceId = manga.sourceId,
                mangaCount = mangaCount,
                extension = ext,
            )
        }
    }.flowOn(Dispatchers.IO)

    val detailState: StateFlow<MangaDetailUiState> = combine(
        selectedRepositoryState,
        chapterSettings.overrides,
        dialogStates,
        missingSourceState,
    ) { result, overrides, dialogs, missingSource ->
        val isEditInfoOpen = dialogs.isEditInfoOpen
        val isChapterSettingsOpen = dialogs.isChapterSettingsOpen
        val isReadingSettingsOpen = dialogs.isReadingSettingsOpen
        when (result) {
            SelectedRepositoryState.Loading -> MangaDetailUiState(
                loading = true,
                isChapterSettingsDialogOpen = isChapterSettingsOpen,
                isReadingSettingsDialogOpen = isReadingSettingsOpen,
            )
            is SelectedRepositoryState.Failed -> MangaDetailUiState(
                errorMessage = result.message,
                isChapterSettingsDialogOpen = isChapterSettingsOpen,
                isReadingSettingsDialogOpen = isReadingSettingsOpen,
            )
            is SelectedRepositoryState.Loaded -> {
                val manga = result.manga
                val rawChapters = result.chapters
                val downloadedIds = if (manga != null && downloader != null) {
                    rawChapters.filter { ch ->
                        downloader.isChapterDownloaded(manga.sourceId, manga.title, ch.id, ch.name, manga.id)
                    }.map { it.id }.toSet()
                } else {
                    emptySet()
                }

                val persistedSettings = manga?.toChapterSettings() ?: chapterSettings.defaultSettings()
                val overrideSettings = overrides.settings
                val settings = if (manga != null && overrides.mangaId == manga.id && overrideSettings != null) {
                    overrideSettings
                } else {
                    persistedSettings
                }
                val filter = settings.toFilterState()
                val scanlatorFiltered = if (settings.excludedScanlators.isEmpty()) {
                    rawChapters
                } else {
                    rawChapters.filter { chapter ->
                        val scanlator = chapter.scanlator?.trim()
                        scanlator.isNullOrBlank() || scanlator !in settings.excludedScanlators
                    }
                }
                val prioritized = applyScanlatorPriority(
                    scanlatorFiltered,
                    parsePreferredScanlators(preferences?.property(PREFERRED_SCANLATORS_KEY)),
                )
                val filtered = prioritized.filter { chapter ->
                    matchesChapterFilter(chapter, filter, downloadedIds)
                }
                val sorted = sortChapters(filtered, settings)
                val readerChapters = sortChapters(prioritized, settings)
                val availableScanlators = rawChapters.mapNotNull { chapter ->
                    chapter.scanlator?.trim()?.takeIf(String::isNotEmpty)
                }.toSet()

                MangaDetailUiState(
                    manga = manga,
                    chapters = sorted,
                    allChapters = rawChapters,
                    readerChapters = readerChapters,
                    readerAvailability = result.readerAvailability,
                    chapterFilterState = filter,
                    chapterSortState = settings.toSortState(),
                    chapterSettings = settings,
                    availableScanlators = availableScanlators,
                    chapterListItems = buildChapterListItems(sorted, settings),
                    isEditInfoDialogOpen = isEditInfoOpen,
                    isChapterSettingsDialogOpen = isChapterSettingsOpen,
                    isReadingSettingsDialogOpen = isReadingSettingsOpen,
                    readingSettingsOverride = manga?.readerSettingsOverride,
                    downloadedChapterIds = downloadedIds,
                    missingSource = missingSource,
                )
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = MangaDetailUiState(),
        )
}
