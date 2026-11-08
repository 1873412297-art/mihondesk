package mihon.desktop.cli

import mihon.reader.memory.ReaderMemoryMetrics
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

internal data class ValidatedReaderFixture(
    val root: Path,
    val mangaRoot: Path,
    val standalone: Path,
    val manifestSha256: String,
)

internal data class AssetVerification(
    val pageCount: Int,
    val decodedTileCount: Int,
    val gifFrameHashes: List<String>,
    val corruptPageObserved: Boolean,
    val decodedAfterCorrupt: Boolean,
)

internal class CoreMemoryHighWater(
    internal val snapshot: () -> ReaderMemoryMetrics,
) : AutoCloseable {
    internal val running = AtomicBoolean(true)
    internal val highWater = AtomicLong(0)
    internal val poller = thread(name = "mihon-reader-memory-verifier", isDaemon = true) {
        while (running.get()) {
            sample()
            LockSupport.parkNanos(500_000)
        }
        sample()
    }

    fun stop(): Long {
        close()
        return highWater.get()
    }

    override fun close() {
        if (running.compareAndSet(true, false)) poller.join()
    }

    internal fun sample() {
        val metrics = snapshot()
        val total = Math.addExact(metrics.reservedBytes, metrics.cacheBytes)
        highWater.accumulateAndGet(total, ::maxOf)
    }
}

internal fun assetLabel(name: String): String = when {
    name == TARGET_CHAPTER_NAME -> "directory"
    name.endsWith(".cbz") -> "cbz"
    name.endsWith(".cbt") -> "cbt"
    name.endsWith(".cb7") -> "cb7"
    name.endsWith(".cbr") -> "cbr"
    name.endsWith(".epub") -> "epub"
    else -> error("unknown reader fixture chapter")
}

internal fun hashImage(image: BufferedImage): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val row = IntArray(image.width)
    repeat(image.height) { y ->
        image.getRGB(0, y, image.width, 1, row, 0, image.width)
        row.forEach { pixel ->
            digest.update((pixel ushr 24).toByte())
            digest.update((pixel ushr 16).toByte())
            digest.update((pixel ushr 8).toByte())
            digest.update(pixel.toByte())
        }
    }
    return digest.digest().toHex()
}

internal fun safeRelativePath(value: String): Path {
    check(value.isNotBlank() && '\\' !in value) { "fixture path is not portable" }
    val path = Path.of(value)
    check(!path.isAbsolute && path.normalize() == path && path.none { it.toString() == ".." }) {
        "fixture path is unsafe"
    }
    return path
}

internal fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

internal fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
