package mihon.desktop.ui.library

import mihon.desktop.category.DesktopCategory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.reader.source.ReaderChapterAsset
import java.nio.file.Files

internal fun parseTriState(value: String?): TriStateFilter {
    return value?.let { runCatching { TriStateFilter.valueOf(it) }.getOrNull() } ?: TriStateFilter.Disabled
}

internal fun matchesFilters(manga: LibraryManga, filters: LibraryFilterState): Boolean {
    // 1. Unread
    when (filters.unread) {
        TriStateFilter.Include -> if (manga.unreadCount <= 0) return false
        TriStateFilter.Exclude -> if (manga.unreadCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 2. Started (some read, some unread)
    when (filters.started) {
        TriStateFilter.Include -> if (!(manga.chapterCount > manga.unreadCount && manga.unreadCount > 0)) return false
        TriStateFilter.Exclude -> if (manga.chapterCount > manga.unreadCount && manga.unreadCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 3. Completed (has chapters and 0 unread)
    when (filters.completed) {
        TriStateFilter.Include -> if (!(manga.unreadCount == 0L && manga.chapterCount > 0)) return false
        TriStateFilter.Exclude -> if (manga.unreadCount == 0L && manga.chapterCount > 0) return false
        TriStateFilter.Disabled -> Unit
    }
    // 4. Downloaded
    when (filters.downloaded) {
        TriStateFilter.Include -> if (manga.downloadedCount == 0L) return false
        TriStateFilter.Exclude -> if (manga.downloadedCount > 0L) return false
        TriStateFilter.Disabled -> Unit
    }
    // 5. Bookmarked
    when (filters.bookmarked) {
        TriStateFilter.Include -> if (manga.bookmarkCount == 0L) return false
        TriStateFilter.Exclude -> if (manga.bookmarkCount > 0L) return false
        TriStateFilter.Disabled -> Unit
    }
    return true
}

internal fun sortManga(items: List<LibraryManga>, sort: LibrarySortState): List<LibraryManga> {
    if (sort.mode == LibrarySortMode.None) return items
    val comparator: Comparator<LibraryManga> = when (sort.mode) {
        LibrarySortMode.None -> return items
        LibrarySortMode.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySortMode.UnreadCount -> compareBy { it.unreadCount }
        LibrarySortMode.TotalChapters -> compareBy { it.chapterCount }
        LibrarySortMode.DateAdded -> compareBy<LibraryManga> { it.dateAdded }.thenBy { it.id }
        LibrarySortMode.LastRead -> compareBy<LibraryManga> { it.lastRead }.thenBy { it.id }
        LibrarySortMode.LastUpdate -> compareBy<LibraryManga> { it.lastUpdate }.thenBy { it.id }
    }

    return if (sort.ascending) {
        items.sortedWith(comparator)
    } else {
        items.sortedWith(comparator.reversed())
    }
}

internal data class ProcessedLibraryData(
    val repoResult: RepositoryState,
    val query: String,
    val categories: List<DesktopCategory>,
    val selectedCategoryId: Long,
    val filters: LibraryFilterState,
    val sort: LibrarySortState,
)

internal data class LayoutAndSelectionData(
    val selectedMangaId: Long?,
    val displayMode: LibraryDisplayMode,
    val gridSize: Float,
    val showUnreadBadge: Boolean,
    val showDownloadedBadge: Boolean,
    val selectionState: LibrarySelectionState,
    val isFilterDialogOpen: Boolean,
    val duplicateDialog: DuplicateMangaDialogState?,
)

internal data class ChapterSettingsOverrides(
    val mangaId: Long? = null,
    val settings: ChapterSettings? = null,
)

internal fun matchesChapterFilter(
    chapter: LibraryChapter,
    filter: ChapterFilterState,
    downloadedChapterIds: Set<Long>,
): Boolean {
    val unreadMatch = when (filter.unread) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> !chapter.read
        TriStateFilter.Exclude -> chapter.read
    }
    val bookmarkMatch = when (filter.bookmarked) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> chapter.bookmark
        TriStateFilter.Exclude -> !chapter.bookmark
    }
    val downloadedMatch = when (filter.downloaded) {
        TriStateFilter.Disabled -> true
        TriStateFilter.Include -> chapter.id in downloadedChapterIds
        TriStateFilter.Exclude -> chapter.id !in downloadedChapterIds
    }
    return unreadMatch && bookmarkMatch && downloadedMatch
}

internal fun sortChapters(
    chapters: List<LibraryChapter>,
    settings: ChapterSettings,
): List<LibraryChapter> {
    val comparator: Comparator<LibraryChapter> = when (settings.sortMode) {
        ChapterSortMode.SourceOrder -> compareBy { it.sourceOrder }
        ChapterSortMode.ChapterNumber -> compareBy { it.chapterNumber }
        ChapterSortMode.UploadDate -> compareBy { it.dateUpload }
    }
    return if (settings.sortAscending) {
        chapters.sortedWith(comparator)
    } else {
        chapters.sortedWith(comparator.reversed())
    }
}

internal sealed interface RepositoryState {
    data object Loading : RepositoryState
    data class Loaded(val items: List<LibraryManga>) : RepositoryState
    data class Failed(val message: String) : RepositoryState
}

internal sealed interface SelectedRepositoryState {
    data object Loading : SelectedRepositoryState
    data class Loaded(
        val selectedId: Long?,
        val manga: MangaDetails?,
        val chapters: List<LibraryChapter>,
        val readerAvailability: Map<Long, ChapterReaderAvailability>,
    ) : SelectedRepositoryState
    data class Failed(val message: String) : SelectedRepositoryState
}

internal fun LibraryChapter.readerAvailability(readerLibrary: ReaderLibraryPort?): ChapterReaderAvailability {
    val localAsset = readerLibrary?.chapterAsset(id)
    if (localAsset != null && localAsset.hasReadableContent()) return ChapterReaderAvailability.Readable
    if (readerLibrary?.onlineChapter(id) != null) return ChapterReaderAvailability.Readable
    return if (localAsset != null) {
        ChapterReaderAvailability.MissingLocalContent
    } else {
        ChapterReaderAvailability.RemoteOnly
    }
}

internal fun ReaderChapterAsset.hasReadableContent(): Boolean {
    val path = storageRoot.resolve(relativePath)
    return if (assetKind == "DIRECTORY") {
        Files.isDirectory(path)
    } else {
        Files.isRegularFile(path)
    }
}

internal fun LibraryChapter.toChapterRecord(): ChapterRecord = ChapterRecord(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    dateFetch = dateFetch,
    dateUpload = dateUpload,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    memoJson = memoJson,
)

internal fun MangaDetails.toMangaRecord(): MangaRecord = MangaRecord(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = genreJson,
    status = status,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    updateStrategy = updateStrategy,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    excludedScanlatorsJson = excludedScanlatorsJson,
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memoJson,
)
