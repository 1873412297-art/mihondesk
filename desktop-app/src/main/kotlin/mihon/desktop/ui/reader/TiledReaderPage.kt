package mihon.desktop.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.desktop.reader.DesktopReaderContent
import mihon.desktop.reader.DesktopReaderPageFrame
import mihon.desktop.reader.DesktopReaderTileSet
import mihon.reader.model.PageId
import kotlin.math.roundToInt

/** Pan/zoom changes the visible bounds every frame; wait for the gesture to settle before decoding. */
private const val TILE_RELOAD_DEBOUNCE_MILLIS = 60L

@Composable
internal fun TiledReaderPage(
    content: DesktopReaderContent,
    pageId: PageId,
    fallback: DesktopReaderPageFrame,
    context: ReaderPageRenderContext,
    colorFilter: ColorFilter?,
    modifier: Modifier,
) {
    var tileSet by remember(pageId) { mutableStateOf<DesktopReaderTileSet?>(null) }
    val latestContext = rememberUpdatedState(context)
    LaunchedEffect(content, pageId) {
        snapshotFlow { latestContext.value.visibleImageBounds to latestContext.value.sampleSize }
            .distinctUntilChanged()
            .debounce(TILE_RELOAD_DEBOUNCE_MILLIS)
            .collectLatest { (bounds, sampleSize) ->
                val replacement = content.loadRegionTiles(pageId, bounds, sampleSize)
                val previous = tileSet
                tileSet = replacement
                previous?.close()
            }
    }
    DisposableEffect(content, pageId) {
        onDispose {
            tileSet?.close()
            tileSet = null
            content.releaseRegionTiles(pageId)
        }
    }

    Canvas(modifier) {
        drawImage(
            image = fallback.tile.image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = colorFilter,
        )
        tileSet?.tiles?.forEach { region ->
            drawRegion(
                image = region.tile.image,
                left = region.bounds.left,
                top = region.bounds.top,
                width = region.bounds.width,
                height = region.bounds.height,
                context = context,
                colorFilter = colorFilter,
            )
        }
    }
}

private fun DrawScope.drawRegion(
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    context: ReaderPageRenderContext,
    colorFilter: ColorFilter?,
) {
    val scaleX = size.width / context.imageWidth
    val scaleY = size.height / context.imageHeight
    drawImage(
        image = image,
        dstOffset = IntOffset((left * scaleX).roundToInt(), (top * scaleY).roundToInt()),
        dstSize = IntSize((width * scaleX).roundToInt(), (height * scaleY).roundToInt()),
        colorFilter = colorFilter,
    )
}
