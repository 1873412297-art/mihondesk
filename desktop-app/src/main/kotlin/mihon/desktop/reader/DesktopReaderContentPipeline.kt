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

/** The session-owned production bridge from reader state to bounded decode/cache coordination. */
class DesktopReaderContentPipeline(
    private val decoder: PageDecoder,
    private val cache: WeightedTileCache,
    private val scope: CoroutineScope,
    private val sourceFactory: ChapterSourceFactory,
    private val maxFullPagePixels: Long = DEFAULT_MAX_FULL_PAGE_PIXELS,
    private val preloadPages: () -> Int = { PrefetchPolicy.AHEAD_PAGES },
) : ReaderContentPipeline {
    private val lock = Any()
    private var coordinator: PageLoadCoordinator? = null
    private var warmupRequest: AdjacentChapterWarmup? = null
    private var warmupJob: Job? = null
    private var extendedPrefetchJob: Job? = null
    private var chapterSource: ChapterSource? = null
    private var chapterPages: List<PageDescriptor> = emptyList()
    private val preloadFailures = HashSet<PageId>()
    private val closed = AtomicBoolean(false)

    override suspend fun open(source: ChapterSource): List<PageDescriptor> {
        check(!closed.get()) { "reader content pipeline is closed" }
        closeChapter()
        val next = newCoordinator()
        synchronized(lock) { coordinator = next }
        return try {
            val opened = next.openChapter(source)
            synchronized(lock) {
                chapterPages = opened
                chapterSource = source
                preloadFailures.clear()
            }
            opened
        } catch (failure: Throwable) {
            synchronized(lock) { if (coordinator === next) coordinator = null }
            next.close()
            throw failure
        }
    }

    override fun updatePosition(position: ReaderContentPosition) {
        val active = activeCoordinator()
        if (position.foreground && position.contentVisible) {
            active.updatePosition(
                selectedIndex = position.selectedIndex,
                visiblePages = position.visiblePages,
                mode = position.mode,
                direction = position.direction,
            )
            val requested = DesktopPreloadPolicy.clamp(preloadPages())
            if (requested == PrefetchPolicy.AHEAD_PAGES) {
                // Native coordinator path: keep its proven prefetch cancellation semantics.
                cancelExtendedPrefetch()
            } else {
                // The core window is hardcoded, so a custom window is scheduled here instead:
                // drop the coordinator's native prefetch and run ours for the requested size.
                active.cancelPrefetch()
                scheduleExtendedPrefetch(active, position, requested)
            }
        } else {
            active.cancelPrefetch()
            cancelExtendedPrefetch()
        }
    }

    override suspend fun loadVisible(pageId: PageId): AutoCloseable? {
        val loaded = loadTile(pageId, frameIndex = 0)
        return if (loaded.tile.resident) null else AutoCloseable { loaded.tile.tile.close() }
    }

    override suspend fun retry(pageId: PageId): AutoCloseable? {
        val loaded = activeCoordinator().retry(pageId)
        return if (loaded.resident) null else AutoCloseable { loaded.tile.close() }
    }

    override fun metrics(): CacheMetrics = cache.metrics

    override fun warmAdjacent(request: AdjacentChapterWarmup?) {
        if (closed.get()) return
        val prior = synchronized(lock) {
            if (warmupRequest == request) return
            warmupRequest = request
            val old = warmupJob
            warmupJob = request?.let { target ->
                scope.launch { runWarmup(target) }
            }
            old
        }
        prior?.cancel()
    }

    override fun closeChapter() {
        val (active, warm) = synchronized(lock) {
            val oldCoordinator = coordinator
            coordinator = null
            val oldWarmup = warmupJob
            warmupJob = null
            warmupRequest = null
            chapterPages = emptyList()
            chapterSource = null
            preloadFailures.clear()
            oldCoordinator to oldWarmup
        }
        cancelExtendedPrefetch()
        warm?.cancel()
        active?.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeChapter()
    }

    internal suspend fun loadTile(pageId: PageId, frameIndex: Int): LoadedDesktopTile {
        val active = activeCoordinator()
        val loaded = active.loadVisible(pageId, frameIndex)
        val metadata = requireNotNull(active.loadedMetadata(pageId)) { "loaded page metadata is missing" }
        return LoadedDesktopTile(loaded, metadata)
    }

    internal suspend fun loadRegionTiles(
        pageId: PageId,
        visibleBounds: IntRect,
        sampleSize: Int,
    ): List<LoadedDesktopTile> {
        val active = activeCoordinator()
        val metadata = requireNotNull(active.loadedMetadata(pageId)) { "loaded page metadata is missing" }
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = metadata.width,
            imageHeight = metadata.height,
            visible = visibleBounds,
            sampleSize = sampleSize,
        )
        active.retainVisibleRegions(pageId, requests.mapTo(HashSet()) { it.key })
        val loaded = ArrayList<LoadedDesktopTile>(requests.size)
        try {
            requests.forEach { request ->
                loaded += LoadedDesktopTile(
                    active.loadRegion(pageId, request.key.bounds, sampleSize = request.key.sampleSize),
                    metadata,
                )
            }
            return loaded
        } catch (failure: Throwable) {
            loaded.filterNot { it.tile.resident }.forEach { it.tile.tile.close() }
            throw failure
        }
    }

    internal fun releaseRegionTiles(pageId: PageId) {
        val active = synchronized(lock) { coordinator } ?: return
        runCatching { active.retainVisibleRegions(pageId, emptySet()) }
    }

    internal suspend fun loadAnimationLease(frameId: FrameId): AutoCloseable? {
        val loaded = loadTile(frameId.pageId, frameId.frameIndex).tile
        return if (loaded.resident) null else AutoCloseable { loaded.tile.close() }
    }

    private fun activeCoordinator(): PageLoadCoordinator = synchronized(lock) {
        check(!closed.get()) { "reader content pipeline is closed" }
        checkNotNull(coordinator) { "reader content pipeline has no open chapter" }
    }

    private fun cancelExtendedPrefetch() {
        val job = synchronized(lock) {
            val current = extendedPrefetchJob
            extendedPrefetchJob = null
            current
        }
        job?.cancel()
    }

    /**
     * Launches a background prefetch for the user-configured window. Runs only when the
     * requested size differs from the core default; the coordinator's native prefetch is
     * cancelled first so the two windows never double-load. Jobs are superseded per position
     * update and share the chapter source and tile cache with visible loads, so identical
     * [TileKey]s single-flight instead of decoding twice.
     */
    private fun scheduleExtendedPrefetch(
        coordinator: PageLoadCoordinator,
        position: ReaderContentPosition,
        aheadPages: Int,
    ) {
        val snapshot = synchronized(lock) {
            val pages = chapterPages
            if (pages.isEmpty()) null else pages to chapterSource
        } ?: return
        val pages = snapshot.first
        val source = snapshot.second ?: return
        val visibleSet = position.visiblePages.toSet()
        val wanted = DesktopPreloadPolicy.plan(
            pageCount = pages.size,
            selectedIndex = position.selectedIndex,
            mode = position.mode,
            direction = position.direction,
            aheadPages = aheadPages,
        )
            .map { pages[it].id }
            .filter { it !in visibleSet }
        if (wanted.isEmpty()) {
            cancelExtendedPrefetch()
            return
        }
        val previous = synchronized(lock) {
            val current = extendedPrefetchJob
            extendedPrefetchJob = scope.launch(PageLoadPriority { false }) {
                runExtendedPrefetch(coordinator, source, wanted)
            }
            current
        }
        previous?.cancel()
    }

    private suspend fun runExtendedPrefetch(
        coordinator: PageLoadCoordinator,
        source: ChapterSource,
        pageIds: List<PageId>,
    ) {
        // Let visible demand queued behind this job register first, mirroring the core prefetch.
        yield()
        for (pageId in pageIds) {
            currentCoroutineContext().ensureActive()
            val alreadyFailed = synchronized(lock) { pageId in preloadFailures }
            if (alreadyFailed) continue
            val metadata = coordinator.loadedMetadata(pageId) ?: probeMetadata(source, pageId)
            if (metadata == null) {
                synchronized(lock) { preloadFailures += pageId }
                continue
            }
            val key = extendedFullPageKey(pageId, metadata)
            val loaded = try {
                cache.getOrLoad(key) {
                    decoder.decodeFull(source.open(pageId), metadata, FrameId(pageId, 0))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                synchronized(lock) { preloadFailures += pageId }
                continue
            }
            if (!loaded.resident) loaded.tile.close()
        }
    }

    private suspend fun probeMetadata(source: ChapterSource, pageId: PageId): ImageMetadata? = try {
        decoder.probe(source.open(pageId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** Mirrors the coordinator's full-page tile key so prefetch and visible loads share tiles. */
    private fun extendedFullPageKey(pageId: PageId, meta: ImageMetadata): TileKey {
        var sampleSize = 1
        if (!meta.isAnimated) {
            while (
                ((meta.width.toLong() + sampleSize - 1) / sampleSize) *
                ((meta.height.toLong() + sampleSize - 1) / sampleSize) > maxFullPagePixels
            ) {
                sampleSize = Math.multiplyExact(sampleSize, 2)
            }
        }
        return TileKey(
            pageId = pageId,
            frameId = if (meta.isAnimated) FrameId(pageId, 0) else null,
            bounds = IntRect(0, 0, meta.width, meta.height),
            sampleSize = sampleSize,
        )
    }

    private fun newCoordinator() = PageLoadCoordinator(
        decoder = decoder,
        cache = cache,
        scope = scope,
        maxFullPagePixels = maxFullPagePixels,
    )

    private suspend fun runWarmup(request: AdjacentChapterWarmup) {
        val source = try {
            sourceFactory.create(request.asset)
        } catch (_: Exception) {
            return
        }
        try {
            if (request.preloadFirstUnit) {
                val warmer = newCoordinator()
                try {
                    val pages = warmer.openChapter(source)
                    pages.firstOrNull()?.let { page -> warmer.loadVisible(page.id) }
                } finally {
                    warmer.close()
                }
            } else {
                source.pages()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Adjacent warmup is opportunistic and must never fail the current visible page.
        } finally {
            source.close()
        }
    }

    companion object {
        const val DEFAULT_MAX_FULL_PAGE_PIXELS = 4L * 1024L * 1024L
    }
}

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
