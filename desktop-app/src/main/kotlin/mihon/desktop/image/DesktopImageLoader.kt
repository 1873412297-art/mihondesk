package mihon.desktop.image

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.reader.codec.PackagedReaderCodec
import mihon.extension.network.withTlsFallback
import mihon.reader.image.ImageFormatDetector
import mihon.reader.image.ReaderImageFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

val LocalImageLoader = staticCompositionLocalOf<DesktopImageLoader?> { null }
val LocalCustomCoverManager = staticCompositionLocalOf<CustomCoverManager?> { null }

data class ImageRequest(
    val uri: String?,
    val mangaId: Long? = null,
    val localMangaPath: Path? = null,
    val headers: Map<String, String> = emptyMap(),
    val onHttpError: ((code: Int) -> Unit)? = null,
    /**
     * When set, the result is downscaled so its longest edge is at most this many pixels and the
     * downscaled copy is persisted under [DesktopImageLoader.THUMBNAIL_DIR]. Grids use this so cold
     * starts decode small thumbnails instead of full-size covers.
     */
    val maxThumbnailSize: Int? = null,
)

internal fun shouldRetryCoverFetch(statusOrNull: Int?, ioFailure: Boolean): Boolean {
    if (ioFailure) return true
    if (statusOrNull == null) return false
    return statusOrNull == 429 || statusOrNull in 500..599
}

class DesktopImageLoader(
    internal val diskCacheDir: Path,
    policyProvider: () -> mihon.desktop.extension.DesktopNetworkPolicy = ::DesktopNetworkPolicy,
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .proxySelector(mihon.desktop.extension.DesktopPolicyProxySelector(policyProvider))
        .dns(mihon.desktop.extension.DesktopDns.following(policyProvider))
        .withTlsFallback(),
    internal val customCoverManager: CustomCoverManager? = null,
    internal val maxMemoryEntries: Int = 120,
) {
    internal val networkSemaphore = Semaphore(6)

    /** Icons/assets on GitHub CDNs: fail fast and fan out, separate from manga cover traffic. */
    internal val mirrorSemaphore = Semaphore(8)
    internal val mirrorClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
    internal val mirrorHealth = MirrorHealth(diskCacheDir.resolve("mirror-health.properties"))
    val coverFailures = mihon.desktop.image.CoverFailureRegistry()
    internal val memoryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(maxMemoryEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > maxMemoryEntries
            }
        },
    )

    init {
        Files.createDirectories(diskCacheDir)
    }

    suspend fun load(request: ImageRequest): ImageBitmap? = withContext(Dispatchers.IO) {
        val maxSize = request.maxThumbnailSize
        if (maxSize == null || maxSize <= 0) return@withContext loadFull(request)
        thumbnailKey(request, maxSize)?.let { key ->
            memoryCache["thumb_$key"]?.let { return@withContext it }
            val file = thumbnailFile(key)
            if (Files.isRegularFile(file) && Files.size(file) > 0) {
                decodeFile(file)?.let {
                    memoryCache["thumb_$key"] = it
                    return@withContext it
                }
            }
        }
        val full = loadFull(request) ?: return@withContext null
        val scaled = downscaleImage(full, maxSize)
        // Key is recomputed: a network load may just have created the source file it depends on.
        thumbnailKey(request, maxSize)?.let { key ->
            memoryCache["thumb_$key"] = scaled
            writeThumbnailJpeg(thumbnailFile(key), scaled)
        }
        scaled
    }

    /**
     * Cache identity for a thumbnail: the source plus its last-modified time, so replacing a cover
     * (custom cover, re-downloaded network image, edited local file) invalidates the old thumbnail.
     */
    internal fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getFromMemory(key: String): ImageBitmap? = memoryCache[key]

    /**
     * Downloads (or reads from the disk cache) the encoded bytes behind [url], trying GitHub CDN mirrors.
     * Used to persist repo icons next to installed extensions.
     */
    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (load(ImageRequest(uri = url)) == null) return@withContext null
        cdnMirrorCandidates(url).firstNotNullOfOrNull { candidate ->
            val file = diskCacheDir.resolve("${sha256(candidate)}.img")
            if (Files.isRegularFile(file) && Files.size(file) > 0) Files.readAllBytes(file) else null
        }
    }

    fun putInMemory(key: String, bitmap: ImageBitmap) {
        memoryCache[key] = bitmap
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    fun getCoverFile(mangaId: Long?, url: String?): Path? {
        if (mangaId != null && customCoverManager != null) {
            val custom = customCoverManager.getCustomCover(mangaId)
            if (custom != null && Files.isRegularFile(custom)) return custom
        }
        val trimmed = url?.trim() ?: return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val hash = sha256(trimmed)
            val diskFile = diskCacheDir.resolve("$hash.img")
            if (Files.isRegularFile(diskFile) && Files.size(diskFile) > 0) return diskFile
        } else {
            val candidate = try {
                Path.of(trimmed)
            } catch (_: Exception) {
                null
            }
            if (candidate != null && Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    fun clearDiskCache() {
        try {
            Files.list(diskCacheDir).use { stream ->
                stream.forEach { Files.deleteIfExists(it) }
            }
        } catch (error: java.io.IOException) {
            mihon.desktop.logging.DesktopLogger.warn("ImageLoader", "Failed to clear image disk cache", error)
        }
    }

    companion object {
        /** Sub-directory of the disk cache holding downscaled grid thumbnails. */
        const val THUMBNAIL_DIR = "thumbs"
        internal const val MAX_FALLBACK_BYTES = 64L * 1024L * 1024L
    }
}
