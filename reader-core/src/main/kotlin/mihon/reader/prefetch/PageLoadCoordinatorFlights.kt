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
import mihon.reader.source.usePageInput
import java.io.Closeable

internal suspend fun PageLoadCoordinator.runVisibleFlight(key: FlightKey): WeightedTileCache.LoadedTile {
    demandUp(key.pageId)
    try {
        val epoch = synchronized(lock) {
            checkOpenLocked()
            if (chapterSource == null) throw ReaderFailure.SourceClosed()
            chapterEpoch
        }
        while (true) {
            val (flight, lead) = synchronized(lock) {
                checkOpenLocked()
                if (chapterEpoch != epoch) throw CancellationException("chapter changed")
                memoizedFailureLocked(key.pageId)?.let { throw it }
                val existing = flights[key]
                if (existing != null && !existing.cancelled) {
                    existing.visibleWaiters++
                    existing.visible = true
                    notifyDemandChangedLocked()
                    existing to false
                } else {
                    val fresh = Flight(
                        visible = true,
                        prefetchWanted = key is FlightKey.FullPage &&
                            key.frameIndex == 0 &&
                            key.pageId in prefetchSet,
                    )
                    fresh.visibleWaiters = 1
                    flights[key] = fresh
                    fresh to true
                }
            }
            if (lead) {
                try {
                    runFlight(key, flight)
                } catch (failure: Throwable) {
                    // withContext checks for cancellation before entering its block. A leader
                    // cancelled in that window never reaches runFlight's own cleanup, which would
                    // leave the flight in the map with a deferred nobody completes.
                    synchronized(lock) {
                        if (flights[key] === flight) flights.remove(key)
                        flight.visibleWaiters--
                    }
                    flight.deferred.completeExceptionally(failure)
                    throw failure
                }
            }
            try {
                return flight.deferred.await()
            } catch (cancel: CancellationException) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (chapterEpoch != epoch) throw CancellationException("chapter changed")
                    if (flights[key] === flight && flight.cancelled) flights.remove(key)
                }
                continue
            } finally {
                synchronized(lock) { flight.visibleWaiters-- }
            }
        }
    } finally {
        demandDown(key.pageId)
    }
}

internal suspend fun PageLoadCoordinator.runFlight(key: FlightKey, flight: Flight) = kotlinx.coroutines.withContext(
    PageLoadPriority { synchronized(lock) { flight.visible } },
) {
    val pageId = key.pageId
    try {
        val source = synchronized(lock) { chapterSource } ?: throw ReaderFailure.SourceClosed()
        synchronized(lock) { memoizedFailureLocked(pageId) }?.let { throw it }
        val meta = metadataFor(source, flight, pageId)
        val loaded = when (key) {
            is FlightKey.FullPage -> loadFullPage(source, flight, key, meta)
            is FlightKey.Region -> loadRegionTile(source, flight, key, meta)
        }
        if (!loaded.resident) {
            synchronized(lock) { uncachedTiles += loaded.tile }
        }
        flight.deferred.complete(loaded)
    } catch (error: Throwable) {
        if (error !is CancellationException && error !is ReaderFailure.SourceClosed) {
            memoize(pageId, error)
        }
        flight.deferred.completeExceptionally(error)
    } finally {
        synchronized(lock) { if (flights[key] === flight) flights.remove(key) }
    }
}

internal suspend fun PageLoadCoordinator.loadFullPage(
    source: ChapterSource,
    flight: Flight,
    key: FlightKey.FullPage,
    meta: ImageMetadata,
): WeightedTileCache.LoadedTile {
    val pageId = key.pageId
    require(meta.isAnimated || key.frameIndex == 0) { "static pages expose only frame zero" }
    val tileKey = fullPageTileKey(pageId, key.frameIndex, meta)
    val sampleSize = tileKey.sampleSize
    synchronized(lock) { if (flight.visible) pinVisibleLocked(tileKey) }
    return cache.getOrLoad(tileKey, insertGuard = { !flight.cancelled }) {
        flight.checkpoint()
        val tile = if (sampleSize == 1) {
            source.usePageInput(pageId) { input ->
                decoder.decodeFull(input, meta, FrameId(pageId, key.frameIndex))
            }
        } else {
            source.usePageInput(pageId) { input ->
                decoder.decodeRegion(
                    input,
                    meta,
                    TileRequest(
                        key = tileKey,
                        targetWidth = (meta.width + sampleSize - 1) / sampleSize,
                        targetHeight = (meta.height + sampleSize - 1) / sampleSize,
                    ),
                )
            }
        }
        try {
            flight.checkpoint()
        } catch (cancel: CancellationException) {
            tile.close()
            throw cancel
        }
        tile
    }
}

internal suspend fun PageLoadCoordinator.loadRegionTile(
    source: ChapterSource,
    flight: Flight,
    key: FlightKey.Region,
    meta: ImageMetadata,
): WeightedTileCache.LoadedTile {
    val pageId = key.pageId
    // GIF region decode ignores sampleSize (Task 4): force 1 for animated pages so the same
    // raster is never cached under two keys and tile weights stay honest.
    val sampleSize = if (meta.isAnimated) 1 else key.sampleSize
    val frameId = if (meta.isAnimated) FrameId(pageId, key.frameIndex) else null
    val tileKey = TileKey(pageId, frameId, key.bounds, sampleSize)
    val request = TileRequest(
        key = tileKey,
        targetWidth = (key.bounds.width + sampleSize - 1) / sampleSize,
        targetHeight = (key.bounds.height + sampleSize - 1) / sampleSize,
    )
    synchronized(lock) { if (flight.visible) pinVisibleLocked(tileKey) }
    return cache.getOrLoad(tileKey, insertGuard = { !flight.cancelled }) {
        flight.checkpoint()
        val tile = source.usePageInput(pageId) { input ->
            decoder.decodeRegion(input, meta, request)
        }
        try {
            flight.checkpoint()
        } catch (cancel: CancellationException) {
            tile.close()
            throw cancel
        }
        tile
    }
}

