package mihon.reader.image

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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** Pure-Java APNG decoder. Frames are reconstructed as ordinary PNGs and composited on the canvas. */
class ApngPageDecoder(
    internal val budget: BoundedReaderMemoryBudget,
) : PageDecoder {
    override suspend fun probe(input: BoundedPageInput): ImageMetadata = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            val animation = parse(pageInput)
            ImageMetadata(
                width = animation.width,
                height = animation.height,
                frameCount = animation.frames.size,
                frameDurationsMillis = animation.frames.map { it.control.durationMillis },
                supportsRegionDecode = false,
                format = ReaderImageFormat.PNG,
                hasAlpha = animation.hasAlpha,
            )
        }
    }

    override suspend fun decodeFull(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
    ): DecodedTile = decode(input, metadata, frameId, null)

    override suspend fun decodeRegion(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        request: TileRequest,
    ): DecodedTile = decode(input, metadata, request.key.frameId ?: FrameId(request.key.pageId, 0), request)

    private suspend fun decode(
        input: BoundedPageInput,
        metadata: ImageMetadata,
        frameId: FrameId,
        request: TileRequest?,
    ): DecodedTile = withContext(Dispatchers.IO) {
        input.use { pageInput ->
            val animation = parse(pageInput)
            if (animation.width != metadata.width || animation.height != metadata.height) {
                throw ReaderFailure.CorruptImage()
            }
            val frameIndex = frameId.frameIndex
            if (frameIndex !in animation.frames.indices) throw ReaderFailure.CorruptImage()
            val requestedRegion = request?.key?.bounds
            val region = requestedRegion?.let {
                val right = it.right.coerceAtMost(animation.width)
                val bottom = it.bottom.coerceAtMost(animation.height)
                if (it.left >= right || it.top >= bottom) {
                    throw ReaderFailure.RegionUnavailable("region lies outside the image")
                }
                IntRect(it.left, it.top, right, bottom)
            }
            val sampleSize = request?.let { chooseSubsampling(region!!, it) } ?: 1
            val outputWidth = region?.let { (it.width + sampleSize - 1) / sampleSize } ?: animation.width
            val outputHeight = region?.let { (it.height + sampleSize - 1) / sampleSize } ?: animation.height
            val outputBytes = checkedRasterBytes(outputWidth, outputHeight)
            val canvasBytes = checkedRasterBytes(animation.width, animation.height)
            if (canvasBytes > ReaderLimits.FULL_DECODE_MAX_BYTES) {
                throw ReaderFailure.LimitExceeded(
                    "APNG composition canvas",
                    ReaderLimits.FULL_DECODE_MAX_BYTES,
                    canvasBytes,
                )
            }
            val outputLease = budget.reserve(MemoryKind.DECODED_OUTPUT, outputBytes)
            val scratchLease = reserveScratch(outputLease, canvasBytes * 3L)
            var canvas: BufferedImage? = null
            var snapshot: BufferedImage? = null
            var output: BufferedImage? = null
            var failed = true
            try {
                canvas = BufferedImage(animation.width, animation.height, BufferedImage.TYPE_INT_ARGB_PRE)
                var previousControl: FrameControl? = null
                for (index in 0..frameIndex) {
                    currentCoroutineContext().ensureActive()
                    val frame = animation.frames[index]
                    previousControl?.let { previous ->
                        when (previous.disposeOp) {
                            DISPOSE_BACKGROUND -> clear(canvas, previous.bounds)
                            DISPOSE_PREVIOUS -> {
                                val restore = snapshot ?: throw ReaderFailure.CorruptImage()
                                draw(canvas, restore, 0, 0, AlphaComposite.Src)
                                restore.flush()
                                snapshot = null
                            }
                        }
                    }
                    if (frame.control.disposeOp == DISPOSE_PREVIOUS) {
                        snapshot?.flush()
                        snapshot = copyOf(canvas)
                    }
                    val decoded = ImageIO.read(ByteArrayInputStream(frame.pngBytes))
                        ?: throw ReaderFailure.CorruptImage()
                    try {
                        if (decoded.width != frame.control.width || decoded.height != frame.control.height) {
                            throw ReaderFailure.CorruptImage()
                        }
                        if (frame.control.blendOp == BLEND_SOURCE) clear(canvas, frame.control.bounds)
                        draw(
                            canvas,
                            decoded,
                            frame.control.xOffset,
                            frame.control.yOffset,
                            if (frame.control.blendOp == BLEND_SOURCE) AlphaComposite.Src else AlphaComposite.SrcOver,
                        )
                    } finally {
                        decoded.flush()
                    }
                    previousControl = frame.control
                }
                output = if (region == null && sampleSize == 1) {
                    canvas.also { canvas = null }
                } else {
                    val result = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB_PRE)
                    val graphics = result.createGraphics()
                    try {
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(
                            canvas,
                            0,
                            0,
                            outputWidth,
                            outputHeight,
                            region!!.left,
                            region.top,
                            region.right,
                            region.bottom,
                            null,
                        )
                    } finally {
                        graphics.dispose()
                    }
                    canvas?.flush()
                    canvas = null
                    result
                }
                snapshot?.flush()
                snapshot = null
                scratchLease.close()
                val key = request?.key ?: TileKey(
                    frameId.pageId,
                    frameId,
                    IntRect(0, 0, animation.width, animation.height),
                )
                val tile = BudgetedDecodedTile(key, output, outputLease)
                output = null
                failed = false
                tile
            } finally {
                if (failed) {
                    output?.flush()
                    canvas?.flush()
                    snapshot?.flush()
                    scratchLease.close()
                    outputLease.close()
                }
            }
        }
    }
}
