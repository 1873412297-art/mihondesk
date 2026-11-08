package mihon.desktop.ui.library

import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaReaderSettingsOverride

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<LibraryManga> = emptyList(),
    val selectedMangaId: Long? = null,
    val errorMessage: String? = null,
    val categories: List<DesktopCategory> = listOf(SYSTEM_ALL_CATEGORY),
    val selectedCategoryId: Long = SYSTEM_ALL_CATEGORY.id,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
    val gridSize: Float = 180f,
    val showUnreadBadge: Boolean = true,
    val showDownloadedBadge: Boolean = true,
    val filterState: LibraryFilterState = LibraryFilterState(),
    val sortState: LibrarySortState = LibrarySortState(),
    val selectionState: LibrarySelectionState = LibrarySelectionState(),
    val isFilterDialogOpen: Boolean = false,
    val isBatchCategoryDialogOpen: Boolean = false,
    val duplicateDialog: DuplicateMangaDialogState? = null,
) {
    val duplicateTarget: DuplicateMangaCandidate? get() = duplicateDialog?.target
    val duplicateCandidates: List<DuplicateMangaCandidate> get() = duplicateDialog?.candidates.orEmpty()
}

data class LibraryBatchState(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val error: String? = null,
)

data class MangaDetailUiState(
    val manga: MangaDetails? = null,
    val chapters: List<LibraryChapter> = emptyList(),
    val allChapters: List<LibraryChapter> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val readerAvailability: Map<Long, ChapterReaderAvailability> = emptyMap(),
    val chapterFilterState: ChapterFilterState = ChapterFilterState(),
    val chapterSortState: ChapterSortState = ChapterSortState(),
    val isEditInfoDialogOpen: Boolean = false,
    val downloadedChapterIds: Set<Long> = emptySet(),
    val readerChapters: List<LibraryChapter> = emptyList(),
    val chapterSettings: ChapterSettings = ChapterSettings(),
    val availableScanlators: Set<String> = emptySet(),
    val chapterListItems: List<ChapterListItem> = emptyList(),
    val isChapterSettingsDialogOpen: Boolean = false,
    val isReadingSettingsDialogOpen: Boolean = false,
    val readingSettingsOverride: MangaReaderSettingsOverride? = null,
    val downloadProgressOf: (Long) -> ChapterDownloadProgress? = NoChapterDownloadProgress,
    val downloadsRunning: Boolean = false,
    val missingSource: MissingSourceInfo? = null,
)

sealed interface ChapterReaderAvailability {
    data object Readable : ChapterReaderAvailability
    data object MissingLocalContent : ChapterReaderAvailability
    data object RemoteOnly : ChapterReaderAvailability
}
