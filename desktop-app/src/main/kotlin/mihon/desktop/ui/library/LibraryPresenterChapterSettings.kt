package mihon.desktop.ui.library

import mihon.desktop.library.model.MangaReaderSettingsOverride

// Chapter list settings of the open manga detail, delegated to its ChapterSettingsCoordinator.

fun LibraryPresenter.setChapterFilter(filter: ChapterFilterState) {
    chapterSettings.updateCurrent { current ->
        current.copy(
            unreadFilter = filter.unread,
            downloadedFilter = filter.downloaded,
            bookmarkedFilter = filter.bookmarked,
        )
    }
}

fun LibraryPresenter.setChapterSort(sort: ChapterSortState) {
    chapterSettings.updateCurrent { current ->
        current.copy(sortMode = sort.mode, sortAscending = sort.ascending)
    }
}

fun LibraryPresenter.setChapterDisplayMode(mode: ChapterDisplayMode) {
    chapterSettings.updateCurrent { it.copy(displayMode = mode) }
}

fun LibraryPresenter.setExcludedScanlators(excludedScanlators: Set<String>) {
    chapterSettings.updateCurrent { it.copy(excludedScanlators = excludedScanlators) }
}

fun LibraryPresenter.setShowMissingChapters(show: Boolean) {
    chapterSettings.updateCurrent { it.copy(showMissingChapters = show) }
}

fun LibraryPresenter.saveReadingSettingsOverride(override: MangaReaderSettingsOverride) = chapterSettings.saveOverride(
    override,
)

fun LibraryPresenter.clearReadingSettingsOverride() = chapterSettings.clearOverride()

fun LibraryPresenter.setChapterSettingsAsDefault(applyToExisting: Boolean) = chapterSettings.setAsDefault(
    applyToExisting,
)

fun LibraryPresenter.resetChapterSettingsToDefault() = chapterSettings.resetToDefault()
