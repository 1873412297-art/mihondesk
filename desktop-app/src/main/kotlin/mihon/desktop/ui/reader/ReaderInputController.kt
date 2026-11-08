package mihon.desktop.ui.reader

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.input.InputPoint
import mihon.desktop.reader.input.ReaderInputCommand
import mihon.desktop.reader.input.ReaderInputContext
import mihon.desktop.reader.input.ReaderSideButton
import mihon.desktop.reader.input.nextForShortcut
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyChord
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState

internal fun ReaderScreenState.dispatchCore(action: ReaderAction) {
    val current = session.state.value
    val boundaryDirection = when {
        action == ReaderAction.Next && current.selectedIndex == current.pages.lastIndex -> {
            ReaderChapterTransitionDirection.NEXT
        }
        action == ReaderAction.Previous && current.selectedIndex == 0 -> {
            ReaderChapterTransitionDirection.PREVIOUS
        }
        else -> null
    }
    if (boundaryDirection != null) {
        val hasAdjacentChapter = when (boundaryDirection) {
            ReaderChapterTransitionDirection.PREVIOUS -> current.hasPreviousChapter || hasPreviousChapter
            ReaderChapterTransitionDirection.NEXT -> current.hasNextChapter || hasNextChapter
        }
        if (settings.alwaysShowChapterTransition) {
            if (hasAdjacentChapter) pendingExternalChapterDirection = boundaryDirection
            chapterTransition = buildChapterTransition(boundaryDirection)
            if (!hasAdjacentChapter) session.dispatch(action)
        } else if (hasAdjacentChapter) {
            navigateExternalChapter(boundaryDirection)
        } else {
            // Preserve the core no-op boundary action for input/progress observers.
            session.dispatch(action)
        }
        return
    }
    session.dispatch(action)
}

internal fun ReaderScreenState.handlePress(normalizedX: Float) {
    val action = clickPolicy.actionAt(normalizedX)
    pressChromeTarget = if (action == ReaderClickAction.TOGGLE_CHROME) !overlayVisibility.chromeVisible else null
    markReadingInput()
}

internal fun ReaderScreenState.handleClickAction(action: ReaderClickAction, pointer: Boolean = false) {
    when (action) {
        ReaderClickAction.PREVIOUS -> {
            dispatchCore(ReaderAction.Previous)
            if (!pointer) markReadingInput()
        }
        ReaderClickAction.NEXT -> {
            dispatchCore(ReaderAction.Next)
            if (!pointer) markReadingInput()
        }
        ReaderClickAction.TOGGLE_CHROME -> {
            val visible = if (pointer) {
                pressChromeTarget ?: !overlayVisibility.chromeVisible
            } else {
                !overlayVisibility.chromeVisible
            }
            overlayVisibility = overlayVisibility.copy(chromeVisible = visible, cursorVisible = true)
            if (pointer) pressChromeTarget = null
        }
        ReaderClickAction.NONE -> if (!pointer) markReadingInput()
    }
}

internal fun ReaderScreenState.panBoundsFor(
    current: ReaderState,
    viewport: ReaderViewport,
    zoom: Float,
): ReaderPanBounds {
    val webtoonMaxWidthPixels = with(density) { settings.webtoonMaxWidth.dp.roundToPx() }
    val selectedPageSize = current.pages.getOrNull(current.selectedIndex)?.let { effectivePageSizes[it.id] }
    return ReaderGesturePolicy.panBounds(
        state = current,
        viewport = viewport,
        zoom = zoom,
        webtoonMaxWidthPixels = webtoonMaxWidthPixels,
        webtoonSidePaddingPercent = settings.webtoonSidePadding,
        pageSize = selectedPageSize,
    )
}

