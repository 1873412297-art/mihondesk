package mihon.desktop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.reader.ReaderLibraryPort
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
fun LocalMangaDetailContent(
    runtime: DesktopRuntime,
    source: SourceDescriptor,
    manga: SManga,
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember(manga.url) { mutableStateOf(0) }
    var detailState by remember(manga.url) { mutableStateOf(LocalMangaDetailState()) }
    var filterState by remember(manga.url) { mutableStateOf(ChapterFilterState()) }
    var sortState by remember(manga.url) { mutableStateOf(ChapterSortState()) }

    LaunchedEffect(manga.url, refreshKey) {
        detailState = detailState.copy(loading = true, errorMessage = null)
        try {
            val localMangaId = withContext(Dispatchers.IO) {
                runtime.library.librarySnapshot(null)
                    .firstOrNull { it.sourceId == source.id && it.url == manga.url }
                    ?.id
                    ?: runtime.library.allMangaSnapshot()
                        .firstOrNull { it.sourceId == source.id && it.url == manga.url }
                        ?.id
            } ?: error("Local manga is no longer in the library")

            val details = withContext(Dispatchers.IO) { runtime.library.mangaSnapshot(localMangaId) }
            val localStoragePath = withContext(Dispatchers.IO) {
                manga.url.substringAfter("local:", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                    ?.let { runtime.library.findLocalMangaByManifest(it)?.storagePath }
            }
            val detailsWithCover = details?.let { value ->
                if (value.thumbnailUrl.isNullOrBlank() && localStoragePath != null) {
                    value.copy(thumbnailUrl = localStoragePath)
                } else {
                    value
                }
            }
            val chapters = withContext(Dispatchers.IO) { runtime.library.chapterSnapshot(localMangaId) }
            val availability = chapters.associate { chapter ->
                chapter.id to chapter.resolveReaderAvailability(runtime.library)
            }
            detailState = LocalMangaDetailState(
                mangaId = localMangaId,
                manga = detailsWithCover,
                chapters = chapters,
                readerAvailability = availability,
                loading = false,
            )
        } catch (e: Exception) {
            detailState = LocalMangaDetailState(
                loading = false,
                errorMessage = e.message ?: "Failed to load local manga",
            )
        }
    }

    val filteredChapters = remember(detailState.chapters, filterState) {
        detailState.chapters.filter { chapter ->
            val unreadMatch = when (filterState.unread) {
                TriStateFilter.Disabled -> true
                TriStateFilter.Include -> !chapter.read
                TriStateFilter.Exclude -> chapter.read
            }
            val bookmarkMatch = when (filterState.bookmarked) {
                TriStateFilter.Disabled -> true
                TriStateFilter.Include -> chapter.bookmark
                TriStateFilter.Exclude -> !chapter.bookmark
            }
            unreadMatch && bookmarkMatch
        }
    }
    val displayedChapters = remember(filteredChapters, sortState) {
        when (sortState.mode) {
            ChapterSortMode.SourceOrder -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.sourceOrder }
            } else {
                filteredChapters.sortedByDescending { it.sourceOrder }
            }
            ChapterSortMode.ChapterNumber -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.chapterNumber }
            } else {
                filteredChapters.sortedByDescending { it.chapterNumber }
            }
            ChapterSortMode.UploadDate -> if (sortState.ascending) {
                filteredChapters.sortedBy { it.dateUpload }
            } else {
                filteredChapters.sortedByDescending { it.dateUpload }
            }
        }
    }

    MangaDetailScreen(
        state = MangaDetailUiState(
            manga = detailState.manga,
            chapters = displayedChapters,
            allChapters = detailState.chapters,
            loading = detailState.loading,
            errorMessage = detailState.errorMessage,
            readerAvailability = detailState.readerAvailability,
            chapterFilterState = filterState,
            chapterSortState = sortState,
        ),
        onBack = onBack,
        onReadChapter = onReadChapter,
        onRetry = { refreshKey++ },
        onChapterFilterChange = { filterState = it },
        onChapterSortChange = { sortState = it },
        onToggleBookmark = { chapterId ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runtime.library.allChaptersSnapshot()
                        .firstOrNull { it.id == chapterId }
                        ?.let { record -> runtime.library.updateChapter(record.copy(bookmark = !record.bookmark)) }
                }
                refreshKey++
            }
        },
        onToggleRead = { chapterId ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runtime.library.allChaptersSnapshot()
                        .firstOrNull { it.id == chapterId }
                        ?.let { record -> runtime.library.updateChapter(record.copy(read = !record.read)) }
                }
                refreshKey++
            }
        },
        onMarkPreviousRead = { chapterId ->
            detailState.mangaId?.let { mangaId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val records = runtime.library.allChaptersSnapshot()
                            .filter { it.mangaId == mangaId }
                            .sortedBy { it.sourceOrder }
                        val targetIndex = records.indexOfFirst { it.id == chapterId }
                        if (targetIndex >= 0) {
                            records.take(targetIndex + 1).forEach { record ->
                                if (!record.read) {
                                    runtime.library.updateChapter(record.copy(read = true))
                                }
                            }
                        }
                    }
                    refreshKey++
                }
            }
        },
    )
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
