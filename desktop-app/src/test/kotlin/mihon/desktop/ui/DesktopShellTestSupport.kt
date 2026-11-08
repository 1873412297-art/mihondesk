package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.common.LayoutPrefsModel
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.tasks.downloadsNavBadge

/** Small fixtures for the shell's public state/action API; no production callback adapter. */
@Composable
internal fun TestDesktopShell(
    selected: DesktopDestination = DesktopDestination.Library,
    onDestinationSelected: (DesktopDestination) -> Unit = {},
    libraryState: LibraryUiState = LibraryUiState(),
    standaloneMangaDetails: Boolean = false,
    downloadsQueue: List<mihon.desktop.download.DesktopDownload> = emptyList(),
    onReadDownloadedChapter: (Long, Long) -> Unit = { _, _ -> },
    statsData: mihon.desktop.stats.DesktopStatsData = mihon.desktop.stats.DesktopStatsData(),
    incognitoMode: Boolean = false,
    onLockNow: (() -> Unit)? = null,
    isUpcomingOpen: Boolean = false,
    onOpenUpcoming: () -> Unit = {},
    onCloseUpcoming: () -> Unit = {},
    upcomingContent: (@Composable () -> Unit)? = null,
    homeToday: List<mihon.desktop.ui.home.TodayMangaUpdates> = emptyList(),
) {
    val layoutPrefs = remember { LayoutPrefsModel(null) }
    DesktopShell(
        DesktopShellState(
            selected = selected,
            railSection = selected,
            libraryState = libraryState,
            standaloneMangaDetails = standaloneMangaDetails,
            downloadsQueue = remember(downloadsQueue) { { downloadsQueue } },
            downloadsBadge = remember(downloadsQueue) { downloadsNavBadge(downloadsQueue) },
            statsData = statsData,
            incognitoMode = incognitoMode,
            isUpcomingOpen = isUpcomingOpen,
            homeToday = homeToday,
            contentDensity = layoutPrefs.contentDensity,
            railCollapsed = layoutPrefs.railCollapsed,
            railToggleRequests = layoutPrefs.railToggleRequests,
        ),
        DesktopShellActions(
            onDestinationSelected = onDestinationSelected,
            downloadActions = DownloadActions(onReadChapter = onReadDownloadedChapter),
            onLockNow = onLockNow,
            onOpenUpcoming = onOpenUpcoming,
            onCloseUpcoming = onCloseUpcoming,
            upcomingContent = upcomingContent,
            onContentDensityChange = layoutPrefs::updateContentDensity,
            onRailCollapsedChange = layoutPrefs::updateRailCollapsed,
        ),
    )
}