internal fun ReaderScreenState.handlePan(delta: ReaderPan, viewport: ReaderViewport, live: Boolean = false) {
    val current = session.state.value
    if (current.pages.isEmpty()) return
    val effectiveDelta = if (current.mode == ReadingMode.VERTICAL || current.mode == ReadingMode.WEBTOON) {
        ReaderPan(delta.x, 0f)
    } else {
        delta
    }
    if (effectiveDelta.x == 0f && effectiveDelta.y == 0f) return
    val base = panAccumulator.value ?: current.pan
    val target = panBoundsFor(current, viewport, current.zoom).panBy(base, effectiveDelta)
    if (target == base) return
    panAccumulator.value = target
    if (live) {
        // Gesture frames only update the draw-phase pan; the session sees one update when it settles.
        livePan = target
    } else {
        session.dispatch(ReaderAction.SetPan(target))
    }
    markReadingInput()
}

internal fun ReaderScreenState.applyZoom(command: ReaderInputCommand.ZoomBy, viewport: ReaderViewport?) {
    val current = session.state.value
    val rawZoom = if (command.factor == 0f) {
        ReaderGesturePolicy.FIT_ZOOM
    } else {
        ReaderLayout.clampZoom(current.zoom * command.factor)
    }
    val targetZoom = ReaderGesturePolicy.clampWebtoonZoom(
        mode = current.mode,
        zoom = rawZoom,
        preventDownsizing = settings.webtoonPreventDownsizing,
    )
    livePan = null
    session.dispatch(ReaderAction.SetZoom(targetZoom))
    if (command.factor == 0f) {
        panAccumulator.value = ReaderPan(0f, 0f)
        session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
    } else if (viewport != null) {
        val base = panAccumulator.value ?: current.pan
        val targetPan = panBoundsFor(current, viewport, targetZoom)
            .clamp(ReaderGesturePolicy.scalePan(base, current.zoom, targetZoom))
        panAccumulator.value = targetPan
        session.dispatch(ReaderAction.SetPan(targetPan))
    }
    markReadingInput()
}

internal fun ReaderScreenState.handleZoomBy(factor: Float, centroid: InputPoint, viewport: ReaderViewport) {
    val command = inputMapper.mapPinch(factor, centroid).action as? ReaderInputCommand.ZoomBy ?: return
    applyZoom(command, viewport)
}
internal fun ReaderScreenState.handleInput(command: ReaderInputCommand) {
    when (command) {
        is ReaderInputCommand.Core -> {
            dispatchCore(command.action)
            markReadingInput()
        }
        is ReaderInputCommand.ZoomBy -> applyZoom(command, session.state.value.viewport)
        ReaderInputCommand.PageActions -> openPageActions()
        ReaderInputCommand.Fullscreen -> toggleImmersive()
        ReaderInputCommand.Borderless -> onBorderless()
        ReaderInputCommand.Escape -> {
            if (onEscape()) closeAndThen(onBack)
        }
        ReaderInputCommand.PreviousChapter -> requestChapter(ReaderChapterTransitionDirection.PREVIOUS)
        ReaderInputCommand.NextChapter -> requestChapter(ReaderChapterTransitionDirection.NEXT)
        ReaderInputCommand.ToggleBookmark -> toggleChapterBookmark()
        ReaderInputCommand.CycleReadingMode -> applySettings(settings.copy(mode = settings.mode.nextForShortcut()))
        ReaderInputCommand.ShowHelp -> showShortcuts = true
        ReaderInputCommand.ToggleAutoScroll -> {
            autoScrolling = !autoScrolling && settings.mode.isContinuousForAutoScroll()
            autoScrollHudTick++
        }
        is ReaderInputCommand.AdjustAutoScroll -> {
            val controller = mihon.desktop.reader.AutoScrollController(settings.autoScrollSpeed)
            val speed = if (command.faster) controller.faster() else controller.slower()
            settings = settings.copy(autoScrollSpeed = speed)
            settingsStore?.save(settings)
            autoScrollHudTick++
        }
    }
}

