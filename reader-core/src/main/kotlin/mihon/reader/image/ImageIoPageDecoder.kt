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

class ImageIoPageDecoder(
    internal val budget: BoundedReaderMemoryBudget,
) : PageDecoder {

    override suspend fun probe(input: BoundedPageInput): ImageMetadata = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val format = reader.readerImageFormat()
                val isGif = format == ReaderImageFormat.GIF
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                validateDimensions(width, height)
                val frameCount = if (isGif) {
                    guarded { reader.getNumImages(true) }.coerceAtLeast(1)
                } else {
                    1
                }
                val durations = if (isGif && frameCount > 1) {
                    (0 until frameCount).map { gifDelayMillis(reader, it) }
                } else {
                    List(frameCount) { 0L }
                }
                val supportsRegion = !isGif || gifRegionDecodeSupported(width, height)
                ImageMetadata(
                    width = width,
                    height = height,
                    frameCount = frameCount,
                    frameDurationsMillis = durations,
                    supportsRegionDecode = supportsRegion,
                    format = format,
                    hasAlpha = hasAlpha(reader),
                )
            }
        }
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val isGif = reader.formatName.equals("gif", ignoreCase = true)
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                if (width != metadata.width || height != metadata.height) throw ReaderFailure.CorruptImage()
                val outputBytes = checkedRasterBytes(width, height)
                if (outputBytes > ReaderLimits.FULL_DECODE_MAX_BYTES) {
                    throw ReaderFailure.LimitExceeded(
                        "full decode raster",
                        ReaderLimits.FULL_DECODE_MAX_BYTES,
                        outputBytes,
                    )
                }
                val key = TileKey(
                    pageId = frameId.pageId,
                    frameId = frameId.takeIf { metadata.isAnimated },
                    bounds = IntRect(0, 0, width, height),
                )
                if (isGif) {
                    decodeGifFrame(reader, width, height, frameId.frameIndex, region = null, key = key)
                } else {
                    require(frameId.frameIndex == 0) { "still images only expose frame zero" }
                    decodeStillFull(reader, width, height, key)
                }
            }
        }
    }

    private suspend fun decodeStillFull(
        reader: ImageReader,
        width: Int,
        height: Int,
        key: TileKey,
    ): DecodedTile {
        val rasterBytes = checkedRasterBytes(width, height)
        val (finalLease, scratchLease) = reservePeak(rasterBytes, rasterBytes)
        var decoded: BufferedImage? = null
        var normalized: BufferedImage? = null
        var failed = true
        try {
            decoded = guarded { reader.read(0) } ?: throw ReaderFailure.CorruptImage()
            if (decoded.width > width || decoded.height > height) {
                throw ReaderFailure.CorruptImage()
            }
            normalized = normalizeToPremultipliedArgb(decoded)
            if (normalized !== decoded) {
                decoded.flush()
                decoded = null
            }
            currentCoroutineContext().ensureActive()
            scratchLease.close()
            val tile = BudgetedDecodedTile(key, normalized, finalLease)
            normalized = null
            decoded = null
            failed = false
            return tile
        } finally {
            if (failed) {
                normalized?.flush()
                if (decoded !== normalized) decoded?.flush()
                finalLease.close()
                scratchLease.close()
            }
        }
    }

    private suspend fun decodeGifFrame(
        reader: ImageReader,
        canvasWidth: Int,
        canvasHeight: Int,
        frameIndex: Int,
        region: IntRect?,
        key: TileKey,
    ): DecodedTile {
        val frameCount = guarded { reader.getNumImages(true) }
        require(frameIndex in 0 until frameCount) { "frame index $frameIndex out of $frameCount frames" }
        val infos = (0..frameIndex).map { gifFrameInfo(reader, it) }
        val needsSnapshot = (0 until frameIndex).any { infos[it].disposal == DISPOSAL_RESTORE_TO_PREVIOUS }
        val canvasBytes = checkedRasterBytes(canvasWidth, canvasHeight)
        val outputBytes = region?.let { checkedRasterBytes(it.width, it.height) } ?: canvasBytes
        // Canvas + frame intermediate (+ restore-to-previous snapshot) before the output crop.
        val scratchBytes = Math.multiplyExact(canvasBytes, if (needsSnapshot) 3L else 2L)
        val (finalLease, scratchLease) = reservePeak(outputBytes, scratchBytes)
        var canvas: BufferedImage? = null
        var snapshot: BufferedImage? = null
        var frame: BufferedImage? = null
        var output: BufferedImage? = null
        var failed = true
        try {
            canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            val graphics = canvas.createGraphics()
            try {
                var pendingClear: Rectangle? = null
                var pendingRestore = false
                for (index in 0..frameIndex) {
                    if (pendingClear != null) {
                        graphics.composite = AlphaComposite.Clear
                        graphics.fillRect(pendingClear.x, pendingClear.y, pendingClear.width, pendingClear.height)
                        graphics.composite = AlphaComposite.SrcOver
                        pendingClear = null
                    }
                    if (pendingRestore) {
                        val restore = snapshot ?: throw ReaderFailure.CorruptImage()
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(restore, 0, 0, null)
                        graphics.composite = AlphaComposite.SrcOver
                        pendingRestore = false
                    }
                    val info = infos[index]
                    if (index < frameIndex && info.disposal == DISPOSAL_RESTORE_TO_PREVIOUS) {
                        snapshot?.flush()
                        val copy = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                        val copyGraphics = copy.createGraphics()
                        copyGraphics.composite = AlphaComposite.Src
                        copyGraphics.drawImage(canvas, 0, 0, null)
                        copyGraphics.dispose()
                        snapshot = copy
                    }
                    frame = guarded { reader.read(index) } ?: throw ReaderFailure.CorruptImage()
                    graphics.drawImage(frame, info.left, info.top, null)
                    frame.flush()
                    frame = null
                    when (info.disposal) {
                        DISPOSAL_RESTORE_TO_BACKGROUND ->
                            pendingClear = Rectangle(info.left, info.top, info.width, info.height)
                        DISPOSAL_RESTORE_TO_PREVIOUS -> pendingRestore = true
                    }
                }
            } finally {
                graphics.dispose()
            }
            snapshot?.flush()
            snapshot = null
            output = if (region != null) {
                val crop = BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB_PRE)
                val cropGraphics = crop.createGraphics()
                try {
                    cropGraphics.drawImage(
                        canvas,
                        0,
                        0,
                        region.width,
                        region.height,
                        region.left,
                        region.top,
                        region.right,
                        region.bottom,
                        null,
                    )
                } finally {
                    cropGraphics.dispose()
                }
                canvas.flush()
                canvas = null
                crop
            } else {
                canvas.also { canvas = null }
            }
            currentCoroutineContext().ensureActive()
            scratchLease.close()
            val tile = BudgetedDecodedTile(key, output, finalLease)
            output = null
            failed = false
            return tile
        } finally {
            if (failed) {
                canvas?.flush()
                snapshot?.flush()
                frame?.flush()
                output?.flush()
                finalLease.close()
                scratchLease.close()
            }
        }
    }

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        input.use { pageInput ->
            withReader(pageInput) { reader ->
                val isGif = reader.formatName.equals("gif", ignoreCase = true)
                val (width, height) = if (isGif) gifCanvasSize(reader) else readFrameSize(reader)
                if (width != metadata.width || height != metadata.height) throw ReaderFailure.CorruptImage()
                val bounds = request.key.bounds
                val right = bounds.right.coerceAtMost(width)
                val bottom = bounds.bottom.coerceAtMost(height)
                if (bounds.left >= right || bounds.top >= bottom) {
                    throw ReaderFailure.RegionUnavailable("region lies outside the image")
                }
                val region = IntRect(bounds.left, bounds.top, right, bottom)
                if (isGif) {
                    if (checkedRasterBytes(width, height) > ReaderLimits.FULL_DECODE_MAX_BYTES) {
                        throw ReaderFailure.RegionUnavailable(
                            "gif composition canvas exceeds the full decode raster limit",
                        )
                    }
                    if (!metadata.supportsRegionDecode) {
                        throw ReaderFailure.RegionUnavailable(
                            "gif composition canvas exceeds the reader memory budget",
                        )
                    }
                    decodeGifFrame(
                        reader,
                        width,
                        height,
                        request.key.frameId?.frameIndex ?: 0,
                        region = region,
                        key = request.key,
                    )
                } else {
                    decodeStillRegion(reader, region, request)
                }
            }
        }
    }
}
