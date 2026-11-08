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

internal suspend fun DesktopImageLoader.loadFromNetwork(
    url: String,
    customHeaders: Map<String, String>,
    request: ImageRequest? = null,
    fast: Boolean = false,
    onIoFailure: (() -> Unit)? = null,
): ImageBitmap? {
    val maxAttempt = if (fast) 0 else 2
    val semaphore = if (fast) mirrorSemaphore else networkSemaphore
    val httpClient = if (fast) mirrorClient else client
    val hash = sha256(url)
    val diskFile = diskCacheDir.resolve("$hash.img")

    // Check disk cache
    if (Files.isRegularFile(diskFile) && Files.size(diskFile) > 0) {
        val cachedBitmap = decodeFile(diskFile)
        if (cachedBitmap != null) return cachedBitmap
        Files.deleteIfExists(diskFile)
    }

    val backoffs = listOf(300L, 900L)
    for (attempt in 0..maxAttempt) {
        if (attempt > 0) {
            delay(backoffs[attempt - 1])
        }
        var shouldRetry = false
        val bitmap = semaphore.withPermit {
            try {
                val reqBuilder = Request.Builder().url(url)
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MihonW/1.0")
                customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val retryable = shouldRetryCoverFetch(statusOrNull = code, ioFailure = false)
                        if (retryable && attempt < maxAttempt) {
                            debugLog("HTTP-$code $url (attempt $attempt, will retry)")
                            shouldRetry = true
                            return@withPermit null
                        }
                        debugLog("HTTP-$code $url")
                        if (request?.mangaId != null) {
                            coverFailures.record(request.mangaId, url, code)
                        }
                        request?.onHttpError?.invoke(code)
                        return@withPermit null
                    }
                    val body = response.body
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) return@withPermit null

                    val decoded = decodeBytes(bytes)
                    if (decoded == null) {
                        debugLog("DECODE-FAIL $url bytes=${bytes.size}")
                        return@withPermit null
                    }

                    // Caching is best effort: a read-only or full cache disk must not turn an
                    // otherwise successful cover download into a failed image.
                    runCatching {
                        val tmpFile = diskCacheDir.resolve("$hash.tmp")
                        Files.write(tmpFile, bytes)
                        Files.move(
                            tmpFile,
                            diskFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    }.onFailure {
                        mihon.desktop.logging.DesktopLogger.warn("ImageLoader", "Cannot cache cover $url", it)
                    }

                    return@withPermit decoded
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: IOException) {
                if (shouldRetryCoverFetch(statusOrNull = null, ioFailure = true) && attempt < maxAttempt) {
                    debugLog("FETCH-FAIL $url ${e.javaClass.name}: ${e.message} (attempt $attempt, will retry)")
                    shouldRetry = true
                    return@withPermit null
                }
                onIoFailure?.invoke()
                debugLog("FETCH-FAIL $url ${e.javaClass.name}: ${e.message}")
                return@withPermit null
            } catch (e: Exception) {
                debugLog("FETCH-FAIL $url ${e.javaClass.name}: ${e.message}")
                return@withPermit null
            }
        }

        if (bitmap != null) {
            return bitmap
        }
        if (!shouldRetry) {
            return null
        }
    }
    return null
}

internal fun DesktopImageLoader.debugLog(line: String) {
    if (System.getenv("MIHON_IMAGE_DEBUG") == null) return
    runCatching {
        java.io.File(System.getenv("TEMP") ?: ".", "mihon-image-debug.log").appendText(
            "[${java.time.LocalTime.now()}] $line\n",
        )
    }
}

internal fun DesktopImageLoader.decodeFile(file: Path): ImageBitmap? {
    return try {
        val bytes = Files.readAllBytes(file)
        decodeBytes(bytes)
    } catch (error: Exception) {
        mihon.desktop.logging.DesktopLogger.debug("ImageLoader", "Cover decode failed", error)
        null
    }
}

internal fun DesktopImageLoader.decodeBytes(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    val native = try {
        Image.makeFromEncoded(bytes).use { skiaImage ->
            skiaImage.toComposeImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
    if (native != null) return native

    return when (ImageFormatDetector.detect(bytes)) {
        ReaderImageFormat.AVIF,
        ReaderImageFormat.HEIF,
        ReaderImageFormat.JXL,
        ReaderImageFormat.TIFF,
        -> decodeWithPackagedCodec(bytes)
        else -> null
    }
}

/** Uses the same bundled ImageMagick runtime as the reader for formats Skia cannot decode. */
internal fun DesktopImageLoader.decodeWithPackagedCodec(bytes: ByteArray): ImageBitmap? {
    val executable = PackagedReaderCodec.executablePath()
    if (!Files.isRegularFile(executable)) return null
    val input = Files.createTempFile(diskCacheDir, "cover-", ".img")
    val output = Files.createTempFile(diskCacheDir, "cover-", ".png")
    val error = Files.createTempFile(diskCacheDir, "cover-", ".log")
    var process: Process? = null
    return try {
        Files.write(input, bytes)
        val codec = ProcessBuilder(
            executable.toString(),
            "-limit", "memory", "128MiB",
            "-limit", "map", "0",
            "-limit", "disk", "256MiB",
            input.toString(),
            "-auto-orient",
            "-delete", "1--1",
            "PNG:$output",
        ).redirectError(error.toFile()).start()
        process = codec
        mihon.desktop.platform.ChildProcessReaper.track(codec)
        if (!codec.waitFor(30, TimeUnit.SECONDS)) {
            return null
        }
        if (codec.exitValue() != 0 || Files.size(output) == 0L ||
            Files.size(output) > DesktopImageLoader.MAX_FALLBACK_BYTES
        ) {
            return null
        }
        val png = Files.readAllBytes(output)
        Image.makeFromEncoded(png).use { image -> image.toComposeImageBitmap() }
    } catch (_: Exception) {
        null
    } finally {
        if (process?.isAlive == true) process?.destroyForcibly()
        Files.deleteIfExists(input)
        Files.deleteIfExists(output)
        Files.deleteIfExists(error)
    }
}

internal fun DesktopImageLoader.isImageFile(name: String): Boolean =
    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".avif") ||
        name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".jxl") ||
        name.endsWith(".bmp") || name.endsWith(".tif") || name.endsWith(".tiff")
