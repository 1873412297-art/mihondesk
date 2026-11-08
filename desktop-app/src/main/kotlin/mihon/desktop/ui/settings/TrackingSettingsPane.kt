package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.text
import mihon.desktop.track.DesktopTracker
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.TrackerAuthType
import mihon.desktop.ui.track.TrackerLoginDialog

@Composable
internal fun TrackingSettingsPane(trackerManager: DesktopTrackerManager?) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var loginTracker by remember { mutableStateOf<DesktopTracker?>(null) }
    val trackers = trackerManager?.trackers ?: emptyList()
    val loggedInCount = trackers.count { it.isLoggedIn }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionTracking,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    strings.settingsTrackingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.settingsTrackingDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    strings.settingsConnectedTrackers(loggedInCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val selfHostedNames = setOf("Komga", "Kavita", "Suwayomi")
        val publicTrackers = trackers.filter { it.name !in selfHostedNames }
        val selfHostedTrackers = trackers.filter { it.name in selfHostedNames }

        @Composable
        fun TrackerCard(tracker: DesktopTracker) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("tracker-setting-card-${tracker.id}"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tracker.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tracker.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (tracker.isLoggedIn) {
                                Text(
                                    strings.settingsTrackerLoggedInAs(tracker.username ?: "User", tracker.serverUrl),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    strings.trackingNotLoggedIn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }

                    if (tracker.isLoggedIn) {
                        FilledTonalButton(
                            onClick = { trackerManager?.logout(tracker.id) },
                            modifier = Modifier.testTag("tracker-logout-${tracker.id}"),
                        ) {
                            Text(strings.settingsTrackerLogout)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { loginTracker = tracker },
                            modifier = Modifier.testTag("tracker-login-${tracker.id}"),
                        ) {
                            Text(
                                if (tracker.authType == TrackerAuthType.SERVER) {
                                    strings.trackerConnect
                                } else {
                                    strings.trackerLogin
                                },
                            )
                        }
                    }
                }
            }
        }

        if (publicTrackers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.settingsTrackingPublicServices,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                publicTrackers.forEach { tracker ->
                    TrackerCard(tracker)
                }
            }
        }

        if (selfHostedTrackers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.settingsTrackingSelfHosted,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selfHostedTrackers.forEach { tracker ->
                    TrackerCard(tracker)
                }
            }
        }
    }

    loginTracker?.let { tracker ->
        TrackerLoginDialog(
            tracker = tracker,
            onDismiss = { loginTracker = null },
            onLogin = { creds ->
                trackerManager?.login(tracker.id, creds) == true
            },
        )
    }
}