internal fun PageLoadCoordinator.fullPageTileKey(pageId: PageId, frameIndex: Int, meta: ImageMetadata): TileKey =
    mihon.reader.image.fullPageTileKey(pageId, FrameId(pageId, frameIndex), meta, maxFullPagePixels)

internal suspend fun PageLoadCoordinator.metadataFor(
    source: ChapterSource,
    flight: Flight,
    pageId: PageId,
): ImageMetadata {
    synchronized(lock) { metadata[pageId] }?.let { return it }
    flight.checkpoint()
    val probed = source.usePageInput(pageId) { input -> decoder.probe(input) }
    flight.checkpoint()
    synchronized(lock) { metadata[pageId] = probed }
    return probed
}

internal fun PageLoadCoordinator.launchPrefetch(frame: FlightKey.FullPage, flight: Flight) {
    flight.job = scope.launch {
        // Yield once so visible requests queued behind this job can register demand first,
        // then wait out any active visible load before touching the source or the ledger.
        yield()
        awaitVisibleIdle(flight, frame.pageId)
        if (!flight.cancelled) runFlight(frame, flight)
    }
}

internal suspend fun PageLoadCoordinator.awaitVisibleIdle(flight: Flight, pageId: PageId) {
    while (true) {
        val signal = synchronized(lock) {
            if (flight.visible || flight.cancelled) return
            val hasOtherDemand = visibleDemand.any { (id, count) -> id != pageId && count > 0 }
            if (!hasOtherDemand) return
            idleSignal
        }
        signal.await()
    }
}

internal fun PageLoadCoordinator.notifyDemandChangedLocked() {
    val signal = idleSignal
    idleSignal = CompletableDeferred()
    signal.complete(Unit)
}

internal fun PageLoadCoordinator.demandUp(pageId: PageId) {
    synchronized(lock) {
        visibleDemand.merge(pageId, 1, Int::plus)
        notifyDemandChangedLocked()
    }
}

internal fun PageLoadCoordinator.demandDown(pageId: PageId) {
    synchronized(lock) {
        val count = (visibleDemand[pageId] ?: 0) - 1
        if (count <= 0) visibleDemand.remove(pageId) else visibleDemand[pageId] = count
        notifyDemandChangedLocked()
    }
}

internal fun PageLoadCoordinator.pinVisibleLocked(key: TileKey) {
    if (key !in visiblePins) visiblePins[key] = cache.pin(key)
}

internal fun PageLoadCoordinator.memoizedFailureLocked(pageId: PageId): Throwable? {
    val recorded = pageErrors[pageId] ?: return null
    val asset = chapterSource?.asset ?: return null
    return if (recorded.sizeBytes == asset.sizeBytes && recorded.modifiedAt == asset.modifiedAt) {
        recorded.failure
    } else {
        pageErrors.remove(pageId)
        null
    }
}

internal fun PageLoadCoordinator.memoize(pageId: PageId, failure: Throwable) {
    synchronized(lock) {
        val asset = chapterSource?.asset ?: return
        pageErrors[pageId] = RecordedError(failure, asset.sizeBytes, asset.modifiedAt)
    }
}

internal fun PageLoadCoordinator.checkOpenLocked() {
    if (closed) throw IllegalStateException("PageLoadCoordinator is closed")
}

/** Collects and clears all per-chapter state; callers cancel/close the results outside the lock. */
internal fun PageLoadCoordinator.drainStateLocked(): DrainedState {
    val drained = DrainedState(
        flights = flights.values.toList(),
        pins = visiblePins.values.toList(),
        uncached = uncachedTiles.toList(),
        chapterIds = buildSet {
            chapterPages.mapTo(this) { it.id.chapterId }
            chapterSource?.let { add(it.asset.chapterId.toString()) }
        },
    )
    flights.values.forEach { it.cancelled = true }
    flights.clear()
    visiblePins.clear()
    uncachedTiles.clear()
    prefetchSet = emptySet()
    chapterPages = emptyList()
    chapterPageIds = emptySet()
    return drained
}

internal class DrainedState(
    val flights: List<Flight>,
    val pins: List<WeightedTileCache.TilePin>,
    val uncached: List<DecodedTile>,
    val chapterIds: Set<String>,
) {
    fun cancelAndClose() {
        flights.forEach { it.cancel("chapter state drained") }
        pins.forEach { it.close() }
        uncached.forEach { it.close() }
    }
}

internal sealed interface FlightKey {
    val pageId: PageId

    data class FullPage(
        override val pageId: PageId,
        val frameIndex: Int,
    ) : FlightKey

    data class Region(
        override val pageId: PageId,
        val bounds: IntRect,
        val frameIndex: Int,
        val sampleSize: Int,
    ) : FlightKey
}

internal class RecordedError(
    val failure: Throwable,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** A single-flight load. [cancel] is idempotent and safe to call from any thread. */
internal class Flight(
    var prefetchWanted: Boolean,
    @Volatile var visible: Boolean = false,
) {
    val deferred = CompletableDeferred<WeightedTileCache.LoadedTile>()
    var job: Job? = null
    var visibleWaiters = 0

    @Volatile var cancelled = false

    suspend fun checkpoint() {
        currentCoroutineContext().ensureActive()
        if (cancelled) throw CancellationException("load superseded")
    }

    fun cancel(reason: String) {
        cancelled = true
        job?.cancel()
        deferred.completeExceptionally(CancellationException(reason))
    }
}
