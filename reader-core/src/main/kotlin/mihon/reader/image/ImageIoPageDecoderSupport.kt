package mihon.reader.image
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.memory.MemoryKind
import mihon.reader.memory.MemoryLease
import mihon.reader.model.FrameId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import mihon.reader.source.ReaderLimits
import java.awt.AlphaComposite
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode

internal fun ImageIoPageDecoder.gifFrameInfo(reader: ImageReader, frameIndex: Int): GifFrameInfo {
    val metadata = guarded { reader.getImageMetadata(frameIndex) }
    val root = runCatching { metadata.getAsTree("javax_imageio_gif_image_1.0") }
        .getOrNull() as? IIOMetadataNode
    val descriptor = root?.getElementsByTagName("ImageDescriptor")?.item(0) as? IIOMetadataNode
    val control = root?.getElementsByTagName("GraphicControlExtension")?.item(0) as? IIOMetadataNode
    return GifFrameInfo(
        left = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0,
        top = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0,
        width = descriptor?.getAttribute("imageWidth")?.toIntOrNull()
            ?: guarded { reader.getWidth(frameIndex) },
        height = descriptor?.getAttribute("imageHeight")?.toIntOrNull()
            ?: guarded { reader.getHeight(frameIndex) },
        disposal = control?.getAttribute("disposalMethod") ?: "none",
    )
}

internal data class GifFrameInfo(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val disposal: String,
)

internal suspend fun ImageIoPageDecoder.reservePeak(
    finalBytes: Long,
    scratchBytes: Long,
): Pair<MemoryLease, MemoryLease> {
    val total = finalBytes + scratchBytes
    val limit = budget.metrics.limitBytes
    if (total > limit) throw ReaderFailure.LimitExceeded("decode peak", limit, total)
    val finalLease = budget.reserve(MemoryKind.DECODED_OUTPUT, finalBytes)
    val scratchLease = try {
        budget.reserve(MemoryKind.DECODED_OUTPUT, scratchBytes)
    } catch (error: Throwable) {
        finalLease.close()
        throw error
    }
    return finalLease to scratchLease
}

