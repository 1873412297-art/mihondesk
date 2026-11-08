package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.library.model.readerSettingsOverride
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.reader.DesktopReaderHandle
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.ReaderChapterTransitionChapter
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.reader.session.ReaderLoadState

@Composable
internal fun ReaderDestination(
    destination: DesktopDestination.Reader,
    runtime: DesktopRuntime,
    mangaTitle: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenMangaDetails: () -> Unit,
    onFullscreen: () -> Unit,
    onBorderless: () -> Unit,
    onEscape: () -> Boolean,
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onChapterSelected: ((Long) -> Unit)? = null,
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    mangaId: Long? = null,
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    previousChapter: ReaderChapterTransitionChapter? = null,
    nextChapter: ReaderChapterTransitionChapter? = null,
    currentChapter: ReaderChapterTransitionChapter? = null,
    currentChapterDownloaded: Boolean = false,
    bookmarkStore: ReaderChapterBookmarkStore? = null,
    foreground: Boolean = true,
    landAtEnd: Boolean = false,
) {
    val strings = LocalStrings.current
    if (runtime.readerFactory == null) {
        Text(strings.readerUnavailable)
        return
    }
    val openContext by produceState(ReaderOpenContext(), destination.chapterId, mangaId) {
        value = withContext(Dispatchers.IO) { resolveReaderOpenContext(runtime, destination.chapterId, mangaId) }
    }
    val effectiveMangaId = openContext.mangaId
    val override = openContext.override
    if (!openContext.ready) {
        ReaderOpening(strings.readerOpeningChapter)
        return
    }
    val activeHandle = rememberReaderHandle(runtime, destination.chapterId, effectiveMangaId, override)
    OpenReaderChapter(activeHandle, destination.chapterId, landAtEnd)
    if (activeHandle == null) return ReaderOpening(strings.readerOpeningChapter)
    val activeSession = activeHandle.session
    ReaderScreen(
        session = activeSession,
        title = mangaTitle,
        chapterTitle = chapterTitle,
        settingsStore = remember(runtime.preferences) { DesktopReaderSettingsStore(runtime.preferences) },
        keyBindingStore = remember(runtime.preferences) { DesktopKeyBindingStore(runtime.preferences) },
        readerSettingsOverride = override,
        onBack = onBack,
        onOpenMangaDetails = onOpenMangaDetails,
        onFullscreen = onFullscreen,
        onBorderless = onBorderless,
        onEscape = onEscape,
        onPreviousChapter = onPreviousChapter,
        onNextChapter = onNextChapter,
        onChapterSelected = onChapterSelected,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        onRetryChapter = { activeSession.open(destination.chapterId) },
        mangaId = mangaId,
        chapterCatalog = chapterCatalog,
        previousChapter = previousChapter,
        nextChapter = nextChapter,
        currentChapter = currentChapter,
        currentChapterDownloaded = currentChapterDownloaded,
        bookmarkStore = bookmarkStore,
        foreground = foreground,
        pageContent = { page, _, modifier ->
            DecodedReaderPage(content = activeHandle.content, page = page, modifier = modifier)
        },
    )
}

private class ReaderOpenContext(
    val mangaId: Long? = null,
    val override: MangaReaderSettingsOverride? = null,
    val ready: Boolean = false,
)

private fun resolveReaderOpenContext(
    runtime: DesktopRuntime,
    chapterId: Long,
    mangaId: Long?,
): ReaderOpenContext {
    val effectiveMangaId = mangaId ?: runtime.library.chapterAsset(chapterId)?.mangaId
    val override = effectiveMangaId?.let { runtime.library.mangaSnapshot(it)?.readerSettingsOverride }
    return ReaderOpenContext(mangaId = effectiveMangaId, override = override, ready = true)
}

@Composable
private fun rememberReaderHandle(
    runtime: DesktopRuntime,
    chapterId: Long,
    mangaId: Long?,
    override: MangaReaderSettingsOverride?,
): DesktopReaderHandle? {
    // Chapter changes stay on this handle; only manga or reader override changes rebuild it.
    var readerHandle by remember(mangaId, override) { mutableStateOf<DesktopReaderHandle?>(null) }
    DisposableEffect(readerHandle) {
        val handle = readerHandle
        onDispose { handle?.session?.cancelWithoutFlush() }
    }
    LaunchedEffect(mangaId, override) {
        if (readerHandle != null) return@LaunchedEffect
        val factory = runtime.readerFactory ?: return@LaunchedEffect
        val handle = withContext(Dispatchers.Default) {
            factory.createHandle(
                isIncognito = runtime.preferences.load().incognitoMode,
                readerSettingsOverride = override,
            )
        }
        readerHandle = handle
    }
    return readerHandle
}

@Composable
private fun ReaderOpening(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}

@Composable
private fun OpenReaderChapter(handle: DesktopReaderHandle?, chapterId: Long, atEnd: Boolean) {
    LaunchedEffect(handle, chapterId, atEnd) {
        if (handle == null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val current = handle.session.state.value
            val alreadyOpen = current.chapterId == chapterId && current.loadState is ReaderLoadState.Ready &&
                (!atEnd || current.selectedIndex == current.pages.lastIndex)
            if (!alreadyOpen) handle.session.open(chapterId, atEnd)
        }
    }
}