internal fun ReaderScreenState.handleWheel(
    deltaPixels: Float,
    ctrl: Boolean,
    centroid: InputPoint,
    viewport: ReaderViewport,
) {
    val current = session.state.value
    val continuous = current.mode == ReadingMode.VERTICAL || current.mode == ReadingMode.WEBTOON
    if (!ctrl && continuous) return
    val result = inputMapper.mapWheel(
        deltaPixels = deltaPixels,
        context = ReaderInputContext(
            mode = current.mode,
            wheelBehavior = settings.wheelBehavior,
            pageCount = current.pages.size.coerceAtLeast(1),
            anchor = current.viewportAnchor,
        ),
        ctrl = ctrl,
        centroid = centroid,
    )
    when (val command = result.action) {
        null -> Unit
        is ReaderInputCommand.ZoomBy -> applyZoom(command, viewport)
        is ReaderInputCommand.Core -> dispatchCore(command.action)
        else -> handleInput(command)
    }
}

internal fun ReaderScreenState.handleDoubleTap(viewport: ReaderViewport) {
    val current = session.state.value
    if (!ReaderGesturePolicy.shouldDoubleTapZoom(current.mode, settings.webtoonDoubleTapZoom)) {
        return
    }
    val targetZoom = ReaderGesturePolicy.doubleTapZoom(current.zoom, doubleTapZoom)
    panAccumulator.value = ReaderPan(0f, 0f)
    livePan = null
    session.dispatch(ReaderAction.SetZoom(targetZoom))
    session.dispatch(ReaderAction.SetPan(ReaderPan(0f, 0f)))
    markReadingInput()
}

internal fun ReaderScreenState.handleTap(normalizedX: Float) {
    focusRequester.requestFocus()
    handleClickAction(clickPolicy.actionAt(normalizedX), pointer = true)
}

internal fun ReaderScreenState.handleSideButton(button: ReaderSideButton) {
    val current = session.state.value
    inputMapper.mapSideButton(
        button,
        ReaderInputContext(
            mode = current.mode,
            wheelBehavior = settings.wheelBehavior,
            pageCount = current.pages.size.coerceAtLeast(1),
            anchor = current.viewportAnchor,
        ),
    ).action?.let(::handleInput)
}
internal fun ReaderScreenState.handleReaderKey(state: ReaderState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        val dismissedOverlay = when {
            pageActionsTarget != null -> true.also { pageActionsTarget = null }
            showSettings -> true.also { showSettings = false }
            showShortcuts -> true.also { showShortcuts = false }
            chapterTransition != null -> true.also { chapterTransition = null }
            else -> false
        }
        if (dismissedOverlay) return true
    }
    val readerBindings = keyBindingStore?.load()?.reader ?: DesktopKeyBindings.defaultReader
    val shortcut = readerBindings.match(KeyChord.from(event)) ?: return false
    val result = inputMapper.mapAction(
        shortcut,
        ReaderInputContext(
            mode = state.mode,
            wheelBehavior = settings.wheelBehavior,
            pageCount = state.pages.size.coerceAtLeast(1),
            anchor = state.viewportAnchor,
            ctrl = event.isCtrlPressed,
            shift = event.isShiftPressed,
        ),
    )
    result.action?.let(::handleInput)
    return result.consumed
}

internal fun ReaderScreenState.selectPage(pageIndex: Int) {
    session.dispatch(ReaderAction.SelectPage(pageIndex))
    markReadingInput()
}

internal fun ReaderScreenState.previousPage() {
    dispatchCore(ReaderAction.Previous)
    markReadingInput()
}

internal fun ReaderScreenState.nextPage() {
    dispatchCore(ReaderAction.Next)
    markReadingInput()
}

internal fun ReaderScreenState.retryCurrentPage(onRetryChapter: suspend () -> Unit) {
    scope.launch {
        val current = session.state.value
        current.pages.getOrNull(current.selectedIndex)?.id?.let { session.retry(it) } ?: onRetryChapter()
    }
}

internal fun ReaderScreenState.selectChapterFromChrome(targetId: Long) {
    navigateExternalChapter(ReaderChapterTransitionDirection.NEXT, targetId)
}
