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
import mihon.desktop.extension.attachLibraryRepository
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
    internal val sourceManager: DesktopSourceManager,
    internal val installer: DesktopExtensionInstaller,
    internal val storeService: ExtensionStoreService,
    internal val libraryRepository: SqlDelightLibraryRepository,
    internal val preferenceStore: DesktopPreferenceStore,
    internal val scope: CoroutineScope,
    internal val onExtensionUpdatesAvailable: (Int) -> Unit = {},
    internal val stringsProvider: () -> DesktopStrings = { DesktopStrings.resolve(preferenceStore.load().language) },
    internal val iconFetcher: (suspend (String) -> ByteArray?)? = null,
    internal val activityCenter: mihon.desktop.activity.ActivityCenter? = null,
) {
    internal var iconBackfillStarted = false

    companion object {
        const val PREF_KEY_PINNED_SOURCES = "browse.pinned_sources"
    }

    internal val json = Json { ignoreUnknownKeys = true }
    internal val stateUpdates = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = stateUpdates.asStateFlow()
    internal val snackbarUpdates = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents: SharedFlow<String> = snackbarUpdates.asSharedFlow()

    internal var lastNotifiedPendingUpdates = 0
    internal var installJob: Job? = null
    internal var globalSearchJob: Job? = null
    internal val globalSearchGeneration = java.util.concurrent.atomic.AtomicLong()
    internal var batchMigrationJob: Job? = null

    val batchMigrationRunner = BatchMigrationRunner(
        scope = scope,
        onSnackbar = { msg -> snackbarUpdates.emit(msg) },
        stringsProvider = stringsProvider,
        onStateChanged = { batchState ->
            stateUpdates.update { it.copy(batchMigrationState = batchState) }
        },
    )

    init {
        // The runtime builds the source manager before the browse presenter, so the built-in local
        // source is wired to the library repository as soon as the Browse tab is created.
        sourceManager.attachLibraryRepository(libraryRepository)
        this.loadPreferences()
        this.refresh()
    }

    internal fun loadPreferences() {
        val prefs = preferenceStore.load()
        val stored = preferenceStore.property(PREF_KEY_PINNED_SOURCES) ?: ""
        val ids = stored.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
        stateUpdates.update {
            it.copy(
                pinnedSourceIds = ids,
                hiddenSourceIds = prefs.hiddenSourceIds,
                showNsfw = prefs.showNsfwSources,
                globalSearchOnlyPinned = prefs.globalSearchOnlyPinned,
            )
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
