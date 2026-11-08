package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsHttpSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** The value type used by desktop source preference screens. */
enum class SourcePreferenceType {
    Boolean,
    String,
    Int,
    Long,
    Float,
    Select,
    List,
    Unsupported,
}

/** A single option of a [SourcePreferenceType.Select] / [SourcePreferenceType.List] preference. */
data class SourcePreferenceOption(
    val label: String,
    val value: String,
)

/**
 * A single configurable field exposed by a source. Values are persisted through
 * [DesktopPreferenceStore] under a key scoped to the source id.
 *
 * [currentValue] is populated when definitions are loaded from the extension host; callers that
 * only have manifest/builtin definitions can leave it null and use [defaultValue].
 */
data class SourcePreferenceDefinition(
    val key: String,
    val title: String,
    val summary: String = "",
    val type: SourcePreferenceType = SourcePreferenceType.Boolean,
    val defaultValue: String = "",
    val options: List<SourcePreferenceOption> = emptyList(),
    val isReadOnly: Boolean = false,
    val currentValue: String? = null,
)

/** Result of [DesktopSourceManager.getSourcePreferencesSnapshot]. */
data class SourcePreferencesSnapshot(
    val definitions: List<SourcePreferenceDefinition> = emptyList(),
    val supported: Boolean = false,
)

/** Encodes a multi-select value for the desktop preference store / UI callback API. */
fun encodeSourcePreferenceListValue(values: Collection<String>): String = values.joinToString(",")

/** Decodes a multi-select value produced by [encodeSourcePreferenceListValue]. */
fun decodeSourcePreferenceListValue(value: String?): List<String> {
    if (value.isNullOrEmpty()) return emptyList()
    return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Desktop equivalent of Android's `ConfigurableSource`. A builtin source that implements this
 * interface can expose its settings to the desktop source preferences screen.
 *
 * Extension packages can opt into the same UI by declaring `source_preferences` (or
 * `configurable_source`) plus optional `pref:<type>:<key>:<title>` capability entries in their
 * manifest.
 */
interface DesktopConfigurableSource : WindowsCatalogueSource {
    fun getPreferenceDefinitions(): List<SourcePreferenceDefinition>
}

/**
 * A source together with the desktop-managed flags. [DesktopSourceManager.getSources] only returns
 * enabled sources; the details screen uses the richer state list to toggle individual sources.
 */
data class SourceState(
    val source: SourceDescriptor,
    val isEnabled: Boolean = true,
    val isIncognito: Boolean = false,
    val isConfigurable: Boolean = false,
    val extensionPackage: String? = null,
    val isLocal: Boolean = false,
)

class DesktopSourceManager(
    internal val installer: DesktopExtensionInstaller? = null,
    val processManager: WindowsExtensionProcessManager? = null,
    preferenceStore: DesktopPreferenceStore? = null,
    internal val cookieStore: DesktopCookieStore? = null,
    internal var libraryRepository: LibraryRepository? = null,
    internal var localLibraryRepository: LibraryRepository? = null,
    internal var localMangaRepository: LibraryRepository? = null,
    internal var repository: LibraryRepository? = null,
    internal var localRepository: LibraryRepository? = null,
) : Closeable {

    companion object {
        const val PREF_PREFIX_SOURCE_ENABLED = "extension.source.enabled."
        const val PREF_PREFIX_SOURCE_INCOGNITO = "extension.source.incognito."
        const val PREF_PREFIX_EXTENSION_INCOGNITO = "extension.incognito."
        const val PREF_PREFIX_SOURCE_PREFERENCE = "extension.source.preference."

        const val CAPABILITY_SOURCE_PREFERENCES = "source_preferences"
        const val CAPABILITY_CONFIGURABLE_SOURCE = "configurable_source"
        const val CAPABILITY_PREFERENCE_PREFIX = "pref:"

        private val activeManagers = java.util.concurrent.CopyOnWriteArrayList<WeakReference<DesktopSourceManager>>()

        private fun registerActiveManager(manager: DesktopSourceManager) {
            activeManagers.removeAll { it.get() == null || it.get() === manager }
            activeManagers.add(WeakReference(manager))
        }

        private fun unregisterActiveManager(manager: DesktopSourceManager) {
            activeManagers.removeAll { it.get() == null || it.get() === manager }
        }

        /**
         * Finds the live manager responsible for [sourceId].
         *
         * The desktop Compose navigation only forwards definitions/values to
         * `SourcePreferencesScreen`; this registry lets the screen lazily fetch the authoritative
         * extension-host model without changing every navigation call site.
         */
        internal suspend fun releaseInstalledPackage(installer: DesktopExtensionInstaller, pkg: String) {
            activeManagers.mapNotNull { it.get() }.filter { it.installer === installer }.forEach { manager ->
                manager.sourceLoadMutex.withLock {
                    manager.unloadExtensionLocked(pkg)
                }
            }
        }

        internal suspend fun probeInstalledPackage(
            installer: DesktopExtensionInstaller,
            packageFile: java.io.File,
            pkg: String,
        ): List<SourceDescriptor> {
            val manager = activeManagers.mapNotNull { it.get() }
                .lastOrNull { it.installer === installer && it.processManager != null }
                ?: throw IllegalStateException(
                    "An extension host is required to discover original APK source identities",
                )
            return manager.sourceLoadMutex.withLock {
                try {
                    manager.processManager!!.loadExtension(packageFile)
                } finally {
                    manager.unloadExtensionLocked(pkg)
                }
            }
        }

        fun findActiveManagerForSource(sourceId: Long): DesktopSourceManager? {
            val candidates = activeManagers
                .mapNotNull { it.get() }
                .filter { it.ownsSource(sourceId) }
            return candidates.lastOrNull { it.processManager != null } ?: candidates.lastOrNull()
        }
    }

    internal val builtinSources = ConcurrentHashMap<Long, WindowsCatalogueSource>()
    internal val registeredSourcePreferences = ConcurrentHashMap<Long, List<SourcePreferenceDefinition>>()
    internal val preferenceStore: DesktopPreferenceStore? = preferenceStore ?: installer?.preferenceStore

    /** Definitions/current values last returned by the extension host for a source. */
    internal val remoteSourcePreferences = ConcurrentHashMap<Long, List<SourcePreferenceDefinition>>()

    /** Source ids whose extension host response confirmed ConfigurableSource support. */
    internal val remotePreferenceSupport = ConcurrentHashMap.newKeySet<Long>()

    /** Falls back to the runtime cookie store located next to the extension install root. */
    internal val fallbackCookieStore: DesktopCookieStore? by lazy {
        val root = installer?.installRoot?.parentFile ?: return@lazy null
        DesktopCookieStore(root.resolve("cookies.json").toPath())
    }

    internal val activeCookieStore: DesktopCookieStore?
        get() = cookieStore ?: fallbackCookieStore

    internal fun effectiveLibraryRepository(): LibraryRepository? =
        libraryRepository ?: localLibraryRepository ?: localMangaRepository ?: repository ?: localRepository

    /** Built-in local source backed by the imported local manga rows in the desktop library. */
    val localSource: BundledLocalSource = BundledLocalSource { effectiveLibraryRepository() }

    init {
        registerActiveManager(this)
        registerBuiltinSource(localSource)
    }

    internal val loadedPackages = ConcurrentHashMap.newKeySet<String>()
    internal val sourceLoadMutex = Mutex()
    internal var lastKnownEpoch: Long = -1L
    override fun close() {
        unregisterActiveManager(this)
        processManager?.close()
    }
}
