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
internal fun ShellFrame(
    density: mihon.desktop.ui.common.ContentDensity,
    content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit,
) {
    ProvideContentDensity(density) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Count shown on a navigation item; [error] badges use the error colour, others the primary colour. */
internal data class NavItemBadge(val count: Int, val error: Boolean, val testTag: String)

internal fun navBadgeFor(
    destination: DesktopDestination,
    updatesUnreadCount: Int,
    downloadsBadge: DownloadsNavBadge?,
): NavItemBadge? = when (destination) {
    DesktopDestination.Updates -> updatesUnreadCount.takeIf { it > 0 }
        ?.let { NavItemBadge(it, error = false, testTag = "updates-unread-badge") }
    DesktopDestination.Downloads ->
        downloadsBadge
            ?.let { NavItemBadge(it.count, error = it.failed, testTag = "downloads-nav-badge") }
    else -> null
}

@Composable
internal fun DesktopRail(
    expanded: Boolean,
    shellSelected: DesktopDestination,
    railSelected: DesktopDestination,
    updatesUnreadCount: Int,
    downloadsBadge: DownloadsNavBadge?,
    onToggle: () -> Unit,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onLockNow: (() -> Unit)?,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val strings = LocalStrings.current
    val toggleLabel = strings.text(if (expanded) UiText.CollapseNavigation else UiText.ExpandNavigation)
    val selected = railSelected
    Surface(
        modifier = Modifier.fillMaxHeight()
            .width(
                androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (expanded) RAIL_EXPANDED_WIDTH else RAIL_COLLAPSED_WIDTH,
                    animationSpec = androidx.compose.animation.core.tween(
                        if (mihon.desktop.platform.WindowsReducedMotion.prefersReducedMotion) 0 else 200,
                    ),
                    label = "rail-width",
                ).value,
            )
            .testTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG),
        tonalElevation = tonalElevation,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            DesktopTooltipBox(text = "$toggleLabel (${mihon.desktop.platform.DesktopPlatform.primaryModifierLabel}B)") {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.padding(horizontal = 12.dp).testTag(DESKTOP_RAIL_TOGGLE_TEST_TAG),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                        contentDescription = toggleLabel,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            RAIL_PRIMARY_DESTINATIONS.forEach { destination ->
                RailEntry(
                    destination = destination,
                    expanded = expanded,
                    selected = selected,
                    onDestinationSelected = onDestinationSelected,
                    badge = navBadgeFor(destination, updatesUnreadCount, downloadsBadge),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider()
            RAIL_SECONDARY_DESTINATIONS.forEach { destination ->
                RailEntry(
                    destination = destination,
                    expanded = expanded,
                    selected = selected,
                    onDestinationSelected = onDestinationSelected,
                    badge = navBadgeFor(destination, updatesUnreadCount, downloadsBadge),
                )
            }
            if (onLockNow != null) {
                HorizontalDivider()
                DesktopTooltipBox(text = strings.text(UiText.LockApp)) {
                    IconButton(
                        onClick = onLockNow,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = strings.text(UiText.LockApp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RailEntry(
    destination: DesktopDestination,
    expanded: Boolean,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    badge: NavItemBadge?,
) {
    if (!expanded) {
        DestinationItem(
            destination = destination,
            selected = selected,
            onDestinationSelected = onDestinationSelected,
            badge = badge,
        )
        return
    }
    val strings = LocalStrings.current
    androidx.compose.material3.NavigationDrawerItem(
        label = { Text(strings.destinationLabel(destination), maxLines = 1) },
        selected = destination == selected,
        onClick = { onDestinationSelected(destination) },
        icon = { Icon(destinationIcon(destination), contentDescription = null) },
        badge = badge?.let { b ->
            {
                val colors = androidx.compose.material3.MaterialTheme.colorScheme
                androidx.compose.material3.Badge(
                    modifier = Modifier.testTag(b.testTag),
                    containerColor = if (b.error) colors.error else colors.primary,
                    contentColor = if (b.error) colors.onError else colors.onPrimary,
                ) { Text(b.count.toString()) }
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            .testTag("rail-item-${destination.name.lowercase()}"),
    )
}

@Composable
internal fun DestinationItem(
    destination: DesktopDestination,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    badge: NavItemBadge? = null,
) {
    val strings = LocalStrings.current
    DesktopTooltipBox(text = strings.destinationLabel(destination)) {
        NavigationRailItem(
            selected = destination == selected,
            onClick = { onDestinationSelected(destination) },
            icon = {
                if (badge != null) {
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            val colors = androidx.compose.material3.MaterialTheme.colorScheme
                            androidx.compose.material3.Badge(
                                modifier = Modifier.testTag(badge.testTag),
                                containerColor = if (badge.error) colors.error else colors.primary,
                                contentColor = if (badge.error) colors.onError else colors.onPrimary,
                            ) {
                                Text(
                                    badge.count.toString(),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    ) {
                        Icon(
                            imageVector = destinationIcon(destination),
                            contentDescription = strings.destinationLabel(destination),
                        )
                    }
                } else {
                    Icon(
                        imageVector = destinationIcon(destination),
                        contentDescription = strings.destinationLabel(destination),
                    )
                }
            },
            // Collapsed rail is icon-only; the label is shown via the hover tooltip.
            label = null,
            alwaysShowLabel = false,
            colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
