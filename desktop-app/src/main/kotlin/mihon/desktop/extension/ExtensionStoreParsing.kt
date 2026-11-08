package mihon.desktop.extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.updates.DesktopAppUpdateService
import mihon.extension.model.SourceDescriptor
import mihon.extension.network.withTlsFallback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

internal fun ExtensionStoreService.applyLegacyRepoMetadata(
    repoUrl: String,
    items: List<ExtensionStoreItem>,
): List<ExtensionStoreItem> {
    if (items.none { !it.hasUsableSigningKey() || it.storeName.isBlank() }) return items
    val metadata = fetchLegacyRepoMetadata(repoUrl)
    if (metadata.signingKey.isBlank() && metadata.name.isBlank()) return items
    return items.map { item ->
        item.copy(
            signingKey = if (item.hasUsableSigningKey()) item.signingKey else metadata.signingKey,
            storeName = item.storeName.ifBlank { metadata.name },
        )
    }
}

/**
 * Fetch a legacy `repo.json` and return its signing key fingerprint and store name, if present.
 * Failures are intentionally ignored so repositories without repo.json still work.
 */
internal fun ExtensionStoreService.fetchLegacyRepoMetadata(repoUrl: String): LegacyRepoMetadata {
    val request = Request.Builder()
        .url("$repoUrl/repo.json")
        .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
        .build()
    val response = try {
        httpClient.newCall(request).execute()
    } catch (_: Exception) {
        return LegacyRepoMetadata()
    }
    if (!response.isSuccessful) {
        response.close()
        return LegacyRepoMetadata()
    }
    return try {
        val body = decompressIfGzipped(response.body.bytes()).decodeToString()
        val obj = json.parseToJsonElement(body).jsonObject
        val meta = obj["meta"]?.jsonObject
        LegacyRepoMetadata(
            signingKey = obj["signingKey"]?.jsonPrimitive?.content
                ?: obj["signingKeyFingerprint"]?.jsonPrimitive?.content
                ?: meta?.get("signingKeyFingerprint")?.jsonPrimitive?.content
                ?: meta?.get("signingKey")?.jsonPrimitive?.content
                ?: "",
            name = meta?.get("name")?.jsonPrimitive?.content
                ?: meta?.get("shortName")?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
                ?: obj["shortName"]?.jsonPrimitive?.content
                ?: "",
        )
    } catch (_: Exception) {
        LegacyRepoMetadata()
    } finally {
        response.close()
    }
}

internal data class LegacyRepoMetadata(
    val signingKey: String = "",
    val name: String = "",
)

/**
 * Fetch a URL and auto-detect the format (protobuf vs JSON) based on content sniffing.
 * Returns null if the HTTP request fails (e.g. 404).
 */
internal fun ExtensionStoreService.tryFetchAndParse(
    repoBaseUrl: String,
    targetUrl: String,
    defaultSigningKey: String = "",
): List<ExtensionStoreItem>? {
    val request = Request.Builder()
        .url(targetUrl)
        .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
        .build()

    val response = try {
        httpClient.newCall(request).execute()
    } catch (_: Exception) {
        return null
    }

    if (!response.isSuccessful) {
        response.close()
        return null
    }

    return try {
        response.use {
            val decompressed = decompressIfGzipped(it.body.bytes())
            if (decompressed.isEmpty()) return@use null

            val jsonPayload = decompressed.jsonPayloadOrNull()
            if (jsonPayload != null) {
                if (jsonPayload.firstOrNull() == '[') {
                    parseIndex(jsonPayload, repoBaseUrl, defaultSigningKey)
                } else {
                    parseNetworkStore(json.decodeFromString(jsonPayload), repoBaseUrl)
                }
            } else {
                parseProtobufIndex(decompressed, repoBaseUrl)
            }
        }
    } catch (error: Exception) {
        // A malformed index.pb must fall back to index.min.json instead of failing the repository.
        mihon.desktop.logging.DesktopLogger.debug("ExtensionStore", "Cannot parse $targetUrl", error)
        null
    }
}

internal fun ByteArray.jsonPayloadOrNull(): String? {
    var offset = if (
        size >= 3 &&
        this[0] == 0xEF.toByte() &&
        this[1] == 0xBB.toByte() &&
        this[2] == 0xBF.toByte()
    ) {
        3
    } else {
        0
    }
    while (offset < size && this[offset].toInt().toChar().isWhitespace()) offset++
    if (offset >= size || (this[offset] != '['.code.toByte() && this[offset] != '{'.code.toByte())) return null
    return decodeToString(offset, size)
}

/**
 * Decompress gzip data if the magic bytes (0x1f 0x8b) are detected.
 */
internal fun ExtensionStoreService.decompressIfGzipped(data: ByteArray): ByteArray {
    if (data.size < 2) return data
    val isGzip = data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    if (!isGzip) return data

    return try {
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    } catch (_: Exception) {
        data // Return original if decompression fails
    }
}

