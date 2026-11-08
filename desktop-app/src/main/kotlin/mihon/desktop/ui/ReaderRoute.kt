package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.navigation.DesktopRoute
import mihon.desktop.reader.MangaCacheRetention
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.mangaCacheRetention
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.library.chapterDisplayLabel
import mihon.desktop.ui.reader.LibraryChapterBookmarkStore
import mihon.desktop.ui.reader.ReaderChapterTransitionChapter
import mihon.desktop.ui.reader.readerNavigationChapters

/** Previous / next readable chapters around the open one, plus the catalog the reader shows. */
internal data class ReaderChapterNavigation(
    val catalog: List<ReaderChapterTransitionChapter>,
    val previous: LibraryChapter?,
    val next: LibraryChapter?,
) {
    fun entry(chapter: LibraryChapter?) = chapter?.let { target -> catalog.firstOrNull { it.id == target.id } }
}

internal fun readerChapterNavigation(
    detail: MangaDetailUiState,
    currentChapterId: Long,
    strings: DesktopStrings,
): ReaderChapterNavigation {
    val filtered = detail.readerChapters.ifEmpty { detail.allChapters }
    val navigationChapters = readerNavigationChapters(filtered, detail.allChapters, currentChapterId)
    val readable = readableChapters(detail, navigationChapters)
    val index = readable.indexOfFirst { it.id == currentChapterId }
    val catalog = navigationChapters.map { chapter ->
        ReaderChapterTransitionChapter(
            id = chapter.id,
            title = chapterDisplayLabel(chapter, detail.chapterSettings.displayMode, strings),
            chapterNumber = chapter.chapterNumber,
            scanlator = chapter.scanlator,
            downloaded = detail.downloadedChapterIds.contains(chapter.id),
            available = detail.readerAvailability.isEmpty() ||
                detail.readerAvailability[chapter.id] == ChapterReaderAvailability.Readable,
            read = chapter.read,
        )
    }
    return ReaderChapterNavigation(
        catalog = catalog,
        previous = readable.getOrNull(index - 1).takeIf { index > 0 },
        next = readable.getOrNull(index + 1).takeIf { index >= 0 },
    )
}

private fun readableChapters(detail: MangaDetailUiState, chapters: List<LibraryChapter>): List<LibraryChapter> {
    val available = if (detail.readerAvailability.isEmpty()) {
        chapters
    } else {
        chapters.filter { detail.readerAvailability[it.id] == ChapterReaderAvailability.Readable }
    }
    return if (detail.readerChapters.isNotEmpty()) {
        available
    } else {
        available.sortedWith(compareBy<LibraryChapter> { it.chapterNumber }.thenBy { it.sourceOrder })
    }
}

/** Keeps the online page cache of the manga being read and drops it once the user moves on. */
@Composable
internal fun OnlineMangaCacheRetention(
    destination: DesktopRoute,
    detail: MangaDetailUiState,
    runtime: DesktopRuntime,
) {
    val readerMangaId = (destination as? DesktopDestination.Reader)?.let { reader ->
        detail.allChapters.firstOrNull { it.id == reader.chapterId }?.mangaId
            ?: runtime.readerFactory?.mangaIdForChapter(reader.chapterId)
    }
    var heldOnlineMangaId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(destination, readerMangaId, detail.manga?.id) {
        val decision = mangaCacheRetention(destination, detail.manga?.id, readerMangaId)
        val next = when (decision) {
            MangaCacheRetention.Keep -> return@LaunchedEffect
            MangaCacheRetention.Clear -> null
            is MangaCacheRetention.Switch -> decision.mangaId.takeIf { it != heldOnlineMangaId }
                ?: return@LaunchedEffect
        }
        val previous = heldOnlineMangaId
        heldOnlineMangaId = next
        previous?.let { runtime.readerFactory?.mangaOnlineCache?.clear(it) }
    }
}

