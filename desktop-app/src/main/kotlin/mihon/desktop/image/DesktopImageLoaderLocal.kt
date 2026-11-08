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

internal fun DesktopImageLoader.thumbnailKey(request: ImageRequest, maxSize: Int): String? = try {
    val custom = if (request.mangaId != null && customCoverManager != null) {
        customCoverManager.getCustomCover(request.mangaId)
    } else {
        null
    }
    val uriStr = request.uri?.trim()
    val source = when {
        custom != null -> "custom:${request.mangaId}:${Files.getLastModifiedTime(custom).toMillis()}"
        uriStr.isNullOrBlank() -> request.localMangaPath?.let { localSourceKey(it) }
        uriStr.startsWith("http://", ignoreCase = true) || uriStr.startsWith("https://", ignoreCase = true) -> {
            val disk = cdnMirrorCandidates(uriStr)
                .map { diskCacheDir.resolve("${sha256(it)}.img") }
                .firstOrNull { Files.isRegularFile(it) }
            "net:$uriStr:${disk?.let { Files.getLastModifiedTime(it).toMillis() } ?: 0}"
        }
        else -> {
            val path = if (uriStr.startsWith(
                    "file://",
                    ignoreCase = true,
                )
            ) {
                Path.of(URI(uriStr))
            } else {
                Path.of(uriStr)
            }
            localSourceKey(path)
        }
    }
    source?.let { "$it@$maxSize" }
} catch (_: Exception) {
    null
}

internal fun DesktopImageLoader.localSourceKey(path: Path): String? =
    if (Files.exists(path)) "file:${path.toAbsolutePath()}:${Files.getLastModifiedTime(path).toMillis()}" else null

internal fun DesktopImageLoader.thumbnailFile(key: String): Path = diskCacheDir.resolve(
    DesktopImageLoader.THUMBNAIL_DIR,
).resolve("${sha256(key)}.jpg")

internal suspend fun DesktopImageLoader.loadFull(request: ImageRequest): ImageBitmap? {
    // 1. Check custom cover first if mangaId is present
    if (request.mangaId != null && customCoverManager != null) {
        val custom = customCoverManager.getCustomCover(request.mangaId)
        if (custom != null) {
            val cacheKey = "custom_${request.mangaId}_${Files.getLastModifiedTime(custom).toMillis()}"
            memoryCache[cacheKey]?.let { return it }
            val bitmap = decodeFile(custom)
            if (bitmap != null) {
                memoryCache[cacheKey] = bitmap
                return bitmap
            }
        }
    }

    // 2. Determine URI to load
    val uriStr = request.uri?.trim()
    if (uriStr.isNullOrBlank()) {
        if (request.localMangaPath != null) {
            return loadFromLocalManga(request.localMangaPath)
        }
        return null
    }

    val memKey = "uri_$uriStr"
    memoryCache[memKey]?.let { return it }

    // 3. Network URL
    if (uriStr.startsWith("http://", ignoreCase = true) || uriStr.startsWith("https://", ignoreCase = true)) {
        val candidates = cdnMirrorCandidates(uriStr)
        if (candidates.size == 1) {
            val bitmap = loadFromNetwork(uriStr, request.headers, request)
            if (bitmap != null) memoryCache[memKey] = bitmap
            return bitmap
        }
        // Any mirror already on disk wins before touching the network.
        for (candidate in candidates) {
            val cached = diskCacheDir.resolve("${sha256(candidate)}.img")
            if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
                decodeFile(cached)?.let {
                    memoryCache[memKey] = it
                    return it
                }
            }
        }
        for (candidate in mirrorHealth.order(candidates)) {
            val host = runCatching { URI(candidate).host }.getOrNull().orEmpty()
            var ioFailed = false
            val bitmap = loadFromNetwork(candidate, request.headers, request, fast = true) { ioFailed = true }
            if (bitmap != null) {
                mirrorHealth.recordSuccess(host)
                memoryCache[memKey] = bitmap
                return bitmap
            }
            if (ioFailed) mirrorHealth.recordFailure(host)
        }
        return null
    }

    // 4. File URI or Path
    val filePath = try {
        if (uriStr.startsWith("file://", ignoreCase = true)) {
            Path.of(URI(uriStr))
        } else {
            Path.of(uriStr)
        }
    } catch (_: Exception) {
        null
    }

    if (filePath != null) {
        val bitmap = loadFromLocalPath(filePath)
        if (bitmap != null) {
            memoryCache[memKey] = bitmap
        }
        return bitmap
    }

    if (request.localMangaPath != null) {
        return loadFromLocalManga(request.localMangaPath)
    }

    return null
}

internal fun DesktopImageLoader.loadFromLocalPath(path: Path): ImageBitmap? {
    if (!Files.exists(path)) return null
    if (Files.isDirectory(path)) {
        return loadFromLocalManga(path)
    }
    val filename = path.fileName.toString().lowercase()
    if (filename.endsWith(".cbz") || filename.endsWith(".zip")) {
        return extractCoverFromArchive(path)
    }
    return decodeFile(path)
}

internal fun DesktopImageLoader.loadFromLocalManga(mangaDirOrArchive: Path): ImageBitmap? {
    if (!Files.exists(mangaDirOrArchive)) return null
    if (Files.isRegularFile(mangaDirOrArchive)) {
        return extractCoverFromArchive(mangaDirOrArchive)
    }
    val coverNames = listOf(
        "cover.jpg", "cover.png", "cover.webp", "cover.jpeg", "cover.gif", "cover.avif",
        "cover.heic", "cover.heif", "cover.jxl", "cover.bmp", "cover.tif", "cover.tiff", "Folder.jpg",
    )
    for (name in coverNames) {
        val candidate = mangaDirOrArchive.resolve(name)
        if (Files.isRegularFile(candidate)) {
            return decodeFile(candidate)
        }
    }
    try {
        Files.list(mangaDirOrArchive).use { stream ->
            val files = stream.toList().sortedBy { it.fileName.toString().lowercase() }
            for (file in files) {
                val fn = file.fileName.toString().lowercase()
                if (isImageFile(fn)) {
                    return decodeFile(file)
                } else if (fn.endsWith(".cbz") || fn.endsWith(".zip")) {
                    val fromZip = extractCoverFromArchive(file)
                    if (fromZip != null) return fromZip
                } else if (Files.isDirectory(file)) {
                    val fromSub = loadFromLocalManga(file)
                    if (fromSub != null) return fromSub
                }
            }
        }
    } catch (error: java.io.IOException) {
        mihon.desktop.logging.DesktopLogger.warn("ImageLoader", "Cannot scan $mangaDirOrArchive for a cover", error)
    }
    return null
}

internal fun DesktopImageLoader.extractCoverFromArchive(zipPath: Path): ImageBitmap? {
    try {
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && isImageFile(it.name.lowercase()) }
                .sortedBy { it.name.lowercase() }
                .toList()
            val first = entries.firstOrNull() ?: return null
            zip.getInputStream(first).use { input ->
                val bytes = input.readAllBytes()
                return decodeBytes(bytes)
            }
        }
    } catch (_: Exception) {
        return null
    }
}
