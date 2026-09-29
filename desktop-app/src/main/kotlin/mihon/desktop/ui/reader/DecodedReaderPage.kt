package mihon.desktop.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.DesktopReaderContent
import mihon.desktop.reader.DesktopReaderPageFrame
import mihon.desktop.reader.ReaderColorFilter
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.SplitSide
import kotlin.math.roundToInt

val LocalReaderColorFilter = staticCompositionLocalOf { ReaderColorFilter.NONE }
val LocalReaderComposeColorFilter = staticCompositionLocalOf<ColorFilter?> { null }
val LocalReaderCropBorders = staticCompositionLocalOf { false }
val LocalReaderForeground = staticCompositionLocalOf { true }
val LocalReaderSelectedPage = staticCompositionLocalOf<PageId?> { null }

/** Each composed page owns its display lease; leaving a spread/list releases that lease. */
@Composable
fun DecodedReaderPage(
    content: DesktopReaderContent,
    page: PageDescriptor,
    modifier: Modifier,
    colorFilter: ReaderColorFilter = LocalReaderColorFilter.current,
    cropBorders: Boolean = LocalReaderCropBorders.current,
) {
    val strings = LocalStrings.current
    val pageSizeSink = LocalReaderPageSizeSink.current
    val imageStore = LocalReaderPageImageStore.current
    val readerForeground = LocalReaderForeground.current
    val selectedPage = LocalReaderSelectedPage.current
    val renderContext = LocalReaderPageRenderContext.current
    // The animated frame renderer has no split/rotation transform. Keep the first decoded
    // frame for these pages so both halves retain their correct crop and reading order.
    val shouldAnimate = readerForeground && selectedPage == page.id && page.splitSide == null && !page.rotated
    val selectedAnimationFrame by content.selectedAnimationFrame.collectAsState()
    var frame by remember(page.id, cropBorders) { mutableStateOf<DesktopReaderPageFrame?>(null) }
    var failure by remember(page.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(frame, imageStore) {
        val image = frame?.tile?.image
        if (image != null) {
            imageStore?.put(page.id, image)
        } else {
            imageStore?.remove(page.id)
        }
    }
    DisposableEffect(page.id, imageStore) {
        onDispose { imageStore?.remove(page.id) }
    }

    // Promote probed intrinsic dimensions into reader layout even before the full frame decodes.
    LaunchedEffect(content, page.id, pageSizeSink) {
        val sink = pageSizeSink ?: return@LaunchedEffect
        content.pageSizes.sizes.collect { sizes ->
            sizes[page.id]?.let { size -> sink.onPageSize(page.id, size) }
        }
    }

    LaunchedEffect(content, page.id, cropBorders) {
        var owned: DesktopReaderPageFrame? = null
        try {
            val replacement = content.loadFrame(page.id, frameIndex = 0, cropBorders)
            pageSizeSink?.onPageSize(
                page.id,
                PageSize(replacement.metadata.width, replacement.metadata.height),
            )
            owned = replacement
            frame = replacement
            kotlinx.coroutines.awaitCancellation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failure = error.message ?: "Unable to decode page"
        } finally {
            frame = null
            owned?.tile?.close()
        }
    }

    val metadata = frame?.metadata
    LaunchedEffect(content, page.id, shouldAnimate, metadata) {
        if (shouldAnimate && metadata?.isAnimated == true) {
            content.startAnimation(page.id, metadata)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                content.stopAnimation(page.id)
            }
        } else {
            content.stopAnimation(page.id)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = frame
        val animatedFrame = selectedAnimationFrame?.takeIf { it.pageId == page.id && shouldAnimate }
        val effectiveColorFilter = LocalReaderComposeColorFilter.current ?: colorFilter.toComposeColorFilter()
        when {
            animatedFrame != null -> AnimatedPage(
                selectedFrame = animatedFrame,
                loadFrame = content::loadAnimatedFrame,
                bridge = content.bridge,
                contentVisible = true,
                foreground = readerForeground,
                visibilityReporter = content,
                modifier = Modifier.matchParentSize(),
            )
            current != null && renderContext != null &&
                current.tile.key.sampleSize > renderContext.sampleSize &&
                !current.metadata.isAnimated && !cropBorders &&
                page.splitSide == null && !page.rotated -> TiledReaderPage(
                content = content,
                pageId = page.id,
                fallback = current,
                context = renderContext,
                colorFilter = effectiveColorFilter,
                modifier = Modifier.matchParentSize().testTag("reader-decoded-${page.id.entryName}"),
            )
            current != null -> {
                val splitSide = page.splitSide
                val rotated = page.rotated
                val tag = if (splitSide != null) {
                    "reader-decoded-${page.id.entryName}-${splitSide.name.lowercase()}"
                } else {
                    "reader-decoded-${page.id.entryName}"
                }
                if (splitSide != null || rotated) {
                    Image(
                        painter = remember(current.tile.image, splitSide, rotated) {
                            SplitImagePainter(current.tile.image, splitSide, rotated)
                        },
                        contentDescription = strings.text(UiText.DecodedPage, page.id.entryName),
                        modifier = Modifier.matchParentSize().testTag(tag),
                        contentScale = ContentScale.Fit,
                        colorFilter = effectiveColorFilter,
                    )
                } else {
                    Image(
                        bitmap = current.tile.image,
                        contentDescription = strings.text(UiText.DecodedPage, page.id.entryName),
                        modifier = Modifier.matchParentSize().testTag(tag),
                        contentScale = ContentScale.Fit,
                        colorFilter = effectiveColorFilter,
                    )
                }
            }
            failure != null -> Text(requireNotNull(failure))
            else -> CircularProgressIndicator()
        }
    }
}

internal class SplitImagePainter(
    private val image: ImageBitmap,
    private val splitSide: SplitSide? = null,
    private val rotated: Boolean = false,
) : Painter() {
    private var alpha: Float = 1.0f
    private var colorFilter: ColorFilter? = null

    private val srcOffset: IntOffset
    private val srcSize: IntSize

    init {
        val imgWidth = image.width
        val imgHeight = image.height
        when (splitSide) {
            SplitSide.LEFT -> {
                val halfWidth = imgWidth / 2
                srcOffset = IntOffset.Zero
                srcSize = IntSize(halfWidth, imgHeight)
            }
            SplitSide.RIGHT -> {
                val halfWidth = imgWidth / 2
                val remaining = imgWidth - halfWidth
                srcOffset = IntOffset(halfWidth, 0)
                srcSize = IntSize(remaining, imgHeight)
            }
            null -> {
                srcOffset = IntOffset.Zero
                srcSize = IntSize(imgWidth, imgHeight)
            }
        }
    }

    override val intrinsicSize: Size = if (rotated) {
        Size(srcSize.height.toFloat(), srcSize.width.toFloat())
    } else {
        Size(srcSize.width.toFloat(), srcSize.height.toFloat())
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        if (rotated) {
            rotate(90f, pivot = center) {
                val dstOffset = IntOffset(
                    ((size.width - size.height) / 2f).roundToInt(),
                    ((size.height - size.width) / 2f).roundToInt(),
                )
                val dstSize = IntSize(size.height.roundToInt(), size.width.roundToInt())
                drawImage(
                    image = image,
                    srcOffset = srcOffset,
                    srcSize = srcSize,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = alpha,
                    colorFilter = colorFilter,
                )
            }
        } else {
            drawImage(
                image = image,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = alpha,
                colorFilter = colorFilter,
            )
        }
    }
}
