package mihon.reader.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderPosition
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import mihon.reader.source.ReaderFailure

/**
 * Session-owned chapter lifetime and durable position coordination. It deliberately has no UI,
 * persistence, or platform dependency: callers provide catalog, sources, and progress writing.
 */
class DefaultReaderSession(
    internal val scope: CoroutineScope,
    internal val catalog: ReaderChapterCatalog,
    internal val sourceFactory: ChapterSourceFactory,
    internal val progressSink: ReaderProgressSink,
    internal val generationSource: ReaderGenerationSource,
    internal val settings: ReaderSettings = ReaderSettings(),
    internal val monotonicClock: ReaderMonotonicClock = ReaderMonotonicClock { System.nanoTime() / NANOS_PER_MILLI },
    internal val epochMillis: () -> Long = System::currentTimeMillis,
    internal val visibleContent: suspend (PageId) -> AutoCloseable? = { null },
    internal val animationCoordinator: AnimationCoordinator? = null,
    internal val invalidateContent: suspend (PageId) -> Unit = {},
    internal val contentPipeline: ReaderContentPipeline = CallbackReaderContentPipeline(visibleContent),
) : ReaderSession {
    internal val sessionLock = Mutex()
    internal val stateUpdates = MutableStateFlow(
        ReaderState(
            mode = settings.mode,
            coverOffset = settings.coverOffset,
            scaleMode = settings.scaleMode,
            zoom = settings.zoom,
            dualPageSplit = settings.dualPageSplit,
            dualPageRotateToFit = settings.dualPageRotateToFit,
        ),
    )
    override val state: StateFlow<ReaderState> = stateUpdates.asStateFlow()

    internal val progress = ProgressDebouncer(scope) { progressSink.record(it) }
    internal var source: ChapterSource? = null
    internal var visibleLease: AutoCloseable? = null
    internal var visibleJob: Job? = null
    internal var generation = -1L
    internal var sequence = 0L
    internal var pendingDurationMillis = 0L
    internal var lastClockMillis: Long? = null
    internal var navigationDirection = NavigationDirection.FORWARD
    internal var contentPipelineClosed = false
    internal var closed = false
    internal val actionQueue = Channel<Pair<ReaderAction, Long?>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((action, chapterId) in actionQueue) {
                try {
                    applyAction(action, chapterId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A single malformed action must not kill the only consumer: the reader would
                    // silently stop reacting to keyboard, click and viewport events.
                    System.getLogger("ReaderSession").log(
                        System.Logger.Level.WARNING,
                        "Dropping reader action $action",
                        error,
                    )
                }
            }
        }
    }

    override suspend fun open(chapterId: Long) = open(chapterId, landAtEnd = false)

    override suspend fun open(chapterId: Long, landAtEnd: Boolean) {
        require(chapterId >= 0L) { "chapterId must not be negative" }
        sessionLock.withLock {
            check(!closed) { "reader session is closed" }
            openLocked(chapterId, landAtEnd)
        }
    }

    override fun dispatch(action: ReaderAction) {
        // A mutex inside separately launched jobs serializes execution but cannot preserve input order.
        actionQueue.trySend(action to stateUpdates.value.chapterId)
    }

    override suspend fun retry(pageId: PageId) {
        sessionLock.withLock {
            val failedState = stateUpdates.value
            if (failedState.loadState is ReaderLoadState.Failed && failedState.pages.isEmpty()) {
                // A chapter that failed to open has no pages, so retry the attempted chapter itself.
                val chapterId = failedState.chapterId ?: return@withLock
                openLocked(chapterId, landAtEnd = false)
                return@withLock
            }
            require(pageId in failedState.pages.map { it.id }) { "page is not in the open chapter" }
            visibleJob?.cancelAndJoin()
            visibleLease?.close()
            visibleLease = null
            invalidateContent(pageId)
            if (failedState.loadState is ReaderLoadState.Failed) {
                val chapterId = requireNotNull(failedState.chapterId) { "failed reader has no chapter" }
                val retryIndex = failedState.pages.indexOfFirst { it.id == pageId }
                openLocked(chapterId, landAtEnd = false)
                val reopened = stateUpdates.value
                if (reopened.loadState is ReaderLoadState.Ready && retryIndex in reopened.pages.indices &&
                    reopened.selectedIndex != retryIndex
                ) {
                    stateUpdates.value = reopened.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(retryIndex)))
                    requestVisibleLocked(pageId)
                }
                return@withLock
            }
            requestPipelineRetryLocked(pageId)
        }
    }

    override suspend fun flushProgress() {
        sessionLock.withLock {
            if (closed || stateUpdates.value.loadState !is ReaderLoadState.Ready) return
            submitProgressLocked()
            progress.flush()
        }
    }

    override suspend fun closeAndFlush() {
        sessionLock.withLock {
            if (closed) return
            if (stateUpdates.value.loadState is ReaderLoadState.Ready) submitProgressLocked()
            progress.closeAndFlush()
            closeChapterLocked()
            contentPipeline.awaitBackgroundRelease()
            closeContentPipelineLocked()
            closed = true
            actionQueue.cancel()
            stateUpdates.value =
                stateUpdates.value.copy(
                    loadState = ReaderLoadState.Closed,
                    visiblePages = emptyList(),
                    visibleTileLeases = emptyList(),
                )
        }
    }

    override fun cancelWithoutFlush(): Job {
        actionQueue.cancel()
        return scope.launch {
            sessionLock.withLock {
                if (closed) return@withLock
                visibleJob?.cancel()
                progress.cancelWithoutFlush()
                closeChapterLocked()
                contentPipeline.awaitBackgroundRelease()
                closeContentPipelineLocked()
                closed = true
                stateUpdates.value =
                    stateUpdates.value.copy(
                        loadState = ReaderLoadState.Closed,
                        visiblePages = emptyList(),
                        visibleTileLeases = emptyList(),
                    )
            }
        }
    }
}
