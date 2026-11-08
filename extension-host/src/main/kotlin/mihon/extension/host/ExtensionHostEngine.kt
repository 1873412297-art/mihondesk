package mihon.extension.host

import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.ImageFilePayload
import mihon.extension.ipc.ImagePayload
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.IpcResponse
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.applyStateFrom
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.encodeFilterList
import mihon.extension.ipc.encodeSourcePreferenceValue
import mihon.extension.ipc.encodeSourcePreferences
import mihon.extension.ipc.findNetworkFailure
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsImageSource
import mihon.extension.source.WindowsSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import mihon.extension.validator.ExtensionPackageValidator
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class ExtensionHostEngine(
    val httpClient: BrokeredHttpClient,
) : AutoCloseable {
    internal val sourceRegistryLock = Any()
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal val loadedSources = ConcurrentHashMap<Long, WindowsSource>()
    internal val extensionSources = ConcurrentHashMap<String, Set<Long>>()
    internal val extensionLoaders = ExtensionLoaderRegistry()
    internal val sourcePreferenceModels = ConcurrentHashMap<Long, SourcePreferenceModel>()

    internal val activeSourceJobs = mutableMapOf<String, MutableSet<kotlinx.coroutines.Job>>()
    internal val sourceContexts = ConcurrentHashMap<Long, ExtensionExecutionContext.Identity>()
    internal val closed = java.util.concurrent.atomic.AtomicBoolean()
    internal val preferenceContext: android.content.Context
        get() = ExtensionExecutionContext.currentApplication()
    init {
        if (System.getProperty("http.agent").isNullOrBlank()) {
            System.setProperty("http.agent", eu.kanade.tachiyomi.network.NetworkHelper.DEFAULT_USER_AGENT)
        }
        eu.kanade.tachiyomi.network.NetworkHelper.installBroker(httpClient)
        mihon.extension.host.WebViewBridge.install(httpClient::webView)
        bootstrapInjekt()
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun bootstrapInjekt() {
        Injekt.addFactory<android.content.Context> { ExtensionExecutionContext.currentApplication() }
        Injekt.addFactory<android.app.Application> { ExtensionExecutionContext.currentApplication() }
        registerInjektSingleton { eu.kanade.tachiyomi.network.NetworkHelper() }

        registerInjektSingleton {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            }
        }
        registerInjektSingleton<kotlinx.serialization.protobuf.ProtoBuf> {
            kotlinx.serialization.protobuf.ProtoBuf
        }
    }

    fun registerSource(source: WindowsSource) {
        synchronized(sourceRegistryLock) {
            loadedSources[source.id] = source
            sourcePreferenceModels.remove(source.id)
        }
    }

    fun loadExtension(packageFile: File, workingDir: File): List<SourceDescriptor> {
        val manifest = ExtensionPackageValidator.validatePackage(packageFile)
        // Reloading a package must not expose classes/assets removed from the new version.
        extensionLoaders.closeAndRemove(manifest.id)
        workingDir.mkdirs()
        // android-data holds the extension's persisted preferences/context and must survive reloads.
        workingDir.listFiles()?.filterNot { it.name == "android-data" }?.forEach { old ->
            runCatching { old.deleteRecursively() }
                .onFailure { System.err.println("Cannot clear stale extension file ${old.name}: ${it.message}") }
        }

        // Unpack classes / jars from .mext
        val extractedJars = mutableListOf<File>()
        ZipFile(packageFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory &&
                    (entry.name.endsWith(".jar") || entry.name.endsWith(".class") || entry.name.startsWith("assets/"))
                ) {
                    val dest = extensionEntryFile(workingDir, entry.name)
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (entry.name.endsWith(".jar")) {
                        extractedJars.add(dest)
                    }
                }
            }
        }

        val urls = (extractedJars.map { it.toURI().toURL() } + listOf(workingDir.toURI().toURL())).toTypedArray()
        val loader = ExtensionClassLoader(urls)
        try {
            // Suwayomi loads an extension's main class once and lets SourceFactory provide the
            // authoritative source instances. A repository manifest can contain one descriptor per
            // language even though every descriptor points to that same factory class.
            val application = android.app.Application(
                File(workingDir, "android-data"),
                manifest.id,
                File(workingDir, "assets"),
            )
            val identity = ExtensionExecutionContext.Identity(manifest.id, null, application)
            val instantiatedSources = ExtensionExecutionContext.duringConstruction(identity) {
                manifest.sources
                    .distinctBy { it.className }
                    .flatMap { descriptor -> loader.instantiateSources(descriptor.className, httpClient) }
                    .distinctBy { it.id }
            }

            val newSources = linkedMapOf<Long, WindowsSource>()
            instantiatedSources.forEach { source -> newSources[source.id] = source }
            val loadedDescriptors = newSources.map { (registeredId, source) ->
                SourceDescriptor(
                    registeredId,
                    source.name,
                    source.lang,
                    source::class.java.name,
                    source.supportsLatest,
                    sourceBaseUrl(source),
                )
            }

            synchronized(sourceRegistryLock) {
                activeSourceJobs.remove(manifest.id)?.forEach { it.cancel() }
                val previousSourceIds = extensionSources.remove(manifest.id).orEmpty()
                discardSources(previousSourceIds)
                loadedSources.putAll(newSources)
                newSources.keys.forEach { sourceContexts[it] = identity.copy(sourceId = it) }
                extensionSources[manifest.id] = newSources.keys
                extensionLoaders.replace(manifest.id, loader)
            }
            return if (loadedDescriptors.isNotEmpty()) loadedDescriptors else manifest.sources
        } catch (failure: Throwable) {
            runCatching { loader.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun discardSources(sourceIds: Set<Long>) {
        sourceIds.forEach {
            loadedSources.remove(it)
            sourcePreferenceModels.remove(it)
            sourceContexts.remove(it)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(sourceRegistryLock) {
            activeSourceJobs.values.flatten().forEach { it.cancel() }
            activeSourceJobs.clear()
            discardSources(extensionSources.values.flatten().toSet())
            extensionSources.clear()
            sourcePreferenceModels.clear()
            extensionLoaders.close()
        }
    }

    internal fun getCatalogueSource(sourceId: Long): WindowsCatalogueSource {
        val source = synchronized(sourceRegistryLock) { loadedSources[sourceId] }
            ?: throw IllegalArgumentException("Source with ID $sourceId not found")
        return source as? WindowsCatalogueSource
            ?: throw IllegalArgumentException("Source $sourceId does not implement WindowsCatalogueSource")
    }

    internal fun sourceBaseUrl(source: WindowsSource): String? = runCatching {
        when (source) {
            is mihon.extension.source.WindowsHttpSource -> source.baseUrl
            is TachiyomiCatalogueSourceAdapter -> (source.delegate as? HttpSource)?.baseUrl
            else -> null
        }
    }.getOrNull()

    fun unloadExtension(pkg: String) {
        synchronized(sourceRegistryLock) {
            activeSourceJobs.remove(pkg)?.forEach { it.cancel() }
            discardSources(extensionSources.remove(pkg).orEmpty())
            extensionLoaders.closeAndRemove(pkg)
        }
    }

    internal inline fun <reified T> decodePayload(request: IpcRequest, element: JsonElement?): T {
        return if (element != null) {
            json.decodeFromJsonElement<T>(element)
        } else {
            json.decodeFromString<T>(request.payloadJson)
        }
    }

    suspend fun handleRequest(request: IpcRequest): IpcResponse {
        val payloadElement = runCatching {
            json.parseToJsonElement(request.payloadJson)
        }.getOrNull()
        val sourceId = runCatching {
            (payloadElement as? JsonObject)?.get("sourceId")?.jsonPrimitive?.content?.toLongOrNull()
        }.getOrNull()
        val identity = sourceId?.let { sourceContexts[it] }
        if (identity == null) return handleBoundRequest(request, payloadElement)
        return coroutineScope {
            val job = currentCoroutineContext().job
            val pkg = requireNotNull(identity.packageId)
            synchronized(sourceRegistryLock) {
                check(sourceContexts[sourceId] === identity) { "Source unloaded before request started" }
                activeSourceJobs.getOrPut(pkg) { mutableSetOf() }.add(job)
            }
            try {
                ExtensionExecutionContext.withIdentity(identity.copy(priority = request.priority)) {
                    handleBoundRequest(request, payloadElement)
                }
            } finally {
                synchronized(sourceRegistryLock) {
                    activeSourceJobs[pkg]?.let { jobs ->
                        jobs.remove(job)
                        if (jobs.isEmpty()) activeSourceJobs.remove(pkg)
                    }
                }
            }
        }
    }
}