@Composable
internal fun ReaderRoute(
    destination: DesktopDestination.Reader,
    runtime: DesktopRuntime,
    detail: MangaDetailUiState,
    libraryPresenter: LibraryPresenter,
    navigator: DesktopNavigator,
    scope: CoroutineScope,
    windowController: MainWindowController,
) {
    val strings = LocalStrings.current
    // Paging back should enter on the last page. Remember dies with the reader,
    // so a later open from the library still resumes the saved page.
    var openChapterAtEnd by remember { mutableStateOf(false) }
    val chapterId = destination.chapterId
    val navigation = remember(detail, chapterId, strings) { readerChapterNavigation(detail, chapterId, strings) }
    val bookmarkStore = rememberBookmarkStore(runtime, detail.manga?.id)
    val chapter = detail.allChapters.firstOrNull { it.id == chapterId }
    val readerManga = detail.manga?.takeIf { manga -> chapter?.mangaId == manga.id }

    fun leaveReader(afterClose: () -> Unit) {
        if (chapter != null && readerManga != null) {
            scope.launch { onChapterLeft(runtime, readerManga, chapter, detail.chapters) }
        }
        windowController.transitionReaderWindow(ReaderWindowMode.NORMAL)
        afterClose()
    }
    fun openChapter(target: LibraryChapter?, atEnd: Boolean) {
        target ?: return
        openChapterAtEnd = atEnd
        navigator.navigate(DesktopDestination.Reader(target.id))
    }

    DesktopBackHandler {
        if (windowController.handleReaderEscape()) leaveReader { navigator.pop() }
        true
    }
    ReaderDestination(
        destination = destination,
        runtime = runtime,
        mangaTitle = detail.manga?.title ?: "Reader",
        chapterTitle = chapter?.let { chapterDisplayLabel(it, detail.chapterSettings.displayMode, strings) }
            ?: strings.text(UiText.ChapterNumberLabel, chapterId),
        mangaId = readerManga?.id,
        chapterCatalog = navigation.catalog,
        previousChapter = navigation.entry(navigation.previous),
        nextChapter = navigation.entry(navigation.next),
        currentChapter = navigation.catalog.firstOrNull { it.id == chapterId },
        currentChapterDownloaded = detail.downloadedChapterIds.contains(chapterId),
        bookmarkStore = bookmarkStore,
        onBack = { leaveReader { navigator.back() } },
        onOpenMangaDetails = {
            readerManga?.let { manga ->
                leaveReader {
                    libraryPresenter.openMangaDetail(manga.id)
                    navigator.navigate(DesktopDestination.MangaDetails(manga.id))
                }
            }
        },
        onPreviousChapter = { openChapter(navigation.previous, atEnd = true) },
        onNextChapter = { openChapter(navigation.next, atEnd = false) },
        onChapterSelected = { selectedId ->
            openChapterAtEnd = false
            navigator.navigate(DesktopDestination.Reader(selectedId))
        },
        landAtEnd = openChapterAtEnd,
        hasPreviousChapter = navigation.previous != null,
        hasNextChapter = navigation.next != null,
        onFullscreen = windowController::toggleReaderFullscreen,
        onBorderless = windowController::toggleReaderBorderless,
        onEscape = windowController::handleReaderEscape,
        foreground = !windowController.windowState.isMinimized,
    )
}

@Composable
private fun rememberBookmarkStore(runtime: DesktopRuntime, mangaId: Long?): ReaderChapterBookmarkStore? =
    remember(runtime.library, mangaId) {
        mangaId?.let { id ->
            LibraryChapterBookmarkStore(
                findChapter = { chapterId -> runtime.library.chapterSnapshot(id).firstOrNull { it.id == chapterId } },
                mutationPort = runtime.library,
            )
        }
    }

/** Tracking, sync, download-ahead and auto-delete once the reader closes a chapter. */
private suspend fun onChapterLeft(
    runtime: DesktopRuntime,
    manga: mihon.desktop.library.model.MangaDetails,
    chapter: LibraryChapter,
    chapters: List<LibraryChapter>,
) {
    val prefs = runtime.preferences.load()
    if (!prefs.incognitoMode) {
        runtime.trackSyncService?.onChapterRead(manga.id, chapter.chapterNumber)
    }
    runtime.syncScheduler?.googleDrive?.requestSync()
    if (prefs.downloadAhead > 0) {
        runtime.downloader?.checkAndDownloadAhead(
            manga = manga,
            currentChapter = chapter,
            allChapters = chapters,
            count = prefs.downloadAhead,
        )
    }
    if (prefs.deleteDownloadedRead && !prefs.incognitoMode) {
        runtime.downloader?.deleteDownloadedChapter(manga, chapter)
    }
}
