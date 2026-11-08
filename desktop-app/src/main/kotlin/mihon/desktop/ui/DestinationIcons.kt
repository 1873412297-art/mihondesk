package mihon.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import mihon.desktop.navigation.DesktopDestination

internal fun icon(
    name: String,
    viewportWidth: Float,
    viewportHeight: Float,
    block: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
).apply {
    path(fill = SolidColor(Color.Black)) {
        block()
    }
}.build()

internal fun destinationIcon(destination: DesktopDestination): ImageVector = when (destination) {
    DesktopDestination.Library -> DestinationLibraryIcon
    DesktopDestination.Updates -> DestinationUpdatesIcon
    DesktopDestination.History -> DestinationHistoryIcon
    DesktopDestination.Browse -> DestinationBrowseIcon
    DesktopDestination.Downloads -> DestinationDownloadsIcon
    DesktopDestination.Stats -> DestinationStatsIcon
    DesktopDestination.Settings -> DestinationSettingsIcon
    DesktopDestination.About -> DestinationAboutIcon
}
