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

internal suspend fun DefaultReaderSession.moveLocked(direction: NavigationDirection) {
    val current = stateUpdates.value
    if (current.loadState !is ReaderLoadState.Ready) return
    accrueDurationLocked()
    val nextIndex = nextLogicalIndex(current, direction)
    if (nextIndex in current.pages.indices) {
        navigationDirection = direction
        stateUpdates.value = current.reduce(ReaderAction.SelectPage(nextIndex))
        updateContentPositionLocked()
        requestVisibleLocked()
        submitProgressLocked()
        resetClockLocked()
        return
    }
    submitProgressLocked()
    progress.flush()
    val adjacent = catalog.adjacentReadableChapter(
        requireNotNull(current.chapterId),
        if (direction == NavigationDirection.FORWARD) ChapterDirection.NEXT else ChapterDirection.PREVIOUS,
    ) ?: return
    openLocked(adjacent.chapterId, landAtEnd = direction == NavigationDirection.BACKWARD)
}

internal suspend fun DefaultReaderSession.requestVisibleLocked(
    pageId: PageId = stateUpdates.value.visiblePages.single(),
) {
    visibleJob?.cancel()
    visibleLease?.close()
    visibleLease = null
    stateUpdates.update { it.copy(visibleTileLeases = emptyList()) }
    visibleJob = scope.launch {
        try {
            val lease = contentPipeline.loadVisible(pageId)
            var adopted = false
            try {
                sessionLock.withLock {
                    if (!closed && stateUpdates.value.visiblePages.singleOrNull() == pageId) {
                        visibleLease = lease
                        adopted = true
                        stateUpdates.update {
                            it.copy(
                                visibleTileLeases = listOfNotNull(lease),
                                cacheMetrics = contentPipeline.metrics(),
                            )
                        }
                    }
                }
            } finally {
                // The job may be cancelled while waiting for the lock; the lease still holds budget.
                if (!adopted) lease?.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            sessionLock.withLock { failLocked(ReaderErrorCode.PAGE_DECODE_FAILED, failure) }
        }
    }
}

internal suspend fun DefaultReaderSession.submitProgressLocked() {
    val state = stateUpdates.value
    val chapterId = state.chapterId ?: return
    val page = if (ReaderLayoutPolicy.isContinuous(state)) state.viewportAnchor.pageIndex else state.selectedIndex
    val update = ReaderProgressUpdate(
        chapterId = chapterId,
        pageIndex = page.toLong(),
        completed = page == state.pages.lastIndex,
        lastReadEpochMillis = epochMillis().coerceAtLeast(0L),
        readDurationDeltaMillis = pendingDurationMillis,
        generation = generation,
        sequence = sequence++,
    )
    pendingDurationMillis = 0L
    progress.submit(update)
}

internal fun DefaultReaderSession.accrueDurationLocked() {
    val now = monotonicClock.nowMillis()
    val prior = lastClockMillis
    lastClockMillis = now
    val state = stateUpdates.value
    if (prior == null || state.loadState !is ReaderLoadState.Ready || !state.foreground ||
        !state.contentVisible
    ) {
        return
    }
    pendingDurationMillis = (pendingDurationMillis + (now - prior).coerceIn(0L, MAX_DURATION_DELTA_MILLIS))
        .coerceAtMost(Long.MAX_VALUE)
}

internal fun DefaultReaderSession.resetClockLocked() {
    lastClockMillis = monotonicClock.nowMillis()
}

internal fun DefaultReaderSession.nextLogicalIndex(state: ReaderState, direction: NavigationDirection): Int {
    val step = if (direction == NavigationDirection.FORWARD) 1 else -1
    if (!state.mode.isDualPage) return state.selectedIndex + step
    val groups = mihon.reader.layout.PageGrouping.forMode(
        state.pages,
        state.mode,
        state.coverOffset,
        state.dualPageSplit,
        state.dualPageRotateToFit,
    )
    val currentId = state.pages.getOrNull(state.selectedIndex)?.id
    val currentGroupIndex = groups.indexOfFirst { group ->
        group.any { it.id == currentId }
    }
    if (currentGroupIndex == -1) {
        return when {
            direction == NavigationDirection.FORWARD && state.coverOffset && state.selectedIndex == 0 -> 1
            direction == NavigationDirection.BACKWARD && state.coverOffset && state.selectedIndex == 1 -> 0
            else -> state.selectedIndex + step * 2
        }
    }
    val targetGroupIndex = currentGroupIndex + step
    if (targetGroupIndex !in groups.indices) {
        return if (direction == NavigationDirection.FORWARD) state.pages.size else -1
    }
    val targetGroup = groups[targetGroupIndex]
    val targetIndices = targetGroup.mapNotNull { page ->
        state.pages.indexOfFirst { it.id == page.id }.takeIf { it != -1 }
    }
    return targetIndices.minOrNull() ?: (state.selectedIndex + step)
}

internal fun DefaultReaderSession.failLocked(code: ReaderErrorCode, cause: Throwable? = null) {
    closeChapterLocked()
    stateUpdates.value = stateUpdates.value.copy(
        loadState = ReaderLoadState.Failed(ReaderSessionError(code, cause)),
        error = ReaderSessionError(code, cause),
        visiblePages = emptyList(),
        visibleTileLeases = emptyList(),
    )
}

/**
 * Records a failed chapter open. The attempted chapter becomes the current chapter and the previous
 * chapter's pages are cleared so retry and progress cannot silently address the wrong chapter.
 */
internal fun DefaultReaderSession.failOpenLocked(
    chapterId: Long,
    code: ReaderErrorCode,
    cause: Throwable? = null,
) {
    closeChapterLocked()
    val error = ReaderSessionError(code, cause)
    stateUpdates.value = stateUpdates.value.copy(
        chapterId = chapterId,
        pages = emptyList(),
        selectedIndex = 0,
        viewportAnchor = ReaderPosition(0),
        loadState = ReaderLoadState.Failed(error),
        error = error,
        visiblePages = emptyList(),
        visibleTileLeases = emptyList(),
        hasPreviousChapter = false,
        hasNextChapter = false,
    )
}

internal suspend fun DefaultReaderSession.flushAndCloseChapterLocked() {
    if (stateUpdates.value.loadState is ReaderLoadState.Ready) {
        submitProgressLocked()
        progress.flush()
    }
    closeChapterLocked()
    contentPipeline.awaitBackgroundRelease()
}

internal fun DefaultReaderSession.closeChapterLocked() {
    animationCoordinator?.cancelForPageOrChapterChange()
    visibleJob?.cancel()
    visibleJob = null
    visibleLease?.close()
    visibleLease = null
    if (source != null) contentPipeline.closeChapter()
    source?.close()
    source = null
    contentPipeline.warmAdjacent(null)
    lastClockMillis = null
}

internal fun DefaultReaderSession.updateContentPositionLocked() {
    val state = stateUpdates.value
    if (state.loadState !is ReaderLoadState.Ready || state.pages.isEmpty()) return
    contentPipeline.updatePosition(
        ReaderContentPosition(
            selectedIndex = state.selectedIndex,
            visiblePages = state.visiblePages,
            mode = state.mode,
            direction = navigationDirection,
            foreground = state.foreground,
            contentVisible = state.contentVisible,
        ),
    )
    val chapterId = requireNotNull(state.chapterId)
    val remainingPages = state.pages.size - state.selectedIndex
    val nearEnd = remainingPages <= NEXT_CHAPTER_WARMUP_PAGES
    val nearStart = state.selectedIndex < NEXT_CHAPTER_WARMUP_PAGES
    // Fetch the pages the reader is about to enter, not only the single boundary image.
    // A short chapter is near both ends; follow the direction the pages are actually moving.
    val warmup = when {
        nearEnd && (!nearStart || navigationDirection != NavigationDirection.BACKWARD) ->
            catalog.adjacentReadableChapter(chapterId, ChapterDirection.NEXT)?.let { adjacent ->
                AdjacentChapterWarmup(
                    asset = adjacent,
                    preloadFirstUnit = true,
                    preloadCount = ADJACENT_PRELOAD_PAGES,
                )
            }
        nearStart ->
            catalog.adjacentReadableChapter(chapterId, ChapterDirection.PREVIOUS)?.let { adjacent ->
                AdjacentChapterWarmup(
                    asset = adjacent,
                    preloadFirstUnit = true,
                    fromEnd = true,
                    preloadCount = ADJACENT_PRELOAD_PAGES,
                )
            }
        else -> null
    }
    contentPipeline.warmAdjacent(warmup)
    stateUpdates.update { it.copy(cacheMetrics = contentPipeline.metrics()) }
}

internal fun DefaultReaderSession.requestPipelineRetryLocked(pageId: PageId) {
    visibleJob?.cancel()
    visibleLease?.close()
    visibleLease = null
    stateUpdates.update { it.copy(visibleTileLeases = emptyList()) }
    visibleJob = scope.launch {
        try {
            val lease = contentPipeline.retry(pageId)
            var adopted = false
            try {
                sessionLock.withLock {
                    if (!closed && stateUpdates.value.visiblePages.singleOrNull() == pageId) {
                        visibleLease = lease
                        adopted = true
                        stateUpdates.update {
                            it.copy(
                                visibleTileLeases = listOfNotNull(lease),
                                cacheMetrics = contentPipeline.metrics(),
                            )
                        }
                    }
                }
            } finally {
                if (!adopted) lease?.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            sessionLock.withLock { failLocked(ReaderErrorCode.PAGE_DECODE_FAILED, failure) }
        }
    }
}

internal fun DefaultReaderSession.closeContentPipelineLocked() {
    if (contentPipelineClosed) return
    contentPipelineClosed = true
    animationCoordinator?.close()
    contentPipeline.close()
}

private object ReaderLayoutPolicy {
    fun isContinuous(state: ReaderState) = state.mode == mihon.reader.model.ReadingMode.VERTICAL ||
        state.mode == mihon.reader.model.ReadingMode.WEBTOON
}

internal const val NANOS_PER_MILLI = 1_000_000L
private const val MAX_DURATION_DELTA_MILLIS = 60_000L
private const val NEXT_CHAPTER_WARMUP_PAGES = 5
private const val ADJACENT_PRELOAD_PAGES = 3
