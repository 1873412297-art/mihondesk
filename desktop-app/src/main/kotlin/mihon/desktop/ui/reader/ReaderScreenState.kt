package mihon.desktop.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.image.CustomCoverManager
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.platform.ScreenAwakeController
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.input.ClickRegionPolicy
import mihon.desktop.reader.input.ReaderInputMapper
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession

/** Observable state and non-input actions for one open reader screen. */
internal class ReaderScreenState(
    val session: ReaderSession,
    val settingsStore: DesktopReaderSettingsStore?,
    val readerSettingsOverride: MangaReaderSettingsOverride?,
    val strings: DesktopStrings,
    val scope: CoroutineScope,
    val density: Density,
    val keyBindingStore: DesktopKeyBindingStore?,
    val chapterTitle: String,
    val chapterBookmarked: Boolean,
    val bookmarkStore: ReaderChapterBookmarkStore?,
    val pageActionTargetProvider: (PageDescriptor, Int) -> ReaderPageActionTarget,
    val pageActionHandler: ReaderPageActionHandler?,
    val pageImageStore: ReaderPageImageStore?,
    val customCoverManager: CustomCoverManager?,
    knownPageSizes: Map<PageId, PageSize>,
    val chapterCatalog: List<ReaderChapterTransitionChapter>,
    val previousChapter: ReaderChapterTransitionChapter?,
    val nextChapter: ReaderChapterTransitionChapter?,
    val currentChapter: ReaderChapterTransitionChapter?,
    val currentChapterDownloaded: Boolean,
    val doubleTapZoom: Float,
    val hasPreviousChapter: Boolean,
    val hasNextChapter: Boolean,
    val onBack: () -> Unit,
    val onFullscreen: () -> Unit,
    val onBorderless: () -> Unit,
    val onEscape: () -> Boolean,
    val onPreviousChapter: () -> Unit,
    val onNextChapter: () -> Unit,
    val onChapterSelected: ((Long) -> Unit)?,
    val onChapterBookmarkChanged: (Boolean) -> Unit,
) {
    var knownPageSizes by mutableStateOf(knownPageSizes)
    var settings by mutableStateOf(session.state.value.toDesktopSettings().withOverride(readerSettingsOverride))
    var overlayVisibility by mutableStateOf(ReaderOverlayVisibilityState())
    val chromeHideRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var showSettings by mutableStateOf(false)
    var autoScrolling by mutableStateOf(false)
    var autoScrollHudTick by mutableStateOf(0)
    var showShortcuts by mutableStateOf(false)
    var closing by mutableStateOf(false)
    var pressChromeTarget by mutableStateOf<Boolean?>(null)
    val panAccumulator = ReaderPanAccumulator()
    var livePan by mutableStateOf<ReaderPan?>(null)
    fun effectivePan(): ReaderPan = livePan ?: session.state.value.pan
    fun commitLivePan() {
        val pending = livePan ?: return
        livePan = null
        session.dispatch(ReaderAction.SetPan(pending))
    }
    val clickPolicy: ClickRegionPolicy get() = ClickRegionPolicy.from(settings.clickRegions)
    val inputMapper = ReaderInputMapper()
    val focusRequester = FocusRequester()
    val decodedPageSizes = mutableStateMapOf<PageId, PageSize>()
    val pageSizeSink = ReaderPageSizeSink { pageId, size ->
        if (decodedPageSizes[pageId] != size) {
            decodedPageSizes[pageId] = size
            session.dispatch(ReaderAction.SetPageIntrinsicSize(pageId, size.width, size.height))
        }
    }
    val effectivePageSizes: Map<PageId, PageSize> get() = knownPageSizes + decodedPageSizes
    var sizeChapterId by mutableStateOf<Long?>(null)
    val imageStore = pageImageStore ?: ReaderPageImageStore()
    val effectivePageActionHandler = pageActionHandler
        ?: defaultReaderPageActionHandler(imageStore, customCoverManager, strings.text(UiText.SavePageImage))
    val effectiveBookmarkStore = bookmarkStore ?: settingsStore?.bookmarkStore()
    var pageActionsTarget by mutableStateOf<ReaderPageActionTarget?>(null)
    var pageActionMessage by mutableStateOf<String?>(null)
    var chapterTransition by mutableStateOf<ReaderChapterTransition?>(null)
    var pendingExternalChapterDirection by mutableStateOf<ReaderChapterTransitionDirection?>(null)
    var pendingChapterDirection by mutableStateOf<ReaderChapterTransitionDirection?>(null)
    var skipArrivalTransition by mutableStateOf(false)
    var observedChapterId by mutableStateOf(session.state.value.chapterId)
    var bookmarked by mutableStateOf(chapterBookmarked)
    var acknowledgedIndicator by mutableStateOf(-1 to 0L)
    var pageIndicatorIndex by mutableStateOf<Int?>(null)
    val pageIndicatorAlpha = Animatable(0f)
    var zoomHudVisible by mutableStateOf(false)
    var lastZoom by mutableStateOf(session.state.value.zoom)
    var acknowledgedFlashPage by mutableStateOf(-1 to 0L)
    var isPageFlashing by mutableStateOf(false)
    val screenAwake = ScreenAwakeController()

    fun markReadingInput() {
        overlayVisibility = reduceReaderOverlayVisibility(overlayVisibility, ReaderOverlayEvent.ReadingInput)
        chromeHideRequests.tryEmit(Unit)
    }

    /** The single F11 toggle; the reader setting picks OS fullscreen or a borderless window. */
    fun toggleImmersive() {
        if (settings.immersiveBorderless) onBorderless() else onFullscreen()
    }

    fun applySettings(updated: DesktopReaderSettings) {
        settings = updated
        settingsStore?.save(updated)
        session.dispatch(ReaderAction.ChangeMode(updated.mode))
        session.dispatch(ReaderAction.SetScaleMode(updated.scaleMode))
        session.dispatch(ReaderAction.SetCoverOffset(updated.coverOffset))
        session.dispatch(ReaderAction.SetDualPageSplit(updated.dualPageSplit))
        session.dispatch(ReaderAction.SetDualPageRotateToFit(updated.dualPageRotateToFit))
    }

    fun transitionChapterInfo(chapterId: Long? = session.state.value.chapterId): ReaderChapterTransitionChapter {
        val catalogChapter = chapterId?.let { id -> chapterCatalog.firstOrNull { it.id == id } }
        if (catalogChapter != null) return catalogChapter
        val explicit = currentChapter
        return when {
            explicit != null && (chapterId == null || explicit.id == chapterId) -> explicit
            explicit != null -> explicit.copy(id = chapterId)
            else -> ReaderChapterTransitionChapter(
                id = chapterId,
                title = chapterTitle,
                downloaded = currentChapterDownloaded,
            )
        }
    }

    fun buildChapterTransition(
        direction: ReaderChapterTransitionDirection,
        chapterId: Long? = session.state.value.chapterId,
    ): ReaderChapterTransition {
        val current = transitionChapterInfo(chapterId)
        val currentState = session.state.value
        val previousFallback = previousChapter ?: if (currentState.hasPreviousChapter) {
            ReaderChapterTransitionChapter(title = strings.readerPreviousChapter)
        } else {
            null
        }
        val nextFallback = nextChapter ?: if (currentState.hasNextChapter) {
            ReaderChapterTransitionChapter(title = strings.readerNextChapter)
        } else {
            null
        }
        return readerChapterTransition(
            direction = direction,
            current = current,
            chapters = chapterCatalog,
            previous = previousFallback,
            next = nextFallback,
            settings = settings,
        )
    }

    fun closeAndThen(afterClose: () -> Unit) {
        if (closing) return
        closing = true
        if (session.state.value.loadState is ReaderLoadState.Loading ||
            session.state.value.loadState is ReaderLoadState.Idle
        ) {
            session.cancelWithoutFlush()
            afterClose()
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { session.closeAndFlush() } }
                .onFailure {
                    mihon.desktop.logging.DesktopLogger.debug("Reader", "Session close/flush failed", it)
                }
            afterClose()
        }
    }

    fun navigateExternalChapter(direction: ReaderChapterTransitionDirection, targetChapterId: Long? = null) {
        // Opening the next chapter on this session flushes progress. Closing first would
        // destroy the viewer and cancel the pages already warming for the boundary.
        skipArrivalTransition = true
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { session.flushProgress() } }
                .onFailure {
                    mihon.desktop.logging.DesktopLogger.debug("Reader", "Session flush failed", it)
                }
            if (targetChapterId != null && onChapterSelected != null) {
                onChapterSelected(targetChapterId)
            } else {
                when (direction) {
                    ReaderChapterTransitionDirection.PREVIOUS -> onPreviousChapter()
                    ReaderChapterTransitionDirection.NEXT -> onNextChapter()
                }
            }
        }
    }

    fun openPageActions() {
        val current = session.state.value
        val page = current.pages.getOrNull(current.selectedIndex) ?: return
        pageActionsTarget = pageActionTargetProvider(page, current.selectedIndex)
    }

    fun runPageAction(
        target: ReaderPageActionTarget,
        block: suspend (ReaderPageActionHandler, ReaderPageActionTarget) -> Boolean,
    ) {
        pageActionsTarget = null
        scope.launch {
            val success = runCatching { block(effectivePageActionHandler, target) }.getOrDefault(false)
            if (!success) pageActionMessage = strings.text(UiText.PageActionFailed)
        }
    }

    fun toggleChapterBookmark() {
        val chapterId = session.state.value.chapterId ?: return
        val updated = !bookmarked
        bookmarked = updated
        effectiveBookmarkStore?.setBookmarked(chapterId, updated)
        onChapterBookmarkChanged(updated)
    }

    fun openChapter(direction: ReaderChapterTransitionDirection) {
        if (settings.alwaysShowChapterTransition) {
            pendingExternalChapterDirection = direction
            chapterTransition = buildChapterTransition(direction)
        } else {
            navigateExternalChapter(direction)
        }
    }

    /** Keyboard chapter jumps are silently ignored at the first/last chapter, like the disabled chrome buttons. */
    fun requestChapter(direction: ReaderChapterTransitionDirection) {
        val current = session.state.value
        val available = when (direction) {
            ReaderChapterTransitionDirection.PREVIOUS -> current.hasPreviousChapter || hasPreviousChapter
            ReaderChapterTransitionDirection.NEXT -> current.hasNextChapter || hasNextChapter
        }
        if (available) openChapter(direction)
    }
}