/**
 * Parse a protobuf-encoded [DesktopNetworkExtensionStore] and convert to [ExtensionStoreItem] list.
 */
internal fun ExtensionStoreService.parseProtobufIndex(data: ByteArray, repoBaseUrl: String): List<ExtensionStoreItem> {
    val store = ProtoBuf.decodeFromByteArray<DesktopNetworkExtensionStore>(data)
    return parseNetworkStore(store, repoBaseUrl)
}

internal fun ExtensionStoreService.parseNetworkStore(
    store: DesktopNetworkExtensionStore,
    repoBaseUrl: String,
): List<ExtensionStoreItem> {
    // If the store has a separate extensionListUrl, fetch that too
    val extensionList = store.extensionList
        ?: store.extensionListUrl?.let { listUrl ->
            val listRequest = Request.Builder()
                .url(resolveUrl(repoBaseUrl, listUrl))
                .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
                .build()
            httpClient.newCall(listRequest).execute().use { listResponse ->
                if (listResponse.isSuccessful) {
                    val listBytes = decompressIfGzipped(listResponse.body.bytes())
                    val jsonPayload = listBytes.jsonPayloadOrNull()
                    if (jsonPayload != null) {
                        json.decodeFromString<DesktopNetworkExtensionStore.ExtensionList>(jsonPayload)
                    } else {
                        ProtoBuf.decodeFromByteArray<DesktopNetworkExtensionStore.ExtensionList>(listBytes)
                    }
                } else {
                    null
                }
            }
        }

    if (extensionList == null) return emptyList()

    return extensionList.extensions.mapNotNull { ext ->
        try {
            val pkg = ext.packageName
            if (pkg.isBlank() || pkg in ExtensionStoreService.HIDDEN_EXTENSION_PACKAGES) return@mapNotNull null

            val lang = ext.sources.map { it.language }.toSet()
            val isNsfw = ext.contentWarning >= DesktopNetworkExtensionStore.ContentWarning.MIXED

            val sources = ext.sources.map { src ->
                SourceDescriptor(
                    id = src.id,
                    name = src.name,
                    lang = src.language,
                    className = "",
                    supportsLatest = true,
                )
            }

            val downloadUrl = when {
                ext.resources.jarUrl.isNotBlank() -> resolveUrl(repoBaseUrl, ext.resources.jarUrl)
                ext.resources.apkUrl.isNotBlank() -> resolveUrl(repoBaseUrl, ext.resources.apkUrl)
                else -> ""
            }
            val iconUrl = if (ext.resources.iconUrl.isNotBlank()) {
                resolveUrl(repoBaseUrl, ext.resources.iconUrl)
            } else if (pkg.isNotEmpty()) {
                resolveUrl(repoBaseUrl, "icon/$pkg.png")
            } else {
                ""
            }

            ExtensionStoreItem(
                pkg = pkg,
                name = ext.name,
                version = ext.versionName,
                versionCode = ext.versionCode,
                libVersion = ext.extensionLib.toDoubleOrNull() ?: 1.4,
                lang = if (lang.size == 1) lang.first() else "all",
                isNsfw = isNsfw,
                sources = sources,
                downloadUrl = downloadUrl,
                iconUrl = iconUrl,
                repoUrl = repoBaseUrl,
                storeName = store.name.ifBlank { store.badgeLabel },
                signingKey = store.signingKey,
            )
        } catch (_: Exception) {
            null
        }
    }
}

internal fun ExtensionStoreItem.hasUsableSigningKey(): Boolean =
    signingKey.hasUsableSigningKeyValue()

internal fun String.hasUsableSigningKeyValue(): Boolean =
    isNotBlank() && !equals("NO_SIGNING_KEY", ignoreCase = true)

internal fun ExtensionStoreService.resolveUrl(base: String, relative: String): String {
    if (relative.startsWith("http://") || relative.startsWith("https://")) {
        return relative
    }
    return try {
        val baseUri = URI(if (base.endsWith("/")) base else "$base/")
        baseUri.resolve(relative.removePrefix("/")).toString()
    } catch (_: Exception) {
        "$base/$relative"
    }
}

internal suspend fun ExtensionStoreService.fetchAvailableExtensions(): List<ExtensionStoreItem> = withContext(
    Dispatchers.IO,
) {
    val repos = getRepositories()
    val allItems = mutableListOf<ExtensionStoreItem>()
    for (repo in repos) {
        try {
            val items = fetchRepository(repo)
            allItems.addAll(items)
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.warn("ExtensionStore", "Repository unavailable", error)
        }
    }
    // Deduplicate by package name, taking the highest versionCode
    allItems.groupBy { it.pkg }.map { (_, items) ->
        items.maxByOrNull { it.versionCode }!!
    }.sortedBy { it.name }
}
