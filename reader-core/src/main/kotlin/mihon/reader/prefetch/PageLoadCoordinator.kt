package mihon.reader.prefetch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mihon.reader.cache.CacheMetrics
import mihon.reader.cache.WeightedTileCache
import mihon.reader.image.DecodedTile
import mihon.reader.image.ImageMetadata
import mihon.reader.image.IntRect
import mihon.reader.image.PageDecoder
import mihon.reader.image.TileKey
import mihon.reader.image.TileRequest
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.source.ChapterSource
import mihon.reader.source.ReaderFailure
import java.io.Closeable

/**
 * Coordinates page loading on top of [WeightedTileCache]: visible-page priority, mode/direction
 * aware prefetch ([PrefetchPolicy]), single-flight per tile, typed error memoization, and
 * deterministic cancellation.
 *
 * - [loadVisible]/[loadRegion]/[retry] run the load pipeline inline in the caller's coroutine so
 *   caller cancellation aborts the work at the next checkpoint; prefetch runs in [scope] jobs
 *   that first yield and then wait for visible demand to drain, so visible work always wins.
 * - Every decode request path checks cancellation before opening input, after probe, after
 *   decode, and before cache insertion (the last two inside the cache's single-flight leader).
 * - GIF region decode ignores `sampleSize` and always returns full-resolution crops (Task 4
 *   constraint), so animated pages are always requested at `sampleSize = 1`; anything else would
 *   double-cache identical rasters under different keys and misstate tile weights.
 * - A page failure is memoized against the chapter asset modification tuple (sizeBytes,
 *   modifiedAt) and served without touching the source until [retry] is called or the chapter
 *   is reopened with a changed tuple. Cancellation is never memoized.
 * - [retry] invalidates every cached tile/frame of the page (even pinned ones) and starts a
 *   fresh single flight.
 *
 * Mutating calls ([openChapter], [updatePosition], [retry]) are expected from the session's
 * serial context. The coordinator does not own sources or the cache: it never closes them.
 * Returned tiles are borrowed; a [WeightedTileCache.LoadedTile] with `resident = false` is owned
 * by the caller and must be closed by it (the coordinator closes any still tracked at chapter
 * end as a backstop).
 */
