package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.ui.upcoming.UpcomingScreen

internal fun upcomingContent(
    ctx: AppShellContext,
    presenter: UpcomingPresenter,
): (@Composable () -> Unit)? = if (ctx.navigator.current == DesktopDestination.Upcoming) {
    {
        val state by presenter.state.collectAsState()
        UpcomingScreen(
            state = state,
            onBack = { ctx.navigator.back() },
            onPreviousMonth = presenter::previousMonth,
            onNextMonth = presenter::nextMonth,
            onSelectDate = presenter::selectDate,
            onOpenFilter = { presenter.setFilterDialogOpen(true) },
            onDismissFilter = { presenter.setFilterDialogOpen(false) },
            onCycleCategory = presenter::cycleCategory,
            onClearFilters = presenter::clearFilters,
            onOpenManga = {
                ctx.navigator.navigate(DesktopDestination.Library)
                ctx.libraryPresenter.selectManga(it)
            },
            onRetry = presenter::retry,
        )
    }
} else {
    null
}

internal fun browseContent(ctx: AppShellContext, live: ShellLiveState): @Composable () -> Unit = {
    mihon.desktop.ui.browse.BrowseContentView(
        runtime = ctx.runtime,
        browseRequests = ctx.browseRequests,
        detailState = live.mangaDetailState,
        detailActions = mangaDetailActions(
            runtime = ctx.runtime,
            libraryPresenter = ctx.libraryPresenter,
            mangaDetailState = live.mangaDetailState,
            session = ctx.session,
            navigator = ctx.navigator,
            presenterScope = ctx.presenterScope,
            snackbarHostState = ctx.snackbarHostState,
            strings = mihon.desktop.i18n.LocalStrings.current,
        ),
        onOpenMangaDetail = ctx.libraryPresenter::openMangaDetail,
        onCloseMangaDetail = { ctx.libraryPresenter.selectManga(null) },
        onRetryMangaDetail = ctx.libraryPresenter::retryDetail,
    )
}
