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

internal suspend fun ExtensionHostEngine.handleBoundRequest(
    request: IpcRequest,
    payloadElement: JsonElement? = null,
): IpcResponse {
    return try {
        check(!closed.get()) { "Extension host engine is closed" }
        when (request.command) {
            IpcCommands.PING -> IpcResponse(request.requestId, success = true, payloadJson = "pong")

            IpcCommands.LOAD_EXTENSION -> {
                val payload = decodePayload<LoadExtensionPayload>(request, payloadElement)
                val sources = loadExtension(File(payload.packagePath), File(payload.workingDir))
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(sources))
            }

            IpcCommands.UNLOAD_EXTENSION -> {
                val payload = decodePayload<mihon.extension.ipc.UnloadExtensionPayload>(request, payloadElement)
                unloadExtension(payload.pkg)
                IpcResponse(request.requestId, success = true)
            }

            IpcCommands.GET_SOURCES -> {
                val descriptors = synchronized(sourceRegistryLock) {
                    loadedSources.map { (registeredId, source) ->
                        SourceDescriptor(
                            registeredId,
                            source.name,
                            source.lang,
                            source::class.java.name,
                            source.supportsLatest,
                            sourceBaseUrl(source),
                        )
                    }
                }
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(descriptors))
            }

            IpcCommands.GET_POPULAR -> {
                val payload = decodePayload<GetPagePayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val mangasPage = catalogue.getPopularManga(payload.page)
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
            }

            IpcCommands.GET_LATEST -> {
                val payload = decodePayload<GetPagePayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val mangasPage = catalogue.getLatestUpdates(payload.page)
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
            }

            IpcCommands.SEARCH_MANGA -> {
                val payload = decodePayload<SearchPayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val requestedFilters = decodeFilterList(payload.filtersJson)
                val mangasPage = if (catalogue is TachiyomiCatalogueSourceAdapter) {
                    catalogue.searchMangaWithFilters(payload.page, payload.query, requestedFilters)
                } else {
                    // Empty payloads keep the legacy "no filters" behavior. Non-empty payloads
                    // are applied to the source's own filter instances so custom subclasses
                    // (and any behavior they carry) survive the round trip. If the source
                    // exposes no filters, use the decoded payload as-is.
                    val sourceFilters = catalogue.getFilterList()
                    val filters = when {
                        requestedFilters.isEmpty() -> FilterList()
                        sourceFilters.isEmpty() -> requestedFilters
                        else -> sourceFilters.applyStateFrom(requestedFilters)
                    }
                    catalogue.searchManga(payload.page, payload.query, filters)
                }
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
            }

            IpcCommands.GET_MANGA_DETAILS -> {
                val payload = decodePayload<MangaPayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val manga = json.decodeFromString<SManga>(payload.mangaJson)
                val details = catalogue.getMangaDetails(manga)
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(details))
            }

            IpcCommands.GET_CHAPTER_LIST -> {
                val payload = decodePayload<MangaPayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val manga = json.decodeFromString<SManga>(payload.mangaJson)
                val chapters = catalogue.getChapterList(manga)
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(chapters))
            }

            IpcCommands.GET_PAGE_LIST -> {
                val payload = decodePayload<ChapterPayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val chapter = json.decodeFromString<SChapter>(payload.chapterJson)
                val pages = catalogue.getPageList(chapter)
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(pages))
            }

            IpcCommands.GET_IMAGE -> {
                val payload = decodePayload<ImagePayload>(request, payloadElement)
                val source = getCatalogueSource(payload.sourceId)
                val image = if (source is WindowsImageSource) {
                    val bytes = source.getImage(payload.page)
                    val directory = File("page-images").apply { mkdirs() }
                    val file = java.nio.file.Files.createTempFile(directory.toPath(), "page-", ".img").toFile()
                    try {
                        file.writeBytes(bytes)
                    } catch (error: Exception) {
                        file.delete()
                        throw error
                    }
                    ImageFilePayload(file.name)
                } else {
                    ImageFilePayload()
                }
                IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(image))
            }

            IpcCommands.GET_FILTER_LIST -> {
                val payload = decodePayload<SourcePayload>(request, payloadElement)
                val catalogue = getCatalogueSource(payload.sourceId)
                val filterList = if (catalogue is TachiyomiCatalogueSourceAdapter) {
                    catalogue.getIpcFilterList()
                } else {
                    catalogue.getFilterList()
                }
                IpcResponse(request.requestId, success = true, payloadJson = encodeFilterList(filterList))
            }

            IpcCommands.GET_SOURCE_PREFERENCES -> {
                val payload = decodePayload<SourcePayload>(request, payloadElement)
                val source = loadedSources[payload.sourceId]
                    ?: throw IllegalArgumentException("Source with ID ${payload.sourceId} not found")
                val configurable = source.configurableSourceOrNull()
                val response = if (configurable == null) {
                    SourcePreferencesDto(sourceId = payload.sourceId, supported = false)
                } else {
                    val model = sourcePreferenceModels.computeIfAbsent(payload.sourceId) {
                        SourcePreferenceModel(configurable, preferenceContext)
                    }
                    SourcePreferencesDto(
                        sourceId = payload.sourceId,
                        supported = true,
                        definitions = model.definitions(),
                    )
                }
                IpcResponse(
                    request.requestId,
                    success = true,
                    payloadJson = encodeSourcePreferences(response),
                )
            }

            IpcCommands.SET_SOURCE_PREFERENCE -> {
                val payload = decodePayload<SetSourcePreferencePayload>(request, payloadElement)
                val source = loadedSources[payload.sourceId]
                    ?: throw IllegalArgumentException("Source with ID ${payload.sourceId} not found")
                val configurable = source.configurableSourceOrNull()
                    ?: throw IllegalArgumentException(
                        "Source with ID ${payload.sourceId} does not implement ConfigurableSource",
                    )
                val model = sourcePreferenceModels.computeIfAbsent(payload.sourceId) {
                    SourcePreferenceModel(configurable, preferenceContext)
                }
                val updatedValue = model.set(payload.key, payload.value)
                IpcResponse(
                    request.requestId,
                    success = true,
                    payloadJson = encodeSourcePreferenceValue(updatedValue),
                )
            }

            else -> IpcResponse(request.requestId, success = false, error = "Unknown command '${request.command}'")
        }
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        if (e is VirtualMachineError || e is ThreadDeath) throw e
        System.err.println("Extension request failed: ${request.command}")
        e.printStackTrace(System.err)
        val unwrapped = generateSequence(e) { it.cause }.firstOrNull {
            it !is java.lang.reflect.InvocationTargetException &&
                it !is java.util.concurrent.ExecutionException
        } ?: e
        val errorKind = unwrapped::class.java.simpleName
        IpcResponse(
            request.requestId,
            success = false,
            error = formatExtensionError(unwrapped),
            networkFailure = e.findNetworkFailure(),
            errorKind = errorKind,
        )
    }
}
