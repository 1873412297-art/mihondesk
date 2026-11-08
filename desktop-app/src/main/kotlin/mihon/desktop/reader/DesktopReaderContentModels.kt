package mihon.desktop.reader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import mihon.desktop.extension.OnlineChapterSource
import mihon.desktop.ui.reader.AnimationVisibilityReporter
import mihon.desktop.ui.reader.ComposeTileBridge
import mihon.desktop.ui.reader.IntrinsicPageSizeCache
import mihon.desktop.ui.reader.ReaderAnimatedFrame
import mihon.desktop.ui.reader.SmartBorderCropper
import mihon.reader.cache.CacheMetrics
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TilePlanner
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.prefetch.PageLoadCoordinator
import mihon.reader.prefetch.PageLoadPriority
import mihon.reader.prefetch.PrefetchPolicy
import mihon.reader.session.AdjacentChapterWarmup
import mihon.reader.session.AnimationCoordinator
import mihon.reader.session.AnimationProbe
import mihon.reader.session.ReaderContentPipeline
import mihon.reader.session.ReaderContentPosition
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import java.util.concurrent.atomic.AtomicBoolean

internal data class LoadedDesktopTile(
    val tile: WeightedTileCache.LoadedTile,
    val metadata: ImageMetadata,
)

/** Compose-facing content handle backed by the same coordinator used by its reader session. */
class DesktopReaderContent internal constructor(
    private val pipeline: DesktopReaderContentPipeline,
    internal val bridge: ComposeTileBridge,
    val pageSizes: IntrinsicPageSizeCache,
    private val animationCoordinator: AnimationCoordinator,
) : AnimationVisibilityReporter {
    val selectedAnimationFrame: StateFlow<FrameId?> = animationCoordinator.selectedFrame

    fun startAnimation(pageId: PageId, metadata: ImageMetadata) {
        if (!metadata.isAnimated) return
        animationCoordinator.start(
            pageId,
            AnimationProbe(metadata.frameCount, metadata.frameDurationsMillis),
        )
    }

    fun stopAnimation(pageId: PageId) {
        if (selectedAnimationFrame.value?.pageId == pageId) {
            animationCoordinator.cancelForPageOrChapterChange()
        }
    }

    override fun setContentVisible(visible: Boolean) {
        animationCoordinator.setContentVisible(visible)
    }

    override fun setForeground(foreground: Boolean) {
        animationCoordinator.setForeground(foreground)
    }

    suspend fun loadAnimatedFrame(frameId: FrameId): ReaderAnimatedFrame {
        val loaded = pipeline.loadTile(frameId.pageId, frameId.frameIndex)
        val lease = if (loaded.tile.resident) null else AutoCloseable { loaded.tile.tile.close() }
        return ReaderAnimatedFrame(loaded.tile.tile.key, loaded.tile.tile.image, lease)
    }

    suspend fun loadFrame(
        pageId: PageId,
        frameIndex: Int,
        cropBorders: Boolean = false,
    ): DesktopReaderPageFrame = withContext(Dispatchers.IO) {
        val loaded = pipeline.loadTile(pageId, frameIndex)
        try {
            val imageToBridge = if (cropBorders) {
                SmartBorderCropper.crop(loaded.tile.tile.image)
            } else {
                loaded.tile.tile.image
            }
            val wasCropped = imageToBridge.width != loaded.tile.tile.image.width ||
                imageToBridge.height != loaded.tile.tile.image.height
            val sourceKey = loaded.tile.tile.key
            val bridgeKey = if (wasCropped) {
                sourceKey.copy(bounds = IntRect(1, 1, loaded.metadata.width, loaded.metadata.height))
            } else {
                sourceKey
            }
            val displayMetadata = if (wasCropped) {
                loaded.metadata.copy(
                    width = Math.multiplyExact(imageToBridge.width, sourceKey.sampleSize),
                    height = Math.multiplyExact(imageToBridge.height, sourceKey.sampleSize),
                )
            } else {
                loaded.metadata
            }
            pageSizes.record(pageId, displayMetadata)
            DesktopReaderPageFrame(bridge.acquire(bridgeKey, imageToBridge), displayMetadata)
        } finally {
            if (!loaded.tile.resident) loaded.tile.tile.close()
        }
    }

    suspend fun loadRegionTiles(
        pageId: PageId,
        visibleBounds: IntRect,
        sampleSize: Int,
    ): DesktopReaderTileSet = withContext(Dispatchers.IO) {
        val loaded = pipeline.loadRegionTiles(pageId, visibleBounds, sampleSize)
        val bridged = ArrayList<DesktopReaderTile>(loaded.size)
        try {
            loaded.forEach { item ->
                bridged += DesktopReaderTile(
                    bounds = item.tile.tile.key.bounds,
                    tile = bridge.acquire(item.tile.tile.key, item.tile.tile.image),
                )
            }
            DesktopReaderTileSet(bridged)
        } catch (failure: Throwable) {
            bridged.forEach { it.close() }
            throw failure
        } finally {
            loaded.filterNot { it.tile.resident }.forEach { it.tile.tile.close() }
        }
    }

    fun releaseRegionTiles(pageId: PageId) {
        pipeline.releaseRegionTiles(pageId)
    }
}

data class DesktopReaderPageFrame(
    val tile: ComposeTileBridge.BridgeTile,
    val metadata: ImageMetadata,
)

data class DesktopReaderTile(
    val bounds: IntRect,
    val tile: ComposeTileBridge.BridgeTile,
) : AutoCloseable {
    override fun close() = tile.close()
}

class DesktopReaderTileSet(
    val tiles: List<DesktopReaderTile>,
) : AutoCloseable {
    override fun close() = tiles.forEach { it.close() }
}

data class DesktopReaderHandle(
    val session: mihon.reader.session.ReaderSession,
    val content: DesktopReaderContent,
)