internal fun ImageIoPageDecoder.normalizeToPremultipliedArgb(image: BufferedImage): BufferedImage {
    if (image.type == BufferedImage.TYPE_INT_ARGB_PRE) return image
    val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
    val graphics = converted.createGraphics()
    try {
        graphics.drawImage(image, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return converted
}

internal suspend fun ImageIoPageDecoder.decodeStillRegion(
    reader: ImageReader,
    region: IntRect,
    request: TileRequest,
): DecodedTile {
    val subsampling = chooseSubsampling(region, request)
    val expectedWidth = (region.width + subsampling - 1) / subsampling
    val expectedHeight = (region.height + subsampling - 1) / subsampling
    val rasterBytes = checkedRasterBytes(expectedWidth, expectedHeight)
    val (finalLease, scratchLease) = reservePeak(rasterBytes, rasterBytes)
    var decoded: BufferedImage? = null
    var output: BufferedImage? = null
    var failed = true
    try {
        val param = reader.defaultReadParam
        param.setSourceRegion(Rectangle(region.left, region.top, region.width, region.height))
        if (subsampling > 1) param.setSourceSubsampling(subsampling, subsampling, 0, 0)
        decoded = guarded { reader.read(0, param) } ?: throw ReaderFailure.CorruptImage()
        if (decoded.width > expectedWidth || decoded.height > expectedHeight) {
            throw ReaderFailure.CorruptImage()
        }
        output = BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_ARGB_PRE)
        val graphics = output.createGraphics()
        try {
            graphics.drawImage(decoded, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        decoded.flush()
        decoded = null
        currentCoroutineContext().ensureActive()
        scratchLease.close()
        val tile = BudgetedDecodedTile(request.key, output, finalLease)
        output = null
        failed = false
        return tile
    } finally {
        if (failed) {
            output?.flush()
            decoded?.flush()
            finalLease.close()
            scratchLease.close()
        }
    }
}

internal fun ImageIoPageDecoder.chooseSubsampling(region: IntRect, request: TileRequest): Int {
    val fitWidth = region.width / request.targetWidth
    val fitHeight = region.height / request.targetHeight
    val fit = Integer.highestOneBit(maxOf(1, minOf(fitWidth, fitHeight)))
    val requested = Integer.highestOneBit(maxOf(1, request.key.sampleSize))
    return maxOf(fit, requested).coerceAtMost(MAX_SUBSAMPLING)
}

internal suspend fun <T> ImageIoPageDecoder.withReader(input: BoundedPageInput, block: suspend (ImageReader) -> T): T {
    val imageInput = ImageIO.createImageInputStream(input.input)
        ?: throw ReaderFailure.UnsupportedImage("no image input stream")
    try {
        val readers = ImageIO.getImageReaders(imageInput)
        if (!readers.hasNext()) throw ReaderFailure.UnsupportedImage("no ImageIO reader for the stream")
        val reader = readers.next()
        try {
            reader.input = imageInput
            return block(reader)
        } finally {
            reader.dispose()
        }
    } finally {
        imageInput.close()
    }
}

internal fun ImageIoPageDecoder.readFrameSize(reader: ImageReader): Pair<Int, Int> =
    guarded { reader.getWidth(0) } to guarded { reader.getHeight(0) }

internal fun ImageReader.readerImageFormat(): ReaderImageFormat = when (formatName.lowercase()) {
    "jpeg", "jpg" -> ReaderImageFormat.JPEG
    "png" -> ReaderImageFormat.PNG
    "gif" -> ReaderImageFormat.GIF
    "webp" -> ReaderImageFormat.WEBP
    "bmp", "wbmp" -> ReaderImageFormat.BMP
    "tif", "tiff", "bigtiff" -> ReaderImageFormat.TIFF
    else -> ReaderImageFormat.UNKNOWN
}

internal fun ImageIoPageDecoder.hasAlpha(reader: ImageReader): Boolean = guarded {
    val types = reader.getImageTypes(0)
    while (types.hasNext()) {
        if (types.next().colorModel.hasAlpha()) return@guarded true
    }
    false
}

internal fun ImageIoPageDecoder.gifCanvasSize(reader: ImageReader): Pair<Int, Int> {
    val frameSize = readFrameSize(reader)
    val streamMetadata = guarded { reader.streamMetadata } ?: return frameSize
    val root = runCatching { streamMetadata.getAsTree("javax_imageio_gif_stream_1.0") }
        .getOrNull() as? IIOMetadataNode ?: return frameSize
    val descriptor = root.getElementsByTagName("LogicalScreenDescriptor").item(0) as? IIOMetadataNode
        ?: return frameSize
    val width = descriptor.getAttribute("logicalScreenWidth").toIntOrNull() ?: return frameSize
    val height = descriptor.getAttribute("logicalScreenHeight").toIntOrNull() ?: return frameSize
    if (width <= 0 || height <= 0) return frameSize
    return maxOf(width, frameSize.first) to maxOf(height, frameSize.second)
}

internal fun ImageIoPageDecoder.gifDelayMillis(reader: ImageReader, frameIndex: Int): Long {
    val metadata = guarded { reader.getImageMetadata(frameIndex) }
    val root = runCatching { metadata.getAsTree("javax_imageio_gif_image_1.0") }
        .getOrNull() as? IIOMetadataNode ?: return GIF_MIN_DURATION_MILLIS
    val control = root.getElementsByTagName("GraphicControlExtension").item(0) as? IIOMetadataNode
        ?: return GIF_MIN_DURATION_MILLIS
    val hundredths = control.getAttribute("delayTime").toLongOrNull() ?: 0L
    return (hundredths * 10L).coerceIn(GIF_MIN_DURATION_MILLIS, GIF_MAX_DURATION_MILLIS)
}

internal fun ImageIoPageDecoder.gifRegionPeakBytes(width: Int, height: Int): Long {
    val canvas = checkedRasterBytes(width, height)
    // Canvas + frame intermediate + restore-to-previous snapshot + cropped output.
    return Math.multiplyExact(canvas, 4L)
}

/**
 * Region decode composites the full GIF canvas, so the canvas must satisfy the same
 * 16 MiB raster cap as a full decode; above it the canvas is never composited.
 */
internal fun ImageIoPageDecoder.gifRegionDecodeSupported(width: Int, height: Int): Boolean {
    if (checkedRasterBytes(width, height) > ReaderLimits.FULL_DECODE_MAX_BYTES) return false
    return gifRegionPeakBytes(width, height) <= budget.metrics.limitBytes
}

internal fun ImageIoPageDecoder.validateDimensions(width: Int, height: Int) {
    if (width <= 0 || height <= 0) throw ReaderFailure.CorruptImage()
    val largest = maxOf(width, height).toLong()
    if (largest > ReaderLimits.MAX_IMAGE_DIMENSION) {
        throw ReaderFailure.LimitExceeded("image dimension", ReaderLimits.MAX_IMAGE_DIMENSION.toLong(), largest)
    }
    val pixels = width.toLong() * height.toLong()
    if (pixels > ReaderLimits.MAX_IMAGE_PIXELS) {
        throw ReaderFailure.LimitExceeded("image pixel count", ReaderLimits.MAX_IMAGE_PIXELS, pixels)
    }
    checkedRasterBytes(width, height)
}

internal fun ImageIoPageDecoder.checkedRasterBytes(width: Int, height: Int): Long = try {
    Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
} catch (overflow: ArithmeticException) {
    throw ReaderFailure.LimitExceeded("image raster", Long.MAX_VALUE, Long.MAX_VALUE)
}

internal fun <T> ImageIoPageDecoder.guarded(block: () -> T): T = try {
    block()
} catch (error: IOException) {
    throw ReaderFailure.CorruptImage(error)
} catch (error: CancellationException) {
    throw error
} catch (error: RuntimeException) {
    // Documented JDK GIF/PNG reader bugs surface as unchecked exceptions on malformed
    // streams; a corrupt page must become a typed retryable failure, never a crash.
    throw ReaderFailure.CorruptImage(error)
}

const val GIF_MIN_DURATION_MILLIS: Long = 20L
const val GIF_MAX_DURATION_MILLIS: Long = 10_000L
const val MAX_SUBSAMPLING: Int = 64
internal const val DISPOSAL_RESTORE_TO_BACKGROUND = "restoreToBackgroundColor"
internal const val DISPOSAL_RESTORE_TO_PREVIOUS = "restoreToPrevious"
