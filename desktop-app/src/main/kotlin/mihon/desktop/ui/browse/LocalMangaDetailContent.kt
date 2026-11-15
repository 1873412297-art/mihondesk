package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import mihon.desktop.DesktopRuntime
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.ui.library.ChapterBrowseState
import mihon.desktop.ui.library.ChapterFilterState
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.ChapterSortMode
import mihon.desktop.ui.library.ChapterSortState
import mihon.desktop.ui.library.MangaDetailScreen
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.library.TriStateFilter
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import java.nio.file.Files

internal data class LocalMangaDetailState(
    val mangaId: Long? = null,
    val manga: MangaDetails? = null,
    val chapters: List<LibraryChapter> = emptyList(),
    val readerAvailability: Map<Long, ChapterReaderAvailability> = emptyMap(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

@Composable
internal fun LocalMangaDetailContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    manga: SManga,
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(source.id, manga.url) {
        LocalMangaDetailPresenter(
            sourceId = source.id,
            mangaUrl = manga.url,
            repository = runtime.library,
            mutations = runtime.library,
            readerLibrary = runtime.library,
            scope = scope,
        )
    }
    DisposableEffect(presenter) {
        presenter.load()
        onDispose { presenter.close() }
    }
    val state by presenter.state.collectAsState()
    val actions = remember(presenter, onReadChapter) { presenter.actions(onReadChapter) }
    MangaDetailScreen(state = state, onBack = onBack, actions = actions, onRetry = presenter::load)
}

/** Finds the library entry behind a local-source manga and loads what its detail page shows. */
internal fun loadLocalMangaDetail(
    sourceId: Long,
    mangaUrl: String,
    repository: LibraryRepository,
    mutations: LibraryMutationPort,
    readerLibrary: ReaderLibraryPort,
): LocalMangaDetailState {
    val mangaId = repository.librarySnapshot(null)
        .firstOrNull { it.sourceId == sourceId && it.url == mangaUrl }?.id
        ?: repository.allMangaSnapshot().firstOrNull { it.sourceId == sourceId && it.url == mangaUrl }?.id
        ?: error("Local manga is no longer in the library")
    val storagePath = mangaUrl.substringAfter("local:", missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { mutations.findLocalMangaByManifest(it)?.storagePath }
    val details = repository.mangaSnapshot(mangaId)?.let { value ->
        if (value.thumbnailUrl.isNullOrBlank() && storagePath != null) value.copy(thumbnailUrl = storagePath) else value
    }
    val chapters = repository.chapterSnapshot(mangaId)
    return LocalMangaDetailState(
        mangaId = mangaId,
        manga = details,
        chapters = chapters,
        readerAvailability = chapters.associate { it.id to it.resolveReaderAvailability(readerLibrary) },
        loading = false,
    )
}

internal fun LocalMangaDetailState.toUiState(
    filter: ChapterFilterState = ChapterFilterState(),
    sort: ChapterSortState = ChapterSortState(),
    browse: ChapterBrowseState = ChapterBrowseState(),
): MangaDetailUiState = MangaDetailUiState(
    manga = manga,
    chapters = chapters.filter { it.matches(filter) }.sortedFor(sort),
    allChapters = chapters,
    loading = loading,
    errorMessage = errorMessage,
    readerAvailability = readerAvailability,
    chapterFilterState = filter,
    chapterSortState = sort,
    chapterBrowse = browse.forManga(manga?.id),
)

private fun LibraryChapter.matches(filter: ChapterFilterState): Boolean {
    val unreadMatch = when (filter.unread) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> !read
        TriStateFilter.Exclude -> read
    }
    val bookmarkMatch = when (filter.bookmarked) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> bookmark
        TriStateFilter.Exclude -> !bookmark
    }
    return unreadMatch && bookmarkMatch
}

private fun List<LibraryChapter>.sortedFor(sort: ChapterSortState): List<LibraryChapter> {
    val key: (LibraryChapter) -> Comparable<*> = when (sort.mode) {
        ChapterSortMode.SourceOrder -> { chapter -> chapter.sourceOrder }
        ChapterSortMode.ChapterNumber -> { chapter -> chapter.chapterNumber }
        ChapterSortMode.UploadDate -> { chapter -> chapter.dateUpload }
    }
    val comparator = compareBy(key)
    // Stable in both directions, matching sortedBy / sortedByDescending.
    return sortedWith(if (sort.ascending) comparator else comparator.reversed())
}

private fun LibraryChapter.resolveReaderAvailability(
    readerLibrary: ReaderLibraryPort,
): ChapterReaderAvailability {
    val asset = readerLibrary.chapterAsset(id)
    if (asset != null) {
        val path = asset.storageRoot.resolve(asset.relativePath)
        val readable = if (asset.assetKind == "DIRECTORY") {
            Files.isDirectory(path)
        } else {
            Files.isRegularFile(path)
        }
        return if (readable) ChapterReaderAvailability.Readable else ChapterReaderAvailability.MissingLocalContent
    }
    return if (readerLibrary.onlineChapter(id) != null) {
        ChapterReaderAvailability.Readable
    } else {
        ChapterReaderAvailability.RemoteOnly
    }
}
