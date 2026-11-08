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

@Serializable
data class ExtensionStoreItem(
    val pkg: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val libVersion: Double = 1.4,
    val lang: String = "en",
    val isNsfw: Boolean = false,
    val sources: List<SourceDescriptor> = emptyList(),
    val downloadUrl: String = "",
    val iconUrl: String = "",
    val sha256: String = "",
    val repoUrl: String = "",
    /** Store name as the repository declares it, when its index publishes one. */
    val storeName: String = "",
    val declaredDomains: List<String> = emptyList(),
    /** Repository signing key (certificate SHA-256 fingerprint) for this package, if known. */
    val signingKey: String = "",
    /** Set by the trust confirmation UI; causes the installer to persist explicit user trust. */
    val trustOnInstall: Boolean = false,
) {
    val signerFingerprint: String get() = signingKey
    val signingKeyFingerprint: String get() = signingKey
    val hasSigningKey: Boolean get() = signingKey.isNotBlank()
}

@OptIn(ExperimentalSerializationApi::class)
class ExtensionStoreService(
    internal val preferenceStore: DesktopPreferenceStore,
    policyProvider: () -> DesktopNetworkPolicy = { DesktopNetworkPolicy() },
    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .proxySelector(DesktopPolicyProxySelector(policyProvider))
        .withTlsFallback(),
) {
    companion object {
        const val PREF_KEY_REPOSITORIES = "extension.repositories"

        /** Desktop can't install these promotional/meta packages; hide them from the store. */
        val HIDDEN_EXTENSION_PACKAGES = setOf(
            "eu.kanade.tachiyomi.extension.all.keiyoushi",
            "eu.kanade.tachiyomi.extension.all.mihon",
        )
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Synchronized
    fun getRepositories(): List<String> {
        val stored = preferenceStore.property(PREF_KEY_REPOSITORIES)
        val rawList = stored.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val normalizedList = rawList.map { normalizeRepoUrl(it) }.filter { it.isNotEmpty() }.distinct()
        if (rawList != normalizedList) {
            preferenceStore.update {
                setProperty(PREF_KEY_REPOSITORIES, normalizedList.joinToString("\n"))
            }
        }
        return normalizedList
    }

    @Synchronized
    fun addRepository(repoUrl: String) {
        val normalized = normalizeRepoUrl(repoUrl)
        if (normalized.isBlank()) return
        val current = getRepositories()
        if (current.none { normalizeRepoUrl(it) == normalized }) {
            val updated = (current + normalized).distinct()
            preferenceStore.update {
                setProperty(PREF_KEY_REPOSITORIES, updated.joinToString("\n"))
            }
        }
    }

    @Synchronized
    fun removeRepository(repoUrl: String) {
        val normalized = normalizeRepoUrl(repoUrl)
        val current = getRepositories()
        val updated = current.filter { normalizeRepoUrl(it) != normalized }
        if (updated.size != current.size) {
            preferenceStore.update {
                setProperty(PREF_KEY_REPOSITORIES, updated.joinToString("\n"))
            }
        }
    }

    fun normalizeRepoUrl(raw: String): String = normalizeRepositoryUrl(raw)

    /**
     * Fetch extensions from a repository URL.
     *
     * Mirrors the Android Mihon strategy:
     * 1. Try fetching `index.pb` (protobuf, possibly gzip-compressed) first
     * 2. Decompress gzip if detected (magic bytes 0x1f 0x8b)
     * 3. Ignore an optional UTF-8 BOM and JSON whitespace before sniffing `[`; otherwise parse protobuf
     * 4. Fall back to `index.min.json` if `index.pb` fails
     */
    suspend fun fetchRepository(repoUrl: String): List<ExtensionStoreItem> = withContext(Dispatchers.IO) {
        val normalized = normalizeRepoUrl(repoUrl)

        // cdn.jsdelivr.net is unreachable on some networks; try equivalent mirrors in order. Items
        // keep the canonical repo URL so installed-extension bookkeeping is unaffected.
        for (base in mirrorCandidates(normalized)) {
            // Try index.pb first (modern protobuf format); it carries its own signingKey.
            val items = tryFetchAndParse(base, "$base/index.pb")
                ?: tryFetchAndParse(base, "$base/index.min.json") // legacy JSON format
                ?: continue
            return@withContext applyLegacyRepoMetadata(base, items)
                .map { if (it.repoUrl == base) it.copy(repoUrl = normalized) else it }
        }

        throw IllegalStateException(
            "Failed to fetch extension index from $normalized: neither index.pb nor index.min.json available" +
                " (tried ${mirrorCandidates(normalized).joinToString()})",
        )
    }

    /**
     * Base URLs to try for [normalizedRepoUrl], canonical first. jsDelivr GitHub URLs fall back to
     * its other CDN hostnames and to raw.githubusercontent.com.
     */
    fun mirrorCandidates(normalizedRepoUrl: String): List<String> {
        val match =
            Regex("""^https?://(?:cdn|fastly|gcore|testingcf)\.jsdelivr\.net/gh/([^/@]+)/([^/@]+)@([^/]+)(/.*)?$""")
                .matchEntire(normalizedRepoUrl) ?: return listOf(normalizedRepoUrl)
        val (user, repo, ref, rest) = match.destructured
        val path = "gh/$user/$repo@$ref$rest"
        return (
            listOf(normalizedRepoUrl) +
                listOf("fastly", "gcore", "testingcf", "cdn").map { "https://$it.jsdelivr.net/$path" } +
                "https://raw.githubusercontent.com/$user/$repo/$ref$rest"
            ).distinct()
    }

    /**
     * Legacy repositories publish their signing key and store name separately in repo.json. Fetch
     * it only when the index itself does not carry that metadata for every extension.
     */
    fun parseIndex(
        indexJson: String,
        repoUrl: String,
        defaultSigningKey: String = "",
    ): List<ExtensionStoreItem> {
        val jsonArray = json.decodeFromString<List<JsonObject>>(indexJson)
        return jsonArray.mapNotNull { obj ->
            try {
                val pkg = obj["pkg"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (pkg in HIDDEN_EXTENSION_PACKAGES) {
                    return@mapNotNull null
                }
                val name = obj["name"]?.jsonPrimitive?.content ?: pkg
                val version = obj["version"]?.jsonPrimitive?.content ?: "1.0.0"
                val code = obj["code"]?.jsonPrimitive?.longOrNull ?: 1L
                val lang = obj["lang"]?.jsonPrimitive?.content ?: "en"
                val libVersion = obj["libVersion"]?.jsonPrimitive?.doubleOrNull ?: 1.4
                val isNsfw = (obj["nsfw"]?.jsonPrimitive?.content == "1") ||
                    (obj["isNsfw"]?.jsonPrimitive?.booleanOrNull == true)

                // Legacy repos (keiyoushi, etc.) store APKs under /apk/ subdirectory.
                // The "apk" field is a bare filename; "downloadUrl" is already a full path/URL.
                val apkField = obj["apk"]?.jsonPrimitive?.content
                val downloadUrlField = obj["downloadUrl"]?.jsonPrimitive?.content
                val downloadUrl = when {
                    apkField != null -> resolveUrl(repoUrl, "apk/$apkField")
                    downloadUrlField != null -> resolveUrl(repoUrl, downloadUrlField)
                    else -> ""
                }

                // Legacy repos store icons under /icon/{pkg}.png
                val iconField = obj["icon"]?.jsonPrimitive?.content
                val iconUrl = if (iconField != null) {
                    resolveUrl(repoUrl, iconField)
                } else if (pkg.isNotEmpty()) {
                    resolveUrl(repoUrl, "icon/$pkg.png")
                } else {
                    ""
                }

                val sha256 = obj["sha256"]?.jsonPrimitive?.content ?: ""
                val signingKey = obj["signingKey"]?.jsonPrimitive?.content?.takeIf { it.hasUsableSigningKeyValue() }
                    ?: obj["signingKeyFingerprint"]?.jsonPrimitive?.content?.takeIf { it.hasUsableSigningKeyValue() }
                    ?: defaultSigningKey.takeIf { it.hasUsableSigningKeyValue() } ?: ""

                val sources = obj["sources"]?.jsonArray?.mapNotNull { sObj ->
                    val s = sObj.jsonObject
                    val sId = s["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val sName = s["name"]?.jsonPrimitive?.content ?: ""
                    val sLang = s["lang"]?.jsonPrimitive?.content ?: lang
                    val sClass = s["className"]?.jsonPrimitive?.content
                        ?: s["class"]?.jsonPrimitive?.content
                        ?: ""
                    val sLatest = s["supportsLatest"]?.jsonPrimitive?.booleanOrNull ?: true
                    SourceDescriptor(sId, sName, sLang, sClass, sLatest)
                } ?: emptyList()

                val declaredDomains = obj["declaredDomains"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: emptyList()

                ExtensionStoreItem(
                    pkg = pkg,
                    name = name,
                    version = version,
                    versionCode = code,
                    libVersion = libVersion,
                    lang = lang,
                    isNsfw = isNsfw,
                    sources = sources,
                    downloadUrl = downloadUrl,
                    iconUrl = iconUrl,
                    sha256 = sha256,
                    repoUrl = repoUrl,
                    declaredDomains = declaredDomains,
                    signingKey = signingKey,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
