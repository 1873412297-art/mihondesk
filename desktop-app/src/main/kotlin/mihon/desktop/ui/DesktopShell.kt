package mihon.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.NavRailState
import mihon.desktop.ui.common.ProvideContentDensity
import mihon.desktop.ui.library.LibraryScreen
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.tasks.DownloadsNavBadge

@Composable
internal fun DesktopShell(
    state: DesktopShellState,
    actions: DesktopShellActions,
) {
    val strings = LocalStrings.current
    var isCookieManagerOpen by remember { mutableStateOf(false) }
    val primary = RAIL_PRIMARY_DESTINATIONS
    val secondary = RAIL_SECONDARY_DESTINATIONS
    val railSelected = railSelectionFor(state.railSection)
    val contentDensity = state.contentDensity
    val railCollapsedPref = state.railCollapsed
    var narrowOverlayOpen by remember { mutableStateOf(false) }
    val downloadBadge = state.downloadsBadge
    val snackbarHostState = LocalSnackbarHostState.current

    ShellFrame(contentDensity) {
        val narrow = maxWidth < NavRailState.AUTO_COLLAPSE_WIDTH
        val railState = NavRailState.resolve(railCollapsedPref, narrow, narrowOverlayOpen)
        val toggleRail = {
            if (narrow) {
                narrowOverlayOpen = !narrowOverlayOpen
            } else {
                actions.onRailCollapsedChange(!railCollapsedPref)
            }
        }
        val toggleRequests = state.railToggleRequests
        val seenToggleRequests = remember { mutableIntStateOf(toggleRequests) }
        LaunchedEffect(toggleRequests) {
            if (toggleRequests != seenToggleRequests.intValue) {
                seenToggleRequests.intValue = toggleRequests
                toggleRail()
            }
        }
        Row {
            DesktopRail(
                expanded = railState == NavRailState.Expanded,
                shellSelected = state.selected,
                railSelected = railSelected,
                updatesUnreadCount = state.updatesUnreadCount,
                downloadsBadge = downloadBadge,
                onToggle = toggleRail,
                onDestinationSelected = actions.onDestinationSelected,
                onLockNow = actions.onLockNow,
            )
            Column(modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.pagePadding)) {
                if (state.libraryBatchState.running || state.libraryBatchState.error != null) {
                    Surface(
                        color = if (state.libraryBatchState.error != null) {
                            androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("library-batch-progress"),
                    ) {
                        Text(
                            state.libraryBatchState.error ?: strings.text(
                                UiText.Processing,
                                state.libraryBatchState.processed,
                                state.libraryBatchState.total,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (state.trackSyncService != null && state.trackerManager != null) {
                    mihon.desktop.ui.track.TrackingRecoveryNotice(state.trackSyncService, state.trackerManager)
                }
                if (state.incognitoMode) {
                    Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("incognito-banner"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.incognitoBannerText,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            androidx.compose.material3.Button(
                                onClick = actions.onToggleIncognito,
                                modifier = Modifier.testTag("incognito-disable-button"),
                            ) {
                                Text(strings.incognitoDisable)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ShellDestinationContent(state, actions, onOpenCookieManager = { isCookieManagerOpen = true })

                    if (isCookieManagerOpen && state.cookieStore != null) {
                        mihon.desktop.ui.network.CookieManagerDialog(
                            cookieStore = state.cookieStore,
                            onDismissRequest = { isCookieManagerOpen = false },
                        )
                    }

                    if (snackbarHostState != null) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .widthIn(max = 560.dp)
                                .semantics {
                                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                                },
                        )
                    }
                }
            }
        }
        if (railState == NavRailState.OverlayExpanded) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                    )
                    .clickable { narrowOverlayOpen = false }
                    .testTag(DESKTOP_RAIL_SCRIM_TEST_TAG),
            )
            DesktopRail(
                expanded = true,
                shellSelected = state.selected,
                railSelected = railSelected,
                updatesUnreadCount = state.updatesUnreadCount,
                downloadsBadge = downloadBadge,
                onToggle = toggleRail,
                onDestinationSelected = { destination ->
                    narrowOverlayOpen = false
                    actions.onDestinationSelected(destination)
                },
                onLockNow = actions.onLockNow,
                tonalElevation = 6.dp,
            )
        }
    }
}
