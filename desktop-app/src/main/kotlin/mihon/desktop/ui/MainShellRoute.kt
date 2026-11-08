package mihon.desktop.ui

import androidx.compose.runtime.Composable
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopRoute
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.updates.AppUpdatePresenter

/** Every destination except the reader, plus shell-scoped dialogs and back handling. */
@Composable
internal fun MainShellRoute(
    ctx: AppShellContext,
    destination: DesktopRoute,
    live: ShellLiveState,
    appUpdatePresenter: AppUpdatePresenter,
    upcomingPresenter: UpcomingPresenter,
    updatesRowOverrides: UpdatesRowOverrides,
) {
    DesktopShell(
        state = shellState(ctx, destination, live),
        actions = shellActions(ctx, live, appUpdatePresenter, upcomingPresenter, updatesRowOverrides),
    )
    ChapterWebViewDialog(ctx)
    if (destination is DesktopDestination.MangaDetails) {
        DesktopBackHandler {
            ctx.libraryPresenter.selectManga(null)
            ctx.navigator.pop()
        }
    }
    if (destination == DesktopDestination.Upcoming) {
        DesktopBackHandler { ctx.navigator.pop() }
    }
}

@Composable
private fun ChapterWebViewDialog(ctx: AppShellContext) {
    ctx.session.chapterWebViewRequest?.let { (source, url) ->
        mihon.desktop.ui.browse.SourceWebPageDialog(
            runtime = ctx.runtime,
            source = source,
            urlOverride = url,
            onDismiss = { ctx.session.chapterWebViewRequest = null },
            onRetry = { },
        )
    }
}
