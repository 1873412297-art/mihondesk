package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import mihon.desktop.logging.DesktopLogger
import mihon.reader.image.ImageIoPageDecoder
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Height a downloaded page is split down to. Android uses `2 * max(displayHeight, displayWidth)`
 * measured from the running display; the download path here has no display metric, so a fixed
 * 4096 px (about twice a 4K display's height) stands in for it.
 */
internal const val OPTIMAL_SPLIT_HEIGHT: Int = 4096

/** Android compresses split parts with `Bitmap.CompressFormat.JPEG` at quality 100. */
internal const val SPLIT_JPEG_QUALITY: Float = 1.0f

/** Outcome of one split attempt; [Untouched] also covers every skip and failure. */
sealed interface TallImageSplitResult {
    /** The original page was replaced by [partCount] parts totalling [totalBytes] on disk. */
    data class Split(val partCount: Int, val totalBytes: Long) : TallImageSplitResult

    /** The page file is exactly as it was written, because splitting is off or does not apply. */
    data object Untouched : TallImageSplitResult
}

/**
 * Replaces a downloaded page whose image is very tall with several screen-sized JPEG parts, so the
 * reader never has to decode one enormous page. Mirrors Android's `ImageUtil.splitTallImage`.
 *
 * A page that cannot be probed, is animated, has a normal aspect ratio, or fails midway is left
 * whole: splitting is an optimisation and must never fail a download.
 */
class TallImageSplitter(
    private val diskProvider: DownloadDiskProvider,
    private val enabled: () -> Boolean = { false },
    private val decoder: PageDecoder = ImageIoPageDecoder(BoundedReaderMemoryBudget()),
    private val encodeJpeg: (BufferedImage, Path) -> Unit = ::writeJpeg,
    private val optimalSplitHeight: Int = OPTIMAL_SPLIT_HEIGHT,
) {
    suspend fun split(pageFile: Path, pageIndex: Int): TallImageSplitResult {
        if (!enabled()) return TallImageSplitResult.Untouched

        val produced = mutableListOf<Path>()
        return try {
            val metadata = decoder.probe(openInput(pageFile))
            if (metadata.isAnimated) return TallImageSplitResult.Untouched
            if (!TallImageSplitPolicy.shouldSplit(metadata.width, metadata.height, optimalSplitHeight)) {
                return TallImageSplitResult.Untouched
            }
            val partCount = TallImageSplitPolicy.partCount(metadata.height, optimalSplitHeight)
            // Parts never coexist with the whole page, so anything already there is stale.
            (0 until partCount).forEach { partIndex ->
                Files.deleteIfExists(diskProvider.getSplitPartFile(pageFile.parent, pageIndex, partIndex))
            }

            val partHeight = metadata.height / partCount
            for (partIndex in 0 until partCount) {
                val top = partIndex * partHeight
                // The remainder of the integer division lands on the last part.
                val bottom = if (partIndex == partCount - 1) metadata.height else top + partHeight
                val target = diskProvider.getSplitPartFile(pageFile.parent, pageIndex, partIndex)
                val staging = target.resolveSibling("${target.fileName}.part")
                produced.add(target)
                try {
                    val tile = decoder.decodeRegion(
                        openInput(pageFile),
                        metadata,
                        TileRequest(
                            TileKey(
                                PageId(SPLITTER_PAGE_ID, pageFile.fileName.toString()),
                                null,
                                IntRect(0, top, metadata.width, bottom),
                            ),
                            metadata.width,
                            bottom - top,
                        ),
                    )
                    try {
                        encodeJpeg(tile.image, staging)
                    } finally {
                        tile.close()
                    }
                    if (!diskProvider.isValidPage(staging)) {
                        throw IOException("Split part ${target.fileName} is corrupt or incomplete")
                    }
                    move(staging, target)
                } finally {
                    Files.deleteIfExists(staging)
                }
            }

            val totalBytes = produced.sumOf { Files.size(it) }
            Files.delete(pageFile)
            TallImageSplitResult.Split(partCount, totalBytes)
        } catch (error: CancellationException) {
            produced.forEach { runCatching { Files.deleteIfExists(it) } }
            throw error
        } catch (error: Exception) {
            produced.forEach { runCatching { Files.deleteIfExists(it) } }
            DesktopLogger.warn(
                TAG,
                "Could not split page ${pageIndex + 1} (${pageFile.fileName}); keeping the original image",
                error,
            )
            TallImageSplitResult.Untouched
        }
    }

    private fun openInput(pageFile: Path): BoundedPageInput =
        BoundedPageInput(Files.newInputStream(pageFile), Files.size(pageFile))

    private fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val TAG = "TallImageSplitter"
        const val SPLITTER_PAGE_ID = "download-page"
    }
}

/**
 * Split arithmetic of Android's `TallImageSplitCalculator`
 * (`core/common/src/main/kotlin/tachiyomi/core/common/util/system/TallImageSplitCalculator.kt`).
 */
private object TallImageSplitPolicy {
    fun partCount(imageHeight: Int, optimalHeight: Int): Int = (imageHeight - 1) / optimalHeight + 1

    fun shouldSplit(imageWidth: Int, imageHeight: Int, optimalHeight: Int): Boolean =
        imageHeight > imageWidth * 3 && partCount(imageHeight, optimalHeight) > 1
}

/**
 * JPEG cannot carry alpha, so the tile is composited over white first; `ImageIO.write` on the
 * decoder's premultiplied ARGB image would write the wrong colours.
 */
private fun writeJpeg(image: BufferedImage, target: Path) {
    val opaque = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    try {
        val graphics = opaque.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, opaque.width, opaque.height)
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }

        val writers = ImageIO.getImageWritersByFormatName(JPEG_FORMAT)
        val writer = if (writers.hasNext()) writers.next() else null
        val params = writer?.defaultWriteParam?.takeIf { it.canWriteCompressed() }?.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = SPLIT_JPEG_QUALITY
        }
        val written = if (writer == null) {
            false
        } else {
            try {
                if (params == null) {
                    false
                } else {
                    ImageIO.createImageOutputStream(target.toFile())?.use { output ->
                        writer.output = output
                        writer.write(null, IIOImage(opaque, null, null), params)
                        true
                    } ?: false
                }
            } finally {
                writer.dispose()
            }
        }
        if (!written && !ImageIO.write(opaque, JPEG_FORMAT, target.toFile())) {
            throw IOException("No JPEG encoder is available")
        }
    } finally {
        opaque.flush()
    }
}

private const val JPEG_FORMAT = "jpeg"
