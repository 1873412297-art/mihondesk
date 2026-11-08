package mihon.desktop.ui

import mihon.desktop.ui.library.LibraryDisplayMode
import mihon.desktop.ui.library.LibraryFilterState
import mihon.desktop.ui.library.LibrarySortState

internal class LibraryActions(
    val query: (String) -> Unit = {},
    val selectManga: (Long) -> Unit = {},
    val retryDetail: () -> Unit = {},
    val retry: () -> Unit = {},
    val category: (Long) -> Unit = {},
    val manageCategories: () -> Unit = {},
    val editCategories: () -> Unit = {},
    val tracking: () -> Unit = {},
    val displayMode: (LibraryDisplayMode) -> Unit = {},
    val gridSize: (Float) -> Unit = {},
    val unreadBadge: (Boolean) -> Unit = {},
    val downloadedBadge: (Boolean) -> Unit = {},
    val openFilter: () -> Unit = {},
    val closeFilter: () -> Unit = {},
    val filter: (LibraryFilterState) -> Unit = {},
    val sort: (LibrarySortState) -> Unit = {},
    val selectionMode: (Boolean) -> Unit = {},
    val selectMangaToggle: (Long) -> Unit = {},
    val selectAll: () -> Unit = {},
    val deselectAll: () -> Unit = {},
    val batchChangeCategories: () -> Unit = {},
    val batchSetCategories: (List<Long>) -> Unit = {},
    val batchCloseCategories: () -> Unit = {},
    val batchRead: (Boolean) -> Unit = {},
    val batchDownload: (Int) -> Unit = {},
    val batchRemove: () -> Unit = {},
    val paneWidth: (Int) -> Unit = {},
)

internal fun libraryActions(ctx: AppShellContext, live: ShellLiveState): LibraryActions {
    val presenter = ctx.libraryPresenter
    return LibraryActions(
        query = presenter::setQuery,
        selectManga = presenter::selectManga,
        retryDetail = presenter::retryDetail,
        retry = presenter::retry,
        category = presenter::selectCategory,
        manageCategories = { ctx.session.isManageCategoriesDialogOpen = true },
        editCategories = { ctx.session.isEditMangaCategoriesDialogOpen = true },
        tracking = { ctx.session.isTrackingDialogOpen = true },
        displayMode = presenter::setDisplayMode,
        gridSize = presenter::setGridSize,
        unreadBadge = presenter::setUnreadBadgeVisible,
        downloadedBadge = presenter::setDownloadedBadgeVisible,
        openFilter = { presenter.setFilterDialogOpen(true) },
        closeFilter = { presenter.setFilterDialogOpen(false) },
        filter = presenter::setFilterState,
        sort = presenter::setSortState,
        selectionMode = presenter::toggleSelectionMode,
        selectMangaToggle = presenter::toggleMangaSelection,
        selectAll = presenter::selectAll,
        deselectAll = presenter::clearSelection,
        batchChangeCategories = { presenter.setBatchCategoryDialogOpen(true) },
        batchSetCategories = presenter::batchSetCategories,
        batchCloseCategories = { presenter.setBatchCategoryDialogOpen(false) },
        batchRead = presenter::batchMarkRead,
        batchDownload = presenter::batchDownload,
        batchRemove = removeLibrarySelectionAction(ctx, live),
        paneWidth = {
            ctx.session.preferences =
                ctx.runtime.preferences.updatePreferences { p -> p.copy(libraryDetailPaneWidth = it) }
        },
    )
}
