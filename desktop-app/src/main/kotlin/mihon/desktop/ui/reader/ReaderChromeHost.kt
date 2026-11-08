package mihon.desktop.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import mihon.desktop.ui.common.LocalTextInputTracker
import mihon.desktop.ui.common.TextInputTracker
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderState

/** Reader chrome plus the text-input tracker it needs for focused toolbar fields. */
@Composable
internal fun ReaderChromeHost(
    holder: ReaderScreenState,
    state: ReaderState,
    textTracker: TextInputTracker,
    title: String,
    chapterTitle: String,
    debugEnabled: Boolean,
    mangaId: Long?,
    onOpenMangaDetails: () -> Unit,
    onBack: () -> Unit,
    onRetryChapter: suspend () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    chapterCatalog: List<ReaderChapterTransitionChapter>,
    currentChapterId: Long?,
) {
    CompositionLocalProvider(LocalTextInputTracker provides textTracker) {
        ReaderChrome(
            state = state,
            title = title,
            chapterTitle = chapterTitle,
            settings = holder.settings,
            visible = holder.overlayVisibility.chromeVisible,
            canRetry = state.loadState is ReaderLoadState.Failed || state.error != null,
            debugEnabled = debugEnabled,
            onBack = {
                holder.closeAndThen(onBack)
            },
            onOpenMangaDetails = if (mangaId != null) ({ holder.closeAndThen(onOpenMangaDetails) }) else null,
            onControlsHovered = { hovered ->
                holder.overlayVisibility = reduceReaderOverlayVisibility(
                    holder.overlayVisibility,
                    if (hovered) ReaderOverlayEvent.ControlEntered else ReaderOverlayEvent.ControlExited,
                )
                holder.chromeHideRequests.tryEmit(Unit)
            },
            onMode = { holder.applySettings(holder.settings.copy(mode = it)) },
            onScale = { holder.applySettings(holder.settings.copy(scaleMode = it)) },
            onCoverOffset = { holder.applySettings(holder.settings.copy(coverOffset = it)) },
            onZoom = {
                val targetZoom = ReaderGesturePolicy.clampWebtoonZoom(
                    mode = state.mode,
                    zoom = it,
                    preventDownsizing = holder.settings.webtoonPreventDownsizing,
                )
                holder.session.dispatch(ReaderAction.SetZoom(targetZoom))
            },
            onRetry = { holder.retryCurrentPage(onRetryChapter) },
            onFullscreen = holder::toggleImmersive,
            onOpenSettings = { holder.showSettings = true },
            onColorFilter = { holder.applySettings(holder.settings.copy(colorFilter = it)) },
            onBackgroundColor = { holder.applySettings(holder.settings.copy(backgroundColor = it)) },
            onCropBorders = { holder.applySettings(holder.settings.copy(cropBorders = it)) },
            onCropBordersWebtoon = { holder.applySettings(holder.settings.copy(cropBordersWebtoon = it)) },
            onPageSelected = holder::selectPage,
            onPreviousPage = holder::previousPage,
            onNextPage = holder::nextPage,
            onPreviousChapter = { holder.openChapter(ReaderChapterTransitionDirection.PREVIOUS) },
            onNextChapter = { holder.openChapter(ReaderChapterTransitionDirection.NEXT) },
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            bookmarked = holder.bookmarked,
            onToggleBookmark = holder::toggleChapterBookmark,
            onOpenPageActions = holder::openPageActions,
            onOpenShortcuts = { holder.showShortcuts = true },
            chapterCatalog = chapterCatalog,
            currentChapterId = currentChapterId,
            onChapterSelected = holder::selectChapterFromChrome,
        )
    }
}
