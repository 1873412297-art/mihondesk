package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.reader.input.InputPoint
import mihon.desktop.ui.common.TextInputTracker
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderState
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed

/** Reader viewport composition: input surface, page layer, chrome, HUD and edge reveals. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ReaderScreenContent(
    holder: ReaderScreenState,
    state: ReaderState,
    modifier: Modifier,
    strings: DesktopStrings,
    title: String,
    chapterTitle: String,
    debugEnabled: Boolean,
    pageContent: ReaderPageContent,
    onRetryChapter: suspend () -> Unit,
    mangaId: Long?,
    onOpenMangaDetails: () -> Unit,
    onBack: () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    chapterCatalog: List<ReaderChapterTransitionChapter>,
    currentChapterId: Long?,
    textTracker: TextInputTracker,
    textInputFocused: () -> Boolean,
) {
    val ready = state.loadState is ReaderLoadState.Ready
    Box(modifier = modifier.readerScreenInputs(holder, state, ready, textInputFocused)) {
        ReaderGestureSurface(holder, state, ready, pageContent, onRetryChapter, onBack)
        ReaderDimmingOverlay(holder)
        ReaderChromeHost(
            holder = holder,
            state = state,
            textTracker = textTracker,
            title = title,
            chapterTitle = chapterTitle,
            debugEnabled = debugEnabled,
            mangaId = mangaId,
            onOpenMangaDetails = onOpenMangaDetails,
            onBack = onBack,
            onRetryChapter = onRetryChapter,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            chapterCatalog = chapterCatalog,
            currentChapterId = currentChapterId,
        )
        ReaderEdgeReveals(holder)
        ReaderHudHost(holder, state, strings, ready)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.readerScreenInputs(
    holder: ReaderScreenState,
    state: ReaderState,
    ready: Boolean,
    textInputFocused: () -> Boolean,
): Modifier = this
    .fillMaxSize()
    .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
        if (!ready) return@onPointerEvent
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val deltaPixels = change.scrollDelta.y
        if (deltaPixels == 0f) return@onPointerEvent
        holder.focusRequester.requestFocus()
        val viewport = state.viewport ?: return@onPointerEvent
        val centroid = InputPoint(
            x = (change.position.x / viewport.width.coerceAtLeast(1)).coerceIn(0f, 1f),
            y = (change.position.y / viewport.height.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
        val ctrl = event.keyboardModifiers.isPointerCtrlPressed
        holder.handleWheel(deltaPixels, ctrl, centroid, viewport)
        val continuous = state.mode == ReadingMode.VERTICAL || state.mode == ReadingMode.WEBTOON
        if (ctrl || !continuous) {
            event.changes.forEach { it.consume() }
        }
    }
    .pointerHoverIcon(
        rememberReaderPointerIcon(readerCursorVisible(holder.overlayVisibility.cursorVisible, ready)),
    )
    .testTag("reader-screen")
    .focusRequester(holder.focusRequester)
    .focusable()
    // Preview first so a focused toolbar button cannot swallow Space or the arrows; the bubbling
    // handler keeps them working for focused text fields and the reader settings sliders.
    .onPreviewKeyEvent { !holder.showSettings && !textInputFocused() && holder.handleReaderKey(state, it) }
    .onKeyEvent { holder.handleReaderKey(state, it) }

@Composable
private fun ReaderGestureSurface(
    holder: ReaderScreenState,
    state: ReaderState,
    ready: Boolean,
    pageContent: ReaderPageContent,
    onRetryChapter: suspend () -> Unit,
    onBack: () -> Unit,
) {
    val isWebtoon = state.mode == ReadingMode.WEBTOON
    val currentCrop = if (isWebtoon) holder.settings.cropBordersWebtoon else holder.settings.cropBorders
    ReaderGestureArea(
        enabled = ready,
        onPress = { holder.handlePress(it) },
        onTap = { holder.handleTap(it) },
        onDoubleTap = { holder.handleDoubleTap(it) },
        onPan = { delta, viewport -> holder.handlePan(delta, viewport, live = true) },
        onZoomBy = { factor, centroid, viewport -> holder.handleZoomBy(factor, centroid, viewport) },
        modifier = Modifier.fillMaxSize().testTag("reader-gesture-area"),
        onLongPress = { holder.openPageActions() },
        onSecondaryClick = { holder.openPageActions() },
        onSideButton = holder::handleSideButton,
        onPointerMove = {
            holder.overlayVisibility = reduceReaderOverlayVisibility(
                holder.overlayVisibility,
                ReaderOverlayEvent.PointerMoved,
            )
            holder.chromeHideRequests.tryEmit(Unit)
        },
    ) {
        ReaderPageSurface(holder, state, currentCrop, ready, pageContent, onRetryChapter, onBack)
        if (ready) {
            ReaderClickRegions(
                policy = holder.clickPolicy,
                onAction = { holder.handleClickAction(it) },
            )
        }
    }
}

@Composable
private fun ReaderPageSurface(
    holder: ReaderScreenState,
    state: ReaderState,
    currentCrop: Boolean,
    ready: Boolean,
    pageContent: ReaderPageContent,
    onRetryChapter: suspend () -> Unit,
    onBack: () -> Unit,
) {
    val composeColorFilter = remember(
        holder.settings.colorFilter,
        holder.settings.customHue,
        holder.settings.customBrightness,
        holder.settings.customContrast,
    ) {
        holder.settings.toComposeColorFilter()
    }
    CompositionLocalProvider(
        LocalReaderColorFilter provides holder.settings.colorFilter,
        LocalReaderComposeColorFilter provides composeColorFilter,
        LocalReaderPageTransition provides holder.settings.pageTransition,
        LocalReaderCropBorders provides currentCrop,
        LocalReaderForeground provides state.foreground,
        LocalReaderSelectedPage provides state.pages.getOrNull(state.selectedIndex)?.id,
        LocalReaderPageSizeSink provides holder.pageSizeSink,
        LocalReaderPageImageStore provides holder.imageStore,
        LocalReaderAutoScrollSpeed provides holder.settings.autoScrollSpeed.takeIf {
            holder.autoScrolling && holder.settings.mode.isContinuousForAutoScroll() && ready
        },
    ) {
        ReaderBody(
            state = state,
            session = holder.session,
            onAction = holder.session::dispatch,
            onRetryChapter = onRetryChapter,
            backgroundColor = holder.settings.backgroundColor,
            webtoonMaxWidth = holder.settings.webtoonMaxWidth,
            webtoonSidePadding = holder.settings.webtoonSidePadding,
            pageSizes = holder.effectivePageSizes,
            pageContent = pageContent,
            onCancelLoading = { holder.closeAndThen(onBack) },
            panProvider = { holder.effectivePan() },
        )
    }
}

@Composable
private fun BoxScope.ReaderDimmingOverlay(holder: ReaderScreenState) {
    if (holder.settings.dimmingPercent >= 100) return
    val dimmingAlpha = (100 - holder.settings.dimmingPercent.coerceIn(20, 100)) / 100f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimmingAlpha))
            .testTag("reader-dimming-overlay"),
    )
}

@Composable
private fun BoxScope.ReaderEdgeReveals(holder: ReaderScreenState) {
    fun reveal() {
        holder.overlayVisibility =
            reduceReaderOverlayVisibility(holder.overlayVisibility, ReaderOverlayEvent.PointerAtEdge)
        holder.chromeHideRequests.tryEmit(Unit)
    }
    ReaderEdgeReveal(tag = "reader-top-reveal", modifier = Modifier.align(Alignment.TopCenter)) { reveal() }
    ReaderEdgeReveal(tag = "reader-bottom-reveal", modifier = Modifier.align(Alignment.BottomCenter)) { reveal() }
}

@Composable
private fun BoxScope.ReaderHudHost(
    holder: ReaderScreenState,
    state: ReaderState,
    strings: DesktopStrings,
    ready: Boolean,
) {
    ReaderHudLayer(
        state = state,
        settings = holder.settings,
        strings = strings,
        chromeVisible = holder.overlayVisibility.chromeVisible,
        ready = ready,
        showSettings = holder.showSettings,
        onSettingsChange = holder::applySettings,
        onDismissSettings = { holder.showSettings = false },
        pageActionMessage = holder.pageActionMessage,
        onDismissPageActionMessage = { holder.pageActionMessage = null },
        pageIndicatorIndex = holder.pageIndicatorIndex,
        pageIndicatorAlpha = { holder.pageIndicatorAlpha.value },
        zoomHudVisible = holder.zoomHudVisible,
        autoScrolling = holder.autoScrolling,
        autoScrollHudTick = holder.autoScrollHudTick,
        isPageFlashing = holder.isPageFlashing,
    )
}
