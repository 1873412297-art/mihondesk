package mihon.desktop.extension

import com.sun.jna.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.ImageFilePayload
import mihon.extension.ipc.ImagePayload
import mihon.extension.ipc.IpcCallbackResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcSession
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnloadExtensionPayload
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.decodeSourcePreferenceValue
import mihon.extension.ipc.decodeSourcePreferences
import mihon.extension.ipc.encodeFilterList
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.io.File

enum class HostProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    CRASHED,
}

open class WindowsExtensionProcessManager(
    val workingDirectory: File,
    internal val customCommand: List<String>? = null,
    internal val memoryLimitBytes: Long = 1024L * 1024L * 1024L,
    internal val onBrokerHttp: (suspend (BrokerHttpRequest) -> BrokerHttpResponse)? = null,
    internal val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    internal val sourceSessionFile: File? = null,
    internal val networkHelper: DesktopNetworkHelper? = null,
    internal val onWebView: (
        suspend (
            mihon.extension.ipc.WebViewRequest,
        ) -> mihon.extension.ipc.WebViewResponse
    )? = null,
    internal val isolatedLeaf: Boolean = false,
) : Closeable {

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal val mutex = Mutex()
    internal val routingMutex = Mutex()
    internal val packageHosts = java.util.concurrent.ConcurrentHashMap<String, WindowsExtensionProcessManager>()
    internal val packageFiles = java.util.concurrent.ConcurrentHashMap<String, File>()
    internal val sourceHosts = java.util.concurrent.ConcurrentHashMap<Long, WindowsExtensionProcessManager>()
    internal val routesPackages: Boolean get() = Platform.isWindows() && !isolatedLeaf

    @Volatile
    var state: HostProcessState = HostProcessState.STOPPED
        internal set

    @Volatile
    var lastExitCode: Int? = null
        internal set

    @Volatile
    var epoch: Long = 0L
        internal set

    @Volatile
    internal var closed: Boolean = false

    @Volatile
    internal var process: Process? = null

    @Volatile
    internal var sandboxLauncher: WindowsAppContainerLauncher? = null
    internal val hostWorkingDirectory: File
        get() = extensionHostSandboxDirectory(workingDirectory)

    @Volatile
    internal var jobObject: WindowsJobObject? = null

    @Volatile
    internal var session: IpcSession? = null
    internal val brokerPackages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal val brokerSources = java.util.concurrent.ConcurrentHashMap<Long, String>()

    internal var lastStderr: String = ""
        internal set

    open suspend fun start() = startInternal()

    open suspend fun loadExtension(packageFile: File): List<SourceDescriptor> =
        loadExtensionInternal(packageFile, null)

    open suspend fun loadExtension(
        packageFile: File,
        validatedManifest: ExtensionManifest?,
    ): List<SourceDescriptor> {
        if (validatedManifest == null) {
            return loadExtension(packageFile)
        }
        return loadExtensionInternal(packageFile, validatedManifest)
    }

    open suspend fun unloadExtension(pkg: String) {
        if (routesPackages) {
            routingMutex.withLock {
                packageFiles.remove(pkg)
                packageHosts.remove(pkg)?.let { child ->
                    sourceHosts.entries.removeIf { it.value === child }
                    try {
                        child.unloadExtension(pkg)
                    } finally {
                        child.close()
                    }
                }
            }
            return
        }
        try {
            if (state == HostProcessState.RUNNING) {
                ensureRunning().sendRequest(
                    IpcCommands.UNLOAD_EXTENSION,
                    json.encodeToString(UnloadExtensionPayload(pkg)),
                )
            }
        } finally {
            // Local ownership must be released even when the host is crashed or restarting.
            brokerPackages.remove(pkg)
            brokerSources.entries.removeIf { it.value == pkg }
            networkHelper?.unregisterExtensionDomains(pkg)
        }
    }

    open suspend fun getSources(): List<SourceDescriptor> {
        if (routesPackages) {
            ensureRunningSession()
            val hostsSnapshot = routingMutex.withLock {
                packageHosts.entries.map { (pkg, child) -> Triple(pkg, child, packageFiles[pkg]) }
            }
            return hostsSnapshot.flatMap { (pkg, child, file) ->
                val current = child.getSources()
                val sources = if (current.isNotEmpty()) {
                    current
                } else {
                    file?.let { child.loadExtension(it) }.orEmpty()
                }
                routingMutex.withLock {
                    if (packageHosts[pkg] === child) {
                        registerRoutes(child, sources)
                    }
                }
                sources
            }
        }
        val session = ensureRunningSession()
        val res = session.sendRequest(IpcCommands.GET_SOURCES)
        return json.decodeFromString(res)
    }

    open suspend fun getPopular(sourceId: Long, page: Int): MangasPage {
        routedHost(sourceId)?.let { return it.getPopular(sourceId, page) }
        val session = ensureRunningSession()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_POPULAR, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getLatest(sourceId: Long, page: Int): MangasPage {
        routedHost(sourceId)?.let { return it.getLatest(sourceId, page) }
        val session = ensureRunningSession()
        val payload = GetPagePayload(sourceId, page)
        val res = session.sendRequest(IpcCommands.GET_LATEST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun searchManga(sourceId: Long, page: Int, query: String): MangasPage {
        return searchManga(sourceId, page, query, FilterList())
    }

    open suspend fun searchManga(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        routedHost(sourceId)?.let { return it.searchManga(sourceId, page, query, filters) }
        val session = ensureRunningSession()
        val payload = SearchPayload(
            sourceId = sourceId,
            page = page,
            query = query,
            // Keep the legacy empty payload for the common "no filters" case.
            filtersJson = if (filters.isEmpty()) "" else encodeFilterList(filters),
        )
        val res = session.sendRequest(IpcCommands.SEARCH_MANGA, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga {
        routedHost(sourceId)?.let { return it.getMangaDetails(sourceId, manga) }
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_MANGA_DETAILS, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> {
        routedHost(sourceId)?.let { return it.getChapterList(sourceId, manga) }
        val session = ensureRunningSession()
        val payload = MangaPayload(sourceId, json.encodeToString(manga))
        val res = session.sendRequest(IpcCommands.GET_CHAPTER_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> {
        routedHost(sourceId)?.let { return it.getPageList(sourceId, chapter) }
        val session = ensureRunningSession()
        val payload = ChapterPayload(sourceId, json.encodeToString(chapter))
        val res = session.sendRequest(IpcCommands.GET_PAGE_LIST, json.encodeToString(payload))
        return json.decodeFromString(res)
    }

    open suspend fun getImage(sourceId: Long, page: Page): ByteArray? {
        routedHost(sourceId)?.let { return it.getImage(sourceId, page) }
        val ipc = ensureRunningSession()
        // Page URLs can use a CDN discovered at runtime, including pages restored from a saved queue.
        // Register it for this loaded extension before its image pipeline calls the HTTP broker.
        brokerSources[sourceId]?.let { owner ->
            val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() } ?: page.url
            networkHelper?.registerRuntimePageUrl(imageUrl, extensionId = owner)
        }
        val response = ipc.sendRequest(
            IpcCommands.GET_IMAGE,
            json.encodeToString(ImagePayload(sourceId, page)),
            timeoutMillis = 120_000L,
        )
        val name = json.decodeFromString<ImageFilePayload>(response).fileName ?: return null
        require(name.matches(PAGE_IMAGE_REGEX)) { "Invalid image transfer filename" }
        val file = File(hostWorkingDirectory, "page-images/$name")
        try {
            require(file.length() <= 64L * 1024 * 1024) { "Source image exceeds 64 MiB" }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    open suspend fun getFilterList(sourceId: Long): FilterList {
        routedHost(sourceId)?.let { return it.getFilterList(sourceId) }
        val session = ensureRunningSession()
        val payload = SourcePayload(sourceId)
        val res = session.sendRequest(IpcCommands.GET_FILTER_LIST, json.encodeToString(payload))
        return decodeFilterList(res)
    }

    /**
     * Full source preference response, including the `supported` flag that lets the desktop app
     * distinguish "source has no settings" from "source does not implement ConfigurableSource".
     */
    open suspend fun getSourcePreferencesResult(sourceId: Long): SourcePreferencesDto {
        routedHost(sourceId)?.let { return it.getSourcePreferencesResult(sourceId) }
        val session = ensureRunningSession()
        val payload = SourcePayload(sourceId)
        val res = session.sendRequest(IpcCommands.GET_SOURCE_PREFERENCES, json.encodeToString(payload))
        return decodeSourcePreferences(res) ?: SourcePreferencesDto(sourceId = sourceId, supported = false)
    }

    /** Definitions (including current values) exposed by a source's ConfigurableSource. */
    open suspend fun getSourcePreferences(sourceId: Long): List<SourcePreferenceDefinitionDto> =
        getSourcePreferencesResult(sourceId).definitions

    /** Sets a typed source preference and returns the value reported by the host. */
    open suspend fun setSourcePreference(
        sourceId: Long,
        key: String,
        value: SourcePreferenceValueDto,
    ): SourcePreferenceValueDto? {
        routedHost(sourceId)?.let { return it.setSourcePreference(sourceId, key, value) }
        val session = ensureRunningSession()
        val payload = SetSourcePreferencePayload(sourceId = sourceId, key = key, value = value)
        val res = session.sendRequest(IpcCommands.SET_SOURCE_PREFERENCE, json.encodeToString(payload))
        return decodeSourcePreferenceValue(res)
    }

    /** Convenience overload for callers that only have the raw UI string. */
    open suspend fun setSourcePreference(sourceId: Long, key: String, value: String) {
        setSourcePreference(sourceId, key, StringPreferenceValueDto(value))
    }

    override fun close() {
        closed = true
        closeInternal()
        state = HostProcessState.STOPPED
        scope.cancel()
    }

    companion object {
        internal val PAGE_IMAGE_REGEX = Regex("^page-[a-zA-Z0-9-]+\\.img$")
        internal val BROKER_RESPONSE_REGEX = Regex("^broker-[a-zA-Z0-9-]+\\.bin$")
    }
}