class PageLoadCoordinator(
    internal val decoder: PageDecoder,
    internal val cache: WeightedTileCache,
    scope: CoroutineScope,
    internal val maxFullPagePixels: Long = Long.MAX_VALUE,
) : Closeable {
    init {
        require(maxFullPagePixels > 0L) { "maxFullPagePixels must be positive" }
    }

    internal val job = SupervisorJob(scope.coroutineContext[Job])
    internal val scope = CoroutineScope(scope.coroutineContext + job)

    internal val lock = Any()
    internal val flights = HashMap<FlightKey, Flight>()
    internal val metadata = HashMap<PageId, ImageMetadata>()
    internal val pageErrors = HashMap<PageId, RecordedError>()
    internal val visiblePins = HashMap<TileKey, WeightedTileCache.TilePin>()
    internal val visibleDemand = HashMap<PageId, Int>()
    internal val uncachedTiles = ArrayList<DecodedTile>()
    internal var idleSignal = CompletableDeferred(Unit)
    internal var chapterSource: ChapterSource? = null
    internal var chapterPages = listOf<PageDescriptor>()
    internal var chapterPageIds = emptySet<PageId>()
    internal var prefetchSet = emptySet<PageId>()
    internal var chapterEpoch = 0
    internal var closed = false

    val cacheMetrics: CacheMetrics
        get() = cache.metrics

    /** Returns metadata already established by a completed page load. */
    fun loadedMetadata(pageId: PageId): ImageMetadata? = synchronized(lock) { metadata[pageId] }

    /**
     * Switches the coordinator to [source]: cancels every in-flight flight, releases visible
     * pins, evicts the previous chapter's unpinned cache entries, and clears memoized errors and
     * probed metadata — unless the chapter id and the asset modification tuple are unchanged, in
     * which case both survive the reopen.
     */
    suspend fun openChapter(source: ChapterSource): List<PageDescriptor> {
        val drained = synchronized(lock) {
            checkOpenLocked()
            chapterEpoch++
            val previous = drainStateLocked()
            val previousAsset = chapterSource?.asset
            val sameChapter = previousAsset?.chapterId == source.asset.chapterId
            val sameTuple = previousAsset != null &&
                previousAsset.sizeBytes == source.asset.sizeBytes &&
                previousAsset.modifiedAt == source.asset.modifiedAt
            if (!(sameChapter && sameTuple)) {
                pageErrors.clear()
                metadata.clear()
            }
            chapterSource = source
            previous
        }
        drained.cancelAndClose()
        drained.chapterIds.forEach { cache.invalidateChapter(it) }

        val pages = source.pages()
        synchronized(lock) {
            if (chapterSource !== source) throw CancellationException("chapter superseded by a newer open")
            chapterPages = pages
            chapterPageIds = pages.mapTo(HashSet()) { it.id }
        }
        return pages
    }

    /**
     * Records the visible pages and recomputes the prefetch window: pins for pages that left the
     * visible set are released, flights for pages outside the new window are cancelled, and new
     * prefetch flights launch (visible demand first, then two units ahead, one behind).
     */
    fun updatePosition(
        selectedIndex: Int,
        visiblePages: List<PageId>,
        mode: ReadingMode,
        direction: NavigationDirection,
    ) {
        val pinsToClose = mutableListOf<WeightedTileCache.TilePin>()
        val toCancel = mutableListOf<Flight>()
        val toLaunch = mutableListOf<Pair<FlightKey.FullPage, Flight>>()
        synchronized(lock) {
            checkOpenLocked()
            val pages = chapterPages
            require(pages.isNotEmpty()) { "no chapter is open" }
            require(selectedIndex in pages.indices) { "selectedIndex $selectedIndex out of ${pages.size} pages" }
            visiblePages.forEach { page ->
                require(page in chapterPageIds) { "visible page is not part of the open chapter" }
            }

            val visibleSet = visiblePages.toSet()
            val stalePins = visiblePins.keys.filter { it.pageId !in visibleSet }
            stalePins.forEach { pinsToClose += visiblePins.remove(it)!! }

            val wanted = PrefetchPolicy.plan(pages.size, selectedIndex, mode, direction)
                .map { pages[it].id }
                .filter { it !in visibleSet }
                .toSet()
            prefetchSet = wanted

            val iterator = flights.entries.iterator()
            while (iterator.hasNext()) {
                val (key, flight) = iterator.next()
                when {
                    flight.cancelled -> iterator.remove()
                    flight.prefetchWanted && key.pageId !in wanted -> {
                        flight.prefetchWanted = false
                        if (flight.visibleWaiters == 0) {
                            flight.cancelled = true
                            iterator.remove()
                            toCancel += flight
                        }
                    }
                }
            }
            for (pageId in wanted) {
                val frame = FlightKey.FullPage(pageId, frameIndex = 0)
                val existing = flights[frame]
                if (existing != null) {
                    existing.prefetchWanted = true
                } else {
                    val flight = Flight(prefetchWanted = true)
                    flights[frame] = flight
                    toLaunch += frame to flight
                }
            }
        }
        pinsToClose.forEach { it.close() }
        toCancel.forEach { it.cancel("prefetch superseded by a new position") }
        toLaunch.forEach { (frame, flight) -> launchPrefetch(frame, flight) }
    }

    /** Cancels background-only work while leaving visible flights and their pins intact. */
    fun cancelPrefetch() {
        val toCancel = mutableListOf<Flight>()
        synchronized(lock) {
            checkOpenLocked()
            prefetchSet = emptySet()
            val iterator = flights.entries.iterator()
            while (iterator.hasNext()) {
                val (_, flight) = iterator.next()
                if (flight.prefetchWanted) {
                    flight.prefetchWanted = false
                    if (flight.visibleWaiters == 0) {
                        flight.cancelled = true
                        iterator.remove()
                        toCancel += flight
                    }
                }
            }
        }
        toCancel.forEach { it.cancel("prefetch paused") }
    }

    /** Loads the full [frameIndex] frame of [pageId] with visible priority, pinning the tile. */
    suspend fun loadVisible(pageId: PageId, frameIndex: Int = 0): WeightedTileCache.LoadedTile {
        require(frameIndex >= 0) { "frameIndex must not be negative" }
        return runVisibleFlight(FlightKey.FullPage(pageId, frameIndex))
    }

    /**
     * Loads a region tile of [pageId]. Animated pages are always requested at `sampleSize = 1`
     * regardless of [sampleSize] because GIF region decode ignores sampling (see class KDoc).
     */
    suspend fun loadRegion(
        pageId: PageId,
        bounds: IntRect,
        frameIndex: Int = 0,
        sampleSize: Int = 1,
    ): WeightedTileCache.LoadedTile {
        require(frameIndex >= 0) { "frameIndex must not be negative" }
        require(sampleSize > 0) { "sampleSize must be positive" }
        return runVisibleFlight(FlightKey.Region(pageId, bounds, frameIndex, sampleSize))
    }

    /**
     * Keeps only the region tiles required by the latest viewport for [pageId]. The downsampled
     * full-page tile remains pinned as a seamless fallback while replacement regions decode.
     */
    fun retainVisibleRegions(pageId: PageId, keys: Set<TileKey>) {
        val pinsToClose = mutableListOf<WeightedTileCache.TilePin>()
        val toCancel = mutableListOf<Flight>()
        synchronized(lock) {
            checkOpenLocked()
            require(pageId in chapterPageIds) { "page is not part of the open chapter" }
            require(keys.all { it.pageId == pageId }) { "region keys must belong to pageId" }
            val meta = metadata[pageId] ?: return@synchronized
            val fullPageKey = fullPageTileKey(pageId, frameIndex = 0, meta)

            val stalePins = visiblePins.keys.filter { key ->
                key.pageId == pageId && key != fullPageKey && key !in keys
            }
            stalePins.forEach { pinsToClose += visiblePins.remove(it)!! }

            val iterator = flights.entries.iterator()
            while (iterator.hasNext()) {
                val (flightKey, flight) = iterator.next()
                if (flightKey is FlightKey.Region && flightKey.pageId == pageId) {
                    val sampleSize = if (meta.isAnimated) 1 else flightKey.sampleSize
                    val frameId = if (meta.isAnimated) FrameId(pageId, flightKey.frameIndex) else null
                    val tileKey = TileKey(pageId, frameId, flightKey.bounds, sampleSize)
                    if (tileKey !in keys) {
                        flight.cancelled = true
                        iterator.remove()
                        toCancel += flight
                    }
                }
            }
        }
        pinsToClose.forEach { it.close() }
        toCancel.forEach { it.cancel("region left the visible viewport") }
    }

    /**
     * Drops the memoized error for [pageId], invalidates every cached tile/frame of the page
     * (even pinned ones), cancels its in-flight flights, and starts a fresh single flight.
     */
    suspend fun retry(pageId: PageId, frameIndex: Int = 0): WeightedTileCache.LoadedTile {
        val (staleFlights, stalePins) = synchronized(lock) {
            checkOpenLocked()
            pageErrors.remove(pageId)
            metadata.remove(pageId)
            val staleF = flights.filterKeys { it.pageId == pageId }
            staleF.values.forEach { it.cancelled = true }
            staleF.keys.forEach { flights.remove(it) }
            val staleP = visiblePins.filterKeys { it.pageId == pageId }
            staleP.keys.forEach { visiblePins.remove(it) }
            staleF.values.toList() to staleP.values.toList()
        }
        staleFlights.forEach { it.cancel("explicit retry") }
        stalePins.forEach { it.close() }
        cache.invalidatePage(pageId)
        val staleUncached = synchronized(lock) {
            val matches = uncachedTiles.filter { it.key.pageId == pageId }
            uncachedTiles.removeAll(matches)
            matches
        }
        staleUncached.forEach { it.close() }
        return loadVisible(pageId, frameIndex)
    }

    override fun close() {
        val drained = synchronized(lock) {
            if (closed) return
            closed = true
            chapterEpoch++
            drainStateLocked()
        }
        drained.cancelAndClose()
        job.cancel()
    }
}
