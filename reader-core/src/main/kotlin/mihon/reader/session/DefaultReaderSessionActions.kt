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

internal suspend fun DefaultReaderSession.applyAction(action: ReaderAction, requestedChapterId: Long?) {
    sessionLock.withLock {
        if (closed) return@withLock
        val chapterAction = action is ReaderAction.SelectPage ||
            action is ReaderAction.SetViewportAnchor || action is ReaderAction.SetVisiblePages ||
            action is ReaderAction.SetPageIntrinsicSize ||
            action is ReaderAction.Next || action is ReaderAction.Previous
        if (chapterAction && requestedChapterId != stateUpdates.value.chapterId) return@withLock
        val pageSensitive = action is ReaderAction.SelectPage || action is ReaderAction.SetViewportAnchor ||
            action is ReaderAction.SetVisiblePages || action is ReaderAction.SetPageIntrinsicSize
        // A composition snapshot from the previous chapter can outlive a failed or loading open.
        if (pageSensitive && stateUpdates.value.loadState !is ReaderLoadState.Ready) return@withLock
        when (action) {
            ReaderAction.Next -> moveLocked(NavigationDirection.FORWARD)
            ReaderAction.Previous -> moveLocked(NavigationDirection.BACKWARD)
            else -> {
                accrueDurationLocked()
                val previousIndex = stateUpdates.value.selectedIndex
                stateUpdates.update { it.reduce(action) }
                val updatedIndex = stateUpdates.value.selectedIndex
                if (updatedIndex != previousIndex) {
                    navigationDirection = if (updatedIndex > previousIndex) {
                        NavigationDirection.FORWARD
                    } else {
                        NavigationDirection.BACKWARD
                    }
                }
                when (action) {
                    is ReaderAction.SetForeground -> animationCoordinator?.setForeground(action.foreground)
                    is ReaderAction.SetContentVisible -> animationCoordinator?.setContentVisible(action.visible)
                    is ReaderAction.SelectPage,
                    is ReaderAction.SetViewportAnchor,
                    -> animationCoordinator?.cancelForPageOrChapterChange()
                    else -> Unit
                }
                if (
                    action is ReaderAction.SelectPage ||
                    action is ReaderAction.SetViewportAnchor ||
                    action is ReaderAction.SetVisiblePages ||
                    action is ReaderAction.ChangeMode ||
                    action is ReaderAction.SetForeground ||
                    action is ReaderAction.SetContentVisible
                ) {
                    updateContentPositionLocked()
                }
                if (action is ReaderAction.SelectPage || action is ReaderAction.SetViewportAnchor) {
                    // A continuous scroll emits anchors per pixel; only a page change needs a visible reload.
                    if (action is ReaderAction.SelectPage || updatedIndex != previousIndex) {
                        requestVisibleLocked()
                        submitProgressLocked()
                    }
                }
                resetClockLocked()
            }
        }
    }
}

internal suspend fun DefaultReaderSession.openLocked(chapterId: Long, landAtEnd: Boolean) {
    flushAndCloseChapterLocked()
    generation = generationSource.nextGeneration()
    sequence = 0L
    pendingDurationMillis = 0L
    lastClockMillis = null
    stateUpdates.value =
        stateUpdates.value.copy(
            loadState = ReaderLoadState.Loading(generation),
            error = null,
            visiblePages = emptyList(),
        )

    val asset = catalog.chapterAsset(chapterId)
    if (asset == null) {
        failOpenLocked(chapterId, ReaderErrorCode.SOURCE_UNAVAILABLE)
        return
    }
    val opened = try {
        sourceFactory.create(asset)
    } catch (failure: Throwable) {
        failOpenLocked(chapterId, ReaderErrorCode.SOURCE_UNAVAILABLE, failure)
        return
    }
    val pages = try {
        contentPipeline.open(opened)
    } catch (cancelled: CancellationException) {
        contentPipeline.closeChapter()
        opened.close()
        throw cancelled
    } catch (failure: Throwable) {
        contentPipeline.closeChapter()
        opened.close()
        failOpenLocked(chapterId, ReaderErrorCode.SOURCE_UNAVAILABLE, failure)
        return
    }
    if (pages.isEmpty()) {
        contentPipeline.closeChapter()
        opened.close()
        failOpenLocked(chapterId, ReaderErrorCode.EMPTY_CHAPTER, ReaderFailure.EmptyChapter())
        return
    }

    source = opened
    val selected = if (landAtEnd) {
        pages.lastIndex
    } else {
        asset.lastPageRead.coerceIn(
            0L,
            pages.lastIndex.toLong(),
        ).toInt()
    }
    val previous = catalog.adjacentReadableChapter(chapterId, ChapterDirection.PREVIOUS) != null
    val next = catalog.adjacentReadableChapter(chapterId, ChapterDirection.NEXT) != null
    stateUpdates.value = ReaderState.ready(chapterId, pages, selected, stateUpdates.value.viewport).copy(
        mode = stateUpdates.value.mode,
        coverOffset = stateUpdates.value.coverOffset,
        scaleMode = stateUpdates.value.scaleMode,
        zoom = stateUpdates.value.zoom,
        pan = stateUpdates.value.pan,
        dualPageSplit = stateUpdates.value.dualPageSplit,
        dualPageRotateToFit = stateUpdates.value.dualPageRotateToFit,
        hasPreviousChapter = previous,
        hasNextChapter = next,
        foreground = stateUpdates.value.foreground,
        contentVisible = stateUpdates.value.contentVisible,
    )
    navigationDirection = NavigationDirection.FORWARD
    updateContentPositionLocked()
    resetClockLocked()
    requestVisibleLocked()
}
