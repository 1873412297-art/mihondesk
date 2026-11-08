package mihon.desktop.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import mihon.desktop.preferences.DesktopPreferenceStore

/** Library display, filtering and sorting preferences, persisted on every change. */
internal class LibraryDisplayPreferences(private val preferences: DesktopPreferenceStore?) {

    // Phase 11: Display mode, grid size, filtering, sorting, selection flows
    private val initialPrefs = preferences?.load()

    val displayMode = MutableStateFlow(
        initialPrefs?.libraryDisplayMode?.let { runCatching { LibraryDisplayMode.valueOf(it) }.getOrNull() }
            ?: LibraryDisplayMode.ComfortableGrid,
    )

    val gridSize = MutableStateFlow(
        initialPrefs?.libraryGridSize ?: 180f,
    )

    val showUnreadBadge = MutableStateFlow(initialPrefs?.libraryBadgeUnread ?: true)

    val showDownloadedBadge = MutableStateFlow(initialPrefs?.libraryBadgeDownloaded ?: true)

    val filterState = MutableStateFlow(
        LibraryFilterState(
            unread = parseTriState(initialPrefs?.libraryFilterUnread),
            downloaded = parseTriState(initialPrefs?.libraryFilterDownloaded),
            started = parseTriState(initialPrefs?.libraryFilterStarted),
            completed = parseTriState(initialPrefs?.libraryFilterCompleted),
            bookmarked = parseTriState(initialPrefs?.libraryFilterBookmarked),
        ),
    )

    val sortState = MutableStateFlow(
        LibrarySortState(
            mode = initialPrefs?.librarySortMode?.let { runCatching { LibrarySortMode.valueOf(it) }.getOrNull() }
                ?: LibrarySortMode.None,
            ascending = initialPrefs?.librarySortAscending ?: true,
        ),
    )

    fun setDisplayMode(mode: LibraryDisplayMode) {
        displayMode.value = mode
        preferences?.update {
            setProperty("library.display_mode", mode.name)
        }
    }

    fun setGridSize(size: Float) {
        gridSize.value = size
        preferences?.update {
            setProperty("library.grid_size", size.toString())
        }
    }

    fun setUnreadBadgeVisible(visible: Boolean) {
        showUnreadBadge.value = visible
        preferences?.update {
            setProperty("library.badge_unread", visible.toString())
        }
    }

    fun setDownloadedBadgeVisible(visible: Boolean) {
        showDownloadedBadge.value = visible
        preferences?.update {
            setProperty("library.badge_downloaded", visible.toString())
        }
    }

    fun setFilterState(filters: LibraryFilterState) {
        filterState.value = filters
        preferences?.update {
            setProperty("library.filter_unread", filters.unread.name)
            setProperty("library.filter_downloaded", filters.downloaded.name)
            setProperty("library.filter_started", filters.started.name)
            setProperty("library.filter_completed", filters.completed.name)
            setProperty("library.filter_bookmarked", filters.bookmarked.name)
        }
    }

    fun setSortState(sort: LibrarySortState) {
        sortState.value = sort
        preferences?.update {
            setProperty("library.sort_mode", sort.mode.name)
            setProperty("library.sort_ascending", sort.ascending.toString())
        }
    }
}
