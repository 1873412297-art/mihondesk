package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.ExtensionStoreUnavailableException
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.isStoreCandidateInstallable
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.ui.browse.migration.BatchMigrationRunner
import mihon.desktop.ui.browse.migration.BatchMigrationState
import mihon.desktop.ui.browse.migration.MigrationMatcher
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga
import java.io.File

class BrowsePresenter(
    private val sourceManager: DesktopSourceManager,
    private val installer: DesktopExtensionInstaller,
    private val storeService: ExtensionStoreService,
    private val libraryRepository: SqlDelightLibraryRepository,
    private val preferenceStore: DesktopPreferenceStore,
    private val scope: CoroutineScope,
    private val onExtensionUpdatesAvailable: (Int) -> Unit = {},
    private val stringsProvider: () -> DesktopStrings = { DesktopStrings.resolve(preferenceStore.load().language) },
) {
    companion object {
        const val PREF_KEY_PINNED_SOURCES = "browse.pinned_sources"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var lastNotifiedPendingUpdates = 0
    private var installJob: Job? = null
    private var globalSearchJob: Job? = null
    private val globalSearchGeneration = java.util.concurrent.atomic.AtomicLong()
    private var batchMigrationJob: Job? = null

    val batchMigrationRunner = BatchMigrationRunner(
        scope = scope,
        onSnackbar = { msg -> _snackbarEvents.emit(msg) },
        stringsProvider = stringsProvider,
        onStateChanged = { batchState ->
            _state.update { it.copy(batchMigrationState = batchState) }
        },
    )

    init {
        // The runtime builds the source manager before the browse presenter, so the built-in local
        // source is wired to the library repository as soon as the Browse tab is created.
        sourceManager.attachLibraryRepository(libraryRepository)
        loadPreferences()
        refresh()
    }

    private fun loadPreferences() {
        val prefs = preferenceStore.load()
        val stored = preferenceStore.property(PREF_KEY_PINNED_SOURCES) ?: ""
        val ids = stored.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
        _state.update {
            it.copy(
                pinnedSourceIds = ids,
                hiddenSourceIds = prefs.hiddenSourceIds,
                showNsfw = prefs.showNsfwSources,
                globalSearchOnlyPinned = prefs.globalSearchOnlyPinned,
            )
        }
    }

    fun reloadPreferences() {
        val prefs = preferenceStore.load()
        val stored = preferenceStore.property(PREF_KEY_PINNED_SOURCES) ?: ""
        val ids = stored.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
        _state.update {
            it.copy(
                pinnedSourceIds = ids,
                hiddenSourceIds = prefs.hiddenSourceIds,
                showNsfw = prefs.showNsfwSources,
                globalSearchOnlyPinned = prefs.globalSearchOnlyPinned,
            )
        }
    }

    fun hideSource(sourceId: Long) {
        val updated = _state.value.hiddenSourceIds + sourceId
        preferenceStore.updatePreferences { it.copy(hiddenSourceIds = updated) }
        _state.update { it.copy(hiddenSourceIds = updated) }
        val sourceName = _state.value.sources.find { it.id == sourceId }?.name ?: "Source"
        scope.launch {
            _snackbarEvents.emit("HIDDEN:$sourceId:$sourceName")
        }
    }

    fun unhideSource(sourceId: Long) {
        val updated = _state.value.hiddenSourceIds - sourceId
        preferenceStore.updatePreferences { it.copy(hiddenSourceIds = updated) }
        _state.update { it.copy(hiddenSourceIds = updated) }
    }

    fun resetHiddenSources() {
        preferenceStore.updatePreferences { it.copy(hiddenSourceIds = emptySet()) }
        _state.update { it.copy(hiddenSourceIds = emptySet()) }
    }

    fun setShowNsfw(show: Boolean) {
        preferenceStore.updatePreferences { it.copy(showNsfwSources = show) }
        _state.update { it.copy(showNsfw = show) }
    }

    fun setGlobalSearchOnlyPinned(onlyPinned: Boolean) {
        preferenceStore.updatePreferences { it.copy(globalSearchOnlyPinned = onlyPinned) }
        _state.update { it.copy(globalSearchOnlyPinned = onlyPinned) }
        if (_state.value.isGlobalSearchOpen && _state.value.globalSearchQuery.isNotBlank()) {
            performGlobalSearch()
        }
    }

    fun setSourceLanguageFilter(lang: String?) {
        _state.update { it.copy(sourceLanguageFilter = lang) }
    }

    private fun savePinnedSources(ids: Set<Long>) {
        preferenceStore.update {
            setProperty(PREF_KEY_PINNED_SOURCES, ids.joinToString(","))
        }
        _state.update { it.copy(pinnedSourceIds = ids) }
    }

    fun togglePinSource(sourceId: Long) {
        val current = _state.value.pinnedSourceIds
        val updated = if (current.contains(sourceId)) {
            current - sourceId
        } else {
            current + sourceId
        }
        savePinnedSources(updated)
    }

    fun setTab(tab: BrowseTab) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == BrowseTab.Migration) {
            refreshMigrationCounts()
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, installFailure = null, errorMessage = null) }
            try {
                val repos = storeService.getRepositories()
                val installed = installer.getInstalledExtensions()
                val snapshot = buildSourceStateSnapshot(installed)

                // Update local state before the network round-trip so installed extensions and
                // sources remain usable when a repository is unreachable.
                _state.update {
                    it.copy(
                        repositories = repos,
                        installedExtensions = installed,
                        sources = snapshot.sources,
                        sourceStates = snapshot.sourceStates,
                        extensionSourceStates = snapshot.extensionSourceStates,
                        incognitoExtensionPackages = snapshot.incognitoExtensionPackages,
                        sourcePreferenceDefinitions = snapshot.sourcePreferenceDefinitions,
                        sourcePreferenceValues = snapshot.sourcePreferenceValues,
                        isLoading = false,
                    )
                }
                refreshMigrationCounts()

                val successfulItems = mutableListOf<ExtensionStoreItem>()
                val repositoryFailures = mutableListOf<String>()
                for (repo in repos) {
                    try {
                        val items = storeService.fetchRepository(repo)
                        successfulItems.addAll(items)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        repositoryFailures.add(msg)
                    }
                }

                val available = mihon.desktop.extension.selectAvailableStoreItems(installed, successfulItems)

                val repoErrorMessage = if (repositoryFailures.isNotEmpty()) {
                    val count = repositoryFailures.size
                    val noun = if (count == 1) "repository" else "repositories"
                    "Failed to refresh $count $noun: ${repositoryFailures.first()}"
                } else {
                    null
                }

                _state.update {
                    it.copy(
                        availableExtensions = available,
                        errorMessage = repoErrorMessage,
                    )
                }
                val pendingUpdates = countPendingExtensionUpdates(installed, available)
                if (pendingUpdates > 0 && pendingUpdates != lastNotifiedPendingUpdates) {
                    onExtensionUpdatesAvailable(pendingUpdates)
                }
                lastNotifiedPendingUpdates = pendingUpdates
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        installFailure = null,
                        errorMessage = "Failed to refresh: ${e.message}",
                    )
                }
            }
        }
    }

    private fun buildSourceStateSnapshot(installed: List<InstalledExtension>): SourceStateSnapshot {
        val sourceStates = sourceManager.getSourceStates()
        val extensionSourceStates = installed.associate { extension ->
            extension.pkg to sourceManager.getSourceStatesForExtension(extension.pkg)
        }
        val allStates = (sourceStates + extensionSourceStates.values.flatten()).distinctBy { it.source.id }

        val definitions = allStates.associate { state ->
            state.source.id to sourceManager.getSourcePreferenceDefinitions(state.source.id)
        }
        val values = definitions.mapValues { (sourceId, definitionsForSource) ->
            definitionsForSource.associate { definition ->
                definition.key to (
                    sourceManager.getSourcePreferenceValue(sourceId, definition.key)
                        ?: definition.defaultValue
                    )
            }
        }

        return SourceStateSnapshot(
            sources = sourceStates.filter { it.isEnabled }.map { it.source },
            sourceStates = sourceStates,
            extensionSourceStates = extensionSourceStates,
            incognitoExtensionPackages = installed
                .filter { sourceManager.isExtensionIncognito(it.pkg) }
                .map { it.pkg }
                .toSet(),
            sourcePreferenceDefinitions = definitions,
            sourcePreferenceValues = values,
        )
    }

    private fun refreshInstalledAndSources() {
        val installed = installer.getInstalledExtensions()
        val snapshot = buildSourceStateSnapshot(installed)
        _state.update {
            it.copy(
                installedExtensions = installed,
                sources = snapshot.sources,
                sourceStates = snapshot.sourceStates,
                extensionSourceStates = snapshot.extensionSourceStates,
                incognitoExtensionPackages = snapshot.incognitoExtensionPackages,
                sourcePreferenceDefinitions = snapshot.sourcePreferenceDefinitions,
                sourcePreferenceValues = snapshot.sourcePreferenceValues,
            )
        }
    }

    private data class SourceStateSnapshot(
        val sources: List<SourceDescriptor>,
        val sourceStates: List<SourceState>,
        val extensionSourceStates: Map<String, List<SourceState>>,
        val incognitoExtensionPackages: Set<String>,
        val sourcePreferenceDefinitions: Map<Long, List<SourcePreferenceDefinition>>,
        val sourcePreferenceValues: Map<Long, Map<String, String>>,
    )

    fun refreshMigrationCounts() {
        scope.launch {
            try {
                val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
                val countsBySource = allManga
                    .groupBy { it.sourceId }
                    .mapValues { it.value.size }

                val sourceList = _state.value.sources
                val sourceMap = sourceList.associateBy { it.id }

                val counts = countsBySource.map { (sourceId, count) ->
                    SourceWithMangaCount(
                        sourceId = sourceId,
                        sourceName = sourceMap[sourceId]?.name ?: "Source #$sourceId",
                        mangaCount = count,
                    )
                }.sortedByDescending { it.mangaCount }

                _state.update { it.copy(sourcesWithMangaCounts = counts) }
            } catch (_: Exception) {}
        }
    }

    fun selectMigrationSource(source: SourceWithMangaCount?) {
        if (source == null) {
            _state.update {
                it.copy(selectedMigrationSource = null, mangasForSelectedMigrationSource = emptyList())
            }
            return
        }

        scope.launch {
            val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
            val mangas = allManga.filter { it.sourceId == source.sourceId }
            _state.update {
                it.copy(
                    selectedMigrationSource = source,
                    mangasForSelectedMigrationSource = mangas,
                )
            }
        }
    }

    suspend fun searchTargetMigrationSource(targetSourceId: Long, query: String): List<SManga> {
        return try {
            val page = sourceManager.searchManga(targetSourceId, 1, query)
            page.mangas
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun autoMatchTargetSource(targetSourceId: Long, manga: LibraryManga): MigrationMatcher.MatchEvaluation {
        return try {
            var mangas = searchTargetMigrationSource(targetSourceId, manga.title)
            if (mangas.isEmpty()) {
                val cleaned = MigrationMatcher.cleanTitle(manga.title)
                if (cleaned != manga.title && cleaned.isNotBlank()) {
                    mangas = searchTargetMigrationSource(targetSourceId, cleaned)
                }
            }
            MigrationMatcher.evaluateCandidates(manga.title, mangas)
        } catch (_: Exception) {
            MigrationMatcher.MatchEvaluation(emptyList(), null, false)
        }
    }

    /**
     * Loads the filter list for a source when the source browse screen is opened. Builtin sources
     * resolve immediately; extension sources are loaded through the extension host and decoded from
     * the IPC filter DTOs.
     */
    suspend fun loadSourceFilters(sourceId: Long): FilterList {
        return sourceManager.loadFilterList(sourceId)
    }

    fun performMigration(
        oldManga: LibraryManga,
        targetSource: SourceDescriptor,
        targetManga: SManga,
    ) {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val strings = stringsProvider()
            try {
                performMigrationInternal(oldManga, targetSource, targetManga)
                _snackbarEvents.emit(strings.migrateSuccessSnackbar(oldManga.title, targetSource.name))
                refresh()
                _state.value.selectedMigrationSource?.let { selectMigrationSource(it) }
                refreshMigrationCounts()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(installFailure = null, errorMessage = "Migration failed: ${e.message}") }
                _snackbarEvents.emit(strings.migrateFailedSnackbar(oldManga.title, e.message ?: "Unknown error"))
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    internal suspend fun performMigrationInternal(
        oldManga: LibraryManga,
        targetSource: SourceDescriptor,
        targetManga: SManga,
    ): Long = withContext(Dispatchers.IO) {
        val detailedTarget = try {
            sourceManager.getMangaDetails(targetSource.id, targetManga)
        } catch (_: Exception) {
            targetManga
        }

        val targetChapters = try {
            sourceManager.getChapterList(targetSource.id, detailedTarget)
        } catch (_: Exception) {
            emptyList()
        }

        val oldChapters = libraryRepository.chapterSnapshot(oldManga.id)
        val oldDetails = libraryRepository.mangaSnapshot(oldManga.id)
        val oldRecord = libraryRepository.findManga(oldManga.sourceId, oldManga.url)
        val oldTrackings = libraryRepository.trackingSnapshot(oldManga.id)
        val sourceCategoryIds = oldDetails?.categories?.map { it.id }
            ?: libraryRepository.mangaCategoryLinksSnapshot()[oldManga.id].orEmpty()

        val existingTarget = libraryRepository.findManga(targetSource.id, detailedTarget.url)
        val existingTargetChapters = if (existingTarget != null) {
            libraryRepository.chapterSnapshot(existingTarget.id).associateBy { it.url }
        } else {
            emptyMap()
        }
        val existingTargetCategoryIds = if (existingTarget != null) {
            libraryRepository.mangaSnapshot(existingTarget.id)?.categories?.map { it.id }?.toSet().orEmpty()
        } else {
            emptySet()
        }

        val now = System.currentTimeMillis()

        libraryRepository.transaction {
            val newMangaId = if (existingTarget == null) {
                insertManga(
                    MangaRecord(
                        id = 0L,
                        sourceId = targetSource.id,
                        url = detailedTarget.url,
                        title = detailedTarget.title,
                        artist = detailedTarget.artist,
                        author = detailedTarget.author,
                        description = detailedTarget.description,
                        genreJson = json.encodeToString(detailedTarget.genre),
                        status = detailedTarget.status.toLong(),
                        thumbnailUrl = detailedTarget.thumbnailUrl,
                        favorite = true,
                        dateAdded = oldDetails?.dateAdded ?: now,
                        lastModifiedAt = now,
                        favoriteModifiedAt = now,
                        initialized = true,
                        viewerFlags = oldDetails?.viewerFlags ?: 0L,
                        chapterFlags = oldDetails?.chapterFlags ?: 0L,
                        memoJson = oldDetails?.memoJson ?: "{}",
                    ),
                )
            } else {
                val updatedViewerFlags = if (existingTarget.viewerFlags !=
                    0L
                ) {
                    existingTarget.viewerFlags
                } else {
                    (oldDetails?.viewerFlags ?: 0L)
                }
                val updatedChapterFlags = if (existingTarget.chapterFlags !=
                    0L
                ) {
                    existingTarget.chapterFlags
                } else {
                    (oldDetails?.chapterFlags ?: 0L)
                }
                val updatedMemoJson = if (existingTarget.memoJson.isNotBlank() &&
                    existingTarget.memoJson != "{}"
                ) {
                    existingTarget.memoJson
                } else {
                    (oldDetails?.memoJson ?: "{}")
                }
                updateManga(
                    existingTarget.copy(
                        favorite = true,
                        favoriteModifiedAt = now,
                        lastModifiedAt = now,
                        viewerFlags = updatedViewerFlags,
                        chapterFlags = updatedChapterFlags,
                        memoJson = updatedMemoJson,
                    ),
                )
                existingTarget.id
            }

            val newChapterRecords = targetChapters.mapIndexed { index, sc ->
                val match = oldChapters.find { oc ->
                    (sc.chapterNumber > 0f && oc.chapterNumber == sc.chapterNumber.toDouble()) ||
                        oc.name.trim().equals(sc.name.trim(), ignoreCase = true)
                }

                val existingChapter = existingTargetChapters[sc.url]
                ChapterRecord(
                    id = existingChapter?.id ?: 0L,
                    mangaId = newMangaId,
                    url = sc.url,
                    name = sc.name,
                    scanlator = sc.scanlator,
                    read = (existingChapter?.read == true) || (match?.read == true),
                    bookmark = (existingChapter?.bookmark == true) || (match?.bookmark == true),
                    lastPageRead = maxOf(existingChapter?.lastPageRead ?: 0L, match?.lastPageRead ?: 0L),
                    chapterNumber = sc.chapterNumber.toDouble(),
                    sourceOrder = index.toLong(),
                    dateFetch = now,
                    dateUpload = sc.dateUpload,
                    lastModifiedAt = now,
                )
            }

            for (record in newChapterRecords) {
                if (record.id == 0L) {
                    insertChapter(record)
                } else {
                    updateChapter(record)
                }
            }

            for (categoryId in sourceCategoryIds) {
                if (categoryId !in existingTargetCategoryIds) {
                    linkCategory(newMangaId, categoryId)
                }
            }

            for (tracking in oldTrackings) {
                val existingTracking = findTracking(newMangaId, tracking.trackerId)
                if (existingTracking == null) {
                    insertTracking(tracking.copy(id = 0L, mangaId = newMangaId))
                } else {
                    updateTracking(
                        existingTracking.copy(
                            lastChapterRead = maxOf(existingTracking.lastChapterRead, tracking.lastChapterRead),
                            score = if (existingTracking.score != 0.0) existingTracking.score else tracking.score,
                            status = if (existingTracking.status != 0L) existingTracking.status else tracking.status,
                            remoteId = if (existingTracking.remoteId !=
                                0L
                            ) {
                                existingTracking.remoteId
                            } else {
                                tracking.remoteId
                            },
                        ),
                    )
                }
                deleteTracking(oldManga.id, tracking.trackerId)
            }

            if (oldRecord != null) {
                updateManga(
                    oldRecord.copy(
                        favorite = false,
                        lastModifiedAt = now,
                        favoriteModifiedAt = now,
                    ),
                )
            }

            newMangaId
        }
    }

    fun startBatchMigration(targetSource: SourceDescriptor, delayMs: Long = 500L): Job? {
        val mangas = _state.value.mangasForSelectedMigrationSource
        if (mangas.isEmpty()) return null

        batchMigrationJob?.cancel()
        val job = batchMigrationRunner.start(
            mangas = mangas,
            targetSource = targetSource,
            searchFn = { query -> searchTargetMigrationSource(targetSource.id, query) },
            migrateFn = { oldManga, targetManga ->
                performMigrationInternal(oldManga, targetSource, targetManga)
            },
            delayMs = delayMs,
            onComplete = {
                refresh()
                _state.value.selectedMigrationSource?.let { selectMigrationSource(it) }
                refreshMigrationCounts()
            },
        )
        batchMigrationJob = job
        return job
    }

    fun cancelBatchMigration() {
        batchMigrationRunner.cancel()
        batchMigrationJob?.cancel()
        batchMigrationJob = null
    }

    fun dismissBatchMigrationReport() {
        batchMigrationRunner.reset()
    }

    fun installExtension(item: ExtensionStoreItem) = installItems(listOf(item))

    internal fun installItems(items: List<ExtensionStoreItem>) {
        if (items.isEmpty()) return
        startInstallation(items.first().pkg, items.first().name, ExtensionInstallPhase.Downloading) {
            for (item in items) {
                currentCoroutineContext().ensureActive()
                synchronized(this@BrowsePresenter) {
                    if (installJob?.isActive != true) throw CancellationException("Extension installation cancelled")
                    _state.update {
                        it.copy(
                            installingPkg = item.pkg,
                            installingName = item.name,
                            installPhase = ExtensionInstallPhase.Downloading,
                        )
                    }
                }
                requireStoreInstallAvailable(item)
                installer.downloadAndInstall(
                    item.downloadUrl,
                    item.sha256,
                    item.repoUrl,
                    storeItem = item,
                    onDownloadComplete = ::beginInstallation,
                )
                refreshInstalledAndSources()
            }
        }
    }

    fun installFromFile(file: File) {
        startInstallation(file.name, file.name, ExtensionInstallPhase.Installing) {
            installer.installFromLocalFile(file)
            refreshInstalledAndSources()
        }
    }

    /**
     * Blocks a store candidate that the configured repositories no longer offer, so the user learns
     * that the listing is gone instead of seeing a failed download.
     */
    private fun requireStoreInstallAvailable(item: ExtensionStoreItem) {
        if (
            isStoreCandidateInstallable(
                item = item,
                repositories = _state.value.repositories,
                available = _state.value.availableExtensions,
            )
        ) {
            return
        }
        throw ExtensionStoreUnavailableException(item.pkg, item.version)
    }

    @Synchronized
    private fun beginInstallation() {
        if (installJob?.isActive != true || _state.value.installPhase != ExtensionInstallPhase.Downloading) {
            throw CancellationException("Extension download was cancelled")
        }
        _state.update { it.copy(installPhase = ExtensionInstallPhase.Installing) }
    }

    @Synchronized
    fun cancelInstallation() {
        if (_state.value.installPhase != ExtensionInstallPhase.Downloading) return
        _state.update { it.copy(installPhase = ExtensionInstallPhase.Cancelling) }
        installJob?.cancel()
    }

    @Synchronized
    private fun startInstallation(pkg: String, name: String, phase: ExtensionInstallPhase, action: suspend () -> Unit) {
        if (installJob?.isCompleted == false || !scope.isActive) return
        _state.update {
            it.copy(
                isInstalling = true,
                installingPkg = pkg,
                installingName = name,
                installPhase = phase,
                installationCancelled = false,
                installFailure = null,
                errorMessage = null,
            )
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val reason = classifyInstallFailure(failure)
                _state.update {
                    it.copy(
                        errorMessage = "Failed to install ${it.installingName}: ${failure.message}",
                        installFailure = reason,
                    )
                }
            }
        }
        installJob = job
        job.invokeOnCompletion { cause ->
            synchronized(this@BrowsePresenter) {
                if (installJob === job) {
                    installJob = null
                    _state.update {
                        it.copy(
                            isInstalling = false,
                            installingPkg = null,
                            installPhase = null,
                            installationCancelled = cause is CancellationException,
                        )
                    }
                }
            }
        }
        job.start()
    }

    fun uninstallExtension(pkg: String) {
        scope.launch {
            try {
                installer.uninstall(pkg)
                sourceManager.unloadExtension(pkg)
                refreshInstalledAndSources()
            } catch (e: Exception) {
                _state.update {
                    it.copy(installFailure = null, errorMessage = "Failed to uninstall $pkg: ${e.message}")
                }
            }
        }
    }

    fun toggleExtensionEnabled(pkg: String, enabled: Boolean) {
        scope.launch {
            try {
                installer.setExtensionEnabled(pkg, enabled)
                if (!enabled) {
                    sourceManager.unloadExtension(pkg)
                }
                refreshInstalledAndSources()
            } catch (e: Exception) {
                _state.update { it.copy(installFailure = null, errorMessage = "Failed to toggle $pkg: ${e.message}") }
            }
        }
    }

    fun setSourceEnabled(sourceId: Long, enabled: Boolean) {
        sourceManager.setSourceEnabled(sourceId, enabled)
        refreshInstalledAndSources()
    }

    fun toggleSourceEnabled(sourceId: Long) {
        setSourceEnabled(sourceId, !sourceManager.isSourceEnabled(sourceId))
    }

    fun setSourceIncognito(sourceId: Long, incognito: Boolean) {
        sourceManager.setSourceIncognito(sourceId, incognito)
        refreshInstalledAndSources()
    }

    fun toggleSourceIncognito(sourceId: Long) {
        setSourceIncognito(sourceId, !sourceManager.isSourceIncognito(sourceId))
    }

    fun setExtensionIncognito(pkg: String, incognito: Boolean) {
        sourceManager.setExtensionIncognito(pkg, incognito)
        _state.update { current ->
            val updated = if (incognito) {
                current.incognitoExtensionPackages + pkg
            } else {
                current.incognitoExtensionPackages - pkg
            }
            current.copy(incognitoExtensionPackages = updated)
        }
    }

    fun toggleExtensionIncognito(pkg: String) {
        setExtensionIncognito(pkg, !sourceManager.isExtensionIncognito(pkg))
    }

    /** Android-style alias for toggling an extension-level incognito flag. */
    fun toggleIncognito(pkg: String) = toggleExtensionIncognito(pkg)

    /** Android-style alias for toggling a source's enabled flag. */
    fun toggleSource(sourceId: Long) = toggleSourceEnabled(sourceId)

    fun clearExtensionCookies(pkg: String) {
        sourceManager.clearExtensionCookies(pkg)
    }

    fun clearSourceCookies(sourceId: Long) {
        sourceManager.clearSourceCookies(sourceId)
    }

    fun clearCookies(pkg: String) = clearExtensionCookies(pkg)

    fun clearCookies(sourceId: Long) = clearSourceCookies(sourceId)

    fun setSourcePreferenceValue(sourceId: Long, key: String, value: String) {
        sourceManager.setSourcePreferenceValue(sourceId, key, value)
        _state.update { current ->
            val updatedValues = current.sourcePreferenceValues[sourceId].orEmpty().toMutableMap()
            updatedValues[key] = value
            current.copy(
                sourcePreferenceValues = current.sourcePreferenceValues + (sourceId to updatedValues),
            )
        }
    }

    fun addRepository(repoUrl: String) {
        storeService.addRepository(repoUrl)
        refresh()
    }

    fun removeRepository(repoUrl: String) {
        storeService.removeRepository(repoUrl)
        refresh()
    }

    fun updateAllPending() {
        val installedMap = _state.value.installedExtensions.associateBy { it.pkg }
        val toUpdate = _state.value.availableExtensions.filter { available ->
            val inst = installedMap[available.pkg]
            inst != null && available.versionCode > inst.manifest.versionCode
        }

        installItems(toUpdate)
    }

    fun openGlobalSearch() {
        _state.update {
            it.copy(
                isGlobalSearchOpen = true,
                globalSearchQuery = it.searchQuery,
            )
        }
        if (_state.value.searchQuery.isNotBlank()) {
            performGlobalSearch()
        }
    }

    fun closeGlobalSearch() {
        globalSearchGeneration.incrementAndGet()
        globalSearchJob?.cancel()
        _state.update {
            it.copy(
                isGlobalSearchOpen = false,
                isGlobalSearching = false,
                globalSearchResults = emptyList(),
            )
        }
    }

    fun setGlobalSearchQuery(query: String) {
        _state.update { it.copy(globalSearchQuery = query) }
    }

    fun performGlobalSearch() {
        val query = _state.value.globalSearchQuery.trim()
        if (query.isBlank()) return
        val generation = globalSearchGeneration.incrementAndGet()
        globalSearchJob?.cancel()

        val showNsfw = _state.value.showNsfw
        val hiddenIds = _state.value.hiddenSourceIds
        val onlyPinned = _state.value.globalSearchOnlyPinned
        val pinnedIds = _state.value.pinnedSourceIds
        val installed = _state.value.installedExtensions
        val available = _state.value.availableExtensions

        val sources = _state.value.sources.filter { source ->
            if (hiddenIds.contains(source.id)) return@filter false
            if (onlyPinned && !pinnedIds.contains(source.id)) return@filter false
            if (!showNsfw && isSourceNsfw(source.id, installed, available)) return@filter false
            true
        }

        _state.update {
            it.copy(
                isGlobalSearching = sources.isNotEmpty(),
                globalSearchResults = sources.map { s -> GlobalSearchSourceResult(source = s, isLoading = true) },
            )
        }

        globalSearchJob = scope.launch {
            coroutineScope {
                for (source in sources) {
                    launch {
                        try {
                            val page = sourceManager.searchManga(source.id, 1, query)
                            currentCoroutineContext().ensureActive()
                            _state.update { current ->
                                if (generation != globalSearchGeneration.get()) return@update current
                                val updated = current.globalSearchResults.map { item ->
                                    if (item.source.id == source.id) {
                                        item.copy(isLoading = false, mangas = page.mangas)
                                    } else {
                                        item
                                    }
                                }
                                val stillLoading = updated.any { it.isLoading }
                                current.copy(
                                    globalSearchResults = updated,
                                    isGlobalSearching = stillLoading,
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _state.update { current ->
                                if (generation != globalSearchGeneration.get()) return@update current
                                val updated = current.globalSearchResults.map { item ->
                                    if (item.source.id == source.id) {
                                        item.copy(
                                            isLoading = false,
                                            errorMessage = e.message ?: "Failed to search",
                                            failureReason = mihon.desktop.download.classifyDownloadFailure(e),
                                        )
                                    } else {
                                        item
                                    }
                                }
                                val stillLoading = updated.any { it.isLoading }
                                current.copy(
                                    globalSearchResults = updated,
                                    isGlobalSearching = stillLoading,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun countPendingExtensionUpdates(
    installed: List<InstalledExtension>,
    available: List<ExtensionStoreItem>,
): Int {
    val installedVersions = installed.associate { extension -> extension.pkg to extension.manifest.versionCode }
    return mihon.desktop.extension.selectAvailableStoreItems(installed, available).count { extension ->
        val installedVersion = installedVersions[extension.pkg]
        installedVersion != null && extension.versionCode > installedVersion
    }
}
