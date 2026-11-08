package mihon.desktop.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.input.ClickRegionPolicy
import mihon.desktop.reader.input.InputPoint
import mihon.desktop.reader.input.ReaderInputCommand
import mihon.desktop.reader.input.ReaderInputContext
import mihon.desktop.reader.input.ReaderInputMapper
import mihon.desktop.reader.input.ReaderSideButton
import mihon.desktop.reader.input.nextForShortcut
import mihon.desktop.ui.common.LocalTextInputTracker
import mihon.desktop.ui.common.rememberScopedTextInputTracker
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyChord
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderSessionError
import mihon.reader.session.ReaderState
import mihon.reader.source.ReaderFailure
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ReaderScreen(
    session: ReaderSession,
    title: String,
    chapterTitle: String,
    settingsStore: DesktopReaderSettingsStore?,
    onBack: () -> Unit,
    keyBindingStore: DesktopKeyBindingStore? = null,
    onOpenMangaDetails: () -> Unit = {},
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onBorderless: () -> Unit = {},
    onEscape: () -> Boolean = { true },
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: ((Long) -> Unit)? = null,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    onRetryChapter: suspend () -> Unit = {},
    foreground: Boolean = true,
    debugEnabled: Boolean = System.getenv("MIHON_W_READER_DEBUG") == "1",
    doubleTapZoom: Float = ReaderGesturePolicy.DEFAULT_DOUBLE_TAP_ZOOM,
    knownPageSizes: Map<PageId, PageSize> = emptyMap(),
    chapterBookmarked: Boolean = false,
    onChapterBookmarkChanged: (Boolean) -> Unit = {},
    bookmarkStore: ReaderChapterBookmarkStore? = null,
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    previousChapter: ReaderChapterTransitionChapter? = null,
    nextChapter: ReaderChapterTransitionChapter? = null,
    currentChapter: ReaderChapterTransitionChapter? = null,
    currentChapterDownloaded: Boolean = false,
    initialTransitionDirection: ReaderChapterTransitionDirection? = null,
    onChapterTransitionContinue: ((ReaderChapterTransitionDirection) -> Unit)? = null,
    pageActionHandler: ReaderPageActionHandler? = null,
    mangaId: Long? = null,
    readerSettingsOverride: MangaReaderSettingsOverride? = null,
    pageUrlResolver: (PageDescriptor) -> String? = { null },
    pageActionTargetProvider: (PageDescriptor, Int) -> ReaderPageActionTarget = { page, index ->
        ReaderPageActionTarget(
            page = page,
            pageIndex = index,
            pageUrl = pageUrlResolver(page),
            mangaId = mangaId,
        )
    },
    pageImageStore: ReaderPageImageStore? = null,
    pageContent: ReaderPageContent = { _, pageIndex, contentModifier ->
        ReaderPagePlaceholder(pageIndex, contentModifier)
    },
) {
    val state by session.state.collectAsState()
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val (textTracker, textInputFocused) = rememberScopedTextInputTracker()
    val customCoverManager = LocalCustomCoverManager.current
    val holder = remember(
        session, settingsStore, readerSettingsOverride, bookmarkStore, pageImageStore, pageActionHandler,
        customCoverManager, strings, scope, keyBindingStore, doubleTapZoom, hasPreviousChapter, hasNextChapter,
        chapterCatalog, previousChapter, nextChapter, currentChapter, currentChapterDownloaded, chapterTitle,
    ) {
        ReaderScreenState(
            session = session,
            settingsStore = settingsStore,
            readerSettingsOverride = readerSettingsOverride,
            strings = strings,
            scope = scope,
            density = density,
            keyBindingStore = keyBindingStore,
            chapterTitle = chapterTitle,
            chapterBookmarked = chapterBookmarked,
            bookmarkStore = bookmarkStore,
            pageActionTargetProvider = pageActionTargetProvider,
            pageActionHandler = pageActionHandler,
            pageImageStore = pageImageStore,
            customCoverManager = customCoverManager,
            knownPageSizes = knownPageSizes,
            chapterCatalog = chapterCatalog,
            previousChapter = previousChapter,
            nextChapter = nextChapter,
            currentChapter = currentChapter,
            currentChapterDownloaded = currentChapterDownloaded,
            doubleTapZoom = doubleTapZoom,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            onBack = onBack,
            onFullscreen = onFullscreen,
            onBorderless = onBorderless,
            onEscape = onEscape,
            onPreviousChapter = onPreviousChapter,
            onNextChapter = onNextChapter,
            onChapterSelected = onChapterSelected,
            onChapterBookmarkChanged = onChapterBookmarkChanged,
        )
    }
    LaunchedEffect(knownPageSizes) {
        holder.knownPageSizes = knownPageSizes
    }
    holder.ReaderScreenEffects(state, initialTransitionDirection, foreground, chapterBookmarked)
    ReaderScreenContent(
        holder = holder,
        state = state,
        modifier = modifier,
        strings = strings,
        title = title,
        chapterTitle = chapterTitle,
        debugEnabled = debugEnabled,
        pageContent = pageContent,
        onRetryChapter = onRetryChapter,
        mangaId = mangaId,
        onOpenMangaDetails = onOpenMangaDetails,
        onBack = onBack,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        chapterCatalog = chapterCatalog,
        currentChapterId = currentChapter?.id,
        textTracker = textTracker,
        textInputFocused = textInputFocused,
    )
    ReaderDialogLayer(
        transition = holder.chapterTransition,
        transitionDismissable = holder.pendingExternalChapterDirection != null,
        onContinueTransition = { transition ->
            val externalDirection = holder.pendingExternalChapterDirection
            holder.chapterTransition = null
            holder.pendingExternalChapterDirection = null
            onChapterTransitionContinue?.invoke(transition.direction)
            if (externalDirection != null) {
                holder.navigateExternalChapter(externalDirection, transition.target?.id)
            }
        },
        onDismissTransition = {
            holder.chapterTransition = null
            holder.pendingExternalChapterDirection = null
        },
        settings = holder.settings,
        pageActionsTarget = holder.pageActionsTarget,
        onRunPageAction = holder::runPageAction,
        onDismissPageActions = { holder.pageActionsTarget = null },
        showShortcuts = holder.showShortcuts,
        onDismissShortcuts = { holder.showShortcuts = false },
        keyBindingStore = keyBindingStore,
    )
}
