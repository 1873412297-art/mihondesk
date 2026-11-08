package mihon.desktop.ui.reader

import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import mihon.reader.model.ReaderPan
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderState

/** Composition effects for one open reader screen; state lives on the receiver. */
@Composable
internal fun ReaderScreenState.ReaderScreenEffects(
    state: ReaderState,
    initialTransitionDirection: ReaderChapterTransitionDirection?,
    foreground: Boolean,
    chapterBookmarked: Boolean,
) {
    ReaderLifecycleEffects(state, foreground)
    ReaderChapterEffects(state, initialTransitionDirection, chapterBookmarked)
    ReaderSettingsEffect()
    ReaderHudEffects(state)
}

/** Reader settings and the chapter bookmark touch the database; read them off the UI thread. */
@Composable
private fun ReaderScreenState.ReaderSettingsEffect() {
    LaunchedEffect(settingsStore, readerSettingsOverride, this) {
        val store = settingsStore ?: return@LaunchedEffect
        settings = withContext(Dispatchers.IO) { store.loadEffective(readerSettingsOverride) }
    }
}

@Composable
private fun ReaderScreenState.ReaderLifecycleEffects(state: ReaderState, foreground: Boolean) {
    LaunchedEffect(chromeHideRequests) {
        chromeHideRequests.collectLatest {
            delay(CHROME_HIDE_DELAY_MILLIS)
            overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.IdleTimeout)
        }
    }
    LaunchedEffect(session, foreground) {
        session.dispatch(ReaderAction.SetForeground(foreground))
    }
    // Keep the display awake while the reader is open and the window is not minimized
    // (mirrors Android's keepScreenOn). No-op off Windows; restored on dispose.
    DisposableEffect(screenAwake, settings.keepScreenOn, foreground) {
        if (settings.keepScreenOn && foreground) screenAwake.acquireDisplay()
        onDispose {
            if (settings.keepScreenOn && foreground) screenAwake.releaseDisplay()
        }
    }
    LaunchedEffect(session, state.chapterId) {
        if (state.chapterId != null) {
            session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
        }
    }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    DisposableEffect(session) {
        session.dispatch(ReaderAction.SetContentVisible(true))
        onDispose { session.dispatch(ReaderAction.SetContentVisible(false)) }
    }
}

@Composable
private fun ReaderScreenState.ReaderChapterEffects(
    state: ReaderState,
    initialTransitionDirection: ReaderChapterTransitionDirection?,
    chapterBookmarked: Boolean,
) {
    LaunchedEffect(session, state.chapterId) {
        val chapterId = state.chapterId ?: return@LaunchedEffect
        val previous = sizeChapterId
        if (previous != null && previous != chapterId) {
            // Drop only the previous chapter; a new page may already have been promoted by its sink.
            val currentChapterKey = chapterId.toString()
            decodedPageSizes.keys
                .filter { it.chapterId != currentChapterKey }
                .forEach { decodedPageSizes.remove(it) }
        }
        sizeChapterId = chapterId
    }
    LaunchedEffect(state.selectedIndex, state.chapterId) {
        panAccumulator.value = null
        livePan = null
    }
    LaunchedEffect(livePan) {
        val pending = livePan ?: return@LaunchedEffect
        delay(PAN_COMMIT_DELAY_MILLIS)
        if (livePan == pending) commitLivePan()
    }
    LaunchedEffect(state.chapterId, chapterBookmarked, effectiveBookmarkStore, this) {
        bookmarked = withContext(Dispatchers.IO) {
            state.chapterId?.let { effectiveBookmarkStore?.isBookmarked(it) } ?: chapterBookmarked
        }
    }
    LaunchedEffect(session, state.chapterId) {
        val chapterId = state.chapterId
        val previous = observedChapterId
        if (chapterId != null && previous != null && chapterId != previous) {
            if (skipArrivalTransition) {
                skipArrivalTransition = false
            } else if (settings.alwaysShowChapterTransition) {
                val direction = pendingChapterDirection ?: ReaderChapterTransitionDirection.NEXT
                chapterTransition = buildChapterTransition(direction, chapterId)
            }
        }
        observedChapterId = chapterId
        pendingChapterDirection = null
    }
    LaunchedEffect(session, state.chapterId, initialTransitionDirection) {
        if (initialTransitionDirection != null && settings.alwaysShowChapterTransition && chapterTransition == null) {
            chapterTransition = buildChapterTransition(initialTransitionDirection, state.chapterId)
        }
    }
}

@Composable
private fun ReaderScreenState.ReaderHudEffects(state: ReaderState) {
    val ready = state.loadState is ReaderLoadState.Ready
    LaunchedEffect(state.zoom, ready) {
        if (!ready) {
            lastZoom = state.zoom
            return@LaunchedEffect
        }
        if (state.zoom == lastZoom) return@LaunchedEffect
        lastZoom = state.zoom
        if (overlayVisibility.chromeVisible) return@LaunchedEffect
        zoomHudVisible = true
        delay(READER_ZOOM_HUD_MILLIS)
        zoomHudVisible = false
    }
    LaunchedEffect(state.selectedIndex, state.chapterId, overlayVisibility.chromeVisible, ready) {
        val chapterId = state.chapterId ?: 0L
        val index = state.selectedIndex
        if (!ready || overlayVisibility.chromeVisible || index < 0) {
            acknowledgedIndicator = index to chapterId
            pageIndicatorIndex = null
            pageIndicatorAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (acknowledgedIndicator == index to chapterId) return@LaunchedEffect
        acknowledgedIndicator = index to chapterId
        pageIndicatorIndex = index
        pageIndicatorAlpha.snapTo(1f)
        delay(READER_PAGE_INDICATOR_MILLIS)
        pageIndicatorAlpha.animateTo(0f, animationSpec = tween(READER_PAGE_INDICATOR_FADE_MILLIS))
        pageIndicatorIndex = null
    }
    LaunchedEffect(state.selectedIndex, state.chapterId, settings.pageFlash, ready) {
        val chapterId = state.chapterId ?: 0L
        val index = state.selectedIndex
        if (!ready || !settings.pageFlash || index < 0) {
            acknowledgedFlashPage = index to chapterId
            isPageFlashing = false
            return@LaunchedEffect
        }
        if (acknowledgedFlashPage == -1 to 0L) {
            acknowledgedFlashPage = index to chapterId
            return@LaunchedEffect
        }
        if (acknowledgedFlashPage == index to chapterId) return@LaunchedEffect
        acknowledgedFlashPage = index to chapterId
        isPageFlashing = true
        delay(100)
        isPageFlashing = false
    }
}

/** Pan is committed to the session once the gesture has been still for this long. */
private const val PAN_COMMIT_DELAY_MILLIS = 120L
