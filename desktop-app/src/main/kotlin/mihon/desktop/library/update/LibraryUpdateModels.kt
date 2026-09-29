package mihon.desktop.library.update

import mihon.desktop.library.model.LibraryChapter

data class LibraryUpdateOptions(
    val skipCompleted: Boolean = true,
    val skipUnread: Boolean = false,
    val skipNotStarted: Boolean = false,
    val skipOutsideReleasePeriod: Boolean = false,
    val includedCategoryIds: Set<Long>? = null,
    val excludedCategoryIds: Set<Long>? = null,
    val autoDownloadNewChapters: Boolean = false,
    val autoDownloadUnreadOnly: Boolean = false,
    val autoDownloadCategories: Set<Long> = emptySet(),
    val autoDownloadCategoriesExclude: Set<Long> = emptySet(),
    val mangaIds: Set<Long>? = null,
)

data class MangaUpdateItemResult(
    val mangaId: Long,
    val title: String,
    val newChapters: List<LibraryChapter>,
    val error: String? = null,
)

data class LibraryUpdateReport(
    val totalMangaChecked: Int,
    val updatedMangaCount: Int,
    val newChaptersTotal: Int,
    val results: List<MangaUpdateItemResult>,
    val errors: List<String>,
)

data class LibraryUpdateProgress(
    val currentMangaTitle: String,
    val currentIndex: Int,
    val totalManga: Int,
    val currentSourceId: Long? = null,
)
