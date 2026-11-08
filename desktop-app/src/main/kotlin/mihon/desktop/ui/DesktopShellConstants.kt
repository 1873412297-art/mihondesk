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

internal const val DESKTOP_MAIN_HEADLINE_TEST_TAG = "desktop-main-headline"
internal const val DESKTOP_NAVIGATION_RAIL_TEST_TAG = "desktop-navigation-rail"
internal const val DESKTOP_RAIL_TOGGLE_TEST_TAG = "desktop-rail-toggle"
internal const val DESKTOP_RAIL_SCRIM_TEST_TAG = "desktop-rail-scrim"
internal val RAIL_COLLAPSED_WIDTH = 80.dp
internal val RAIL_EXPANDED_WIDTH = 220.dp

/** Rail IA: daily destinations on top, Settings alone at the bottom. Stats lives in History, About in Settings. */
internal val RAIL_PRIMARY_DESTINATIONS = listOf(
    DesktopDestination.Library,
    DesktopDestination.Updates,
    DesktopDestination.History,
    DesktopDestination.Browse,
    DesktopDestination.Downloads,
)
internal val RAIL_SECONDARY_DESTINATIONS = listOf(DesktopDestination.Settings)

/** Stats is a History segment and About a Settings section, so the rail highlights their hosts. */
internal fun railSelectionFor(selected: DesktopDestination): DesktopDestination = when (selected) {
    DesktopDestination.Stats -> DesktopDestination.History
    DesktopDestination.About -> DesktopDestination.Settings
    else -> selected
}

const val DESKTOP_LOCK_NOW_BUTTON_TEST_TAG = "desktop-lock-now-button"
