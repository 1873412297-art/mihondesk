package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.locale
import mihon.desktop.sync.gdrive.GoogleDriveBusy
import mihon.desktop.sync.gdrive.GoogleDriveError
import mihon.desktop.sync.gdrive.GoogleDriveSyncService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun gdriveErrorText(strings: DesktopStrings, error: GoogleDriveError): String = when (error) {
    GoogleDriveError.NOT_CONFIGURED -> strings.gdriveErrNotConfigured
    GoogleDriveError.AUTH_CANCELLED -> strings.gdriveErrCancelled
    GoogleDriveError.AUTH_EXPIRED -> strings.gdriveErrExpired
    GoogleDriveError.NETWORK -> strings.gdriveErrNetwork
    GoogleDriveError.CORRUPT -> strings.gdriveErrCorrupt
    GoogleDriveError.UNKNOWN -> strings.gdriveErrUnknown
}

internal fun gdriveLastSyncText(
    strings: DesktopStrings,
    lastSyncAt: Long,
    summary: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (lastSyncAt <= 0L) return strings.gdriveLastSync + strings.syncNever
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(strings.locale)
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncAt), zone).format(formatter)
    val sep = if (strings.gdriveLastSync.endsWith(":")) " " else ""
    return strings.gdriveLastSync + sep + time + if (summary.isNotBlank()) " ($summary)" else ""
}

@Composable
internal fun GoogleDriveSyncCard(service: GoogleDriveSyncService) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val state by service.state.collectAsState()
    val busy = state.busy != GoogleDriveBusy.IDLE

    Card(modifier = Modifier.fillMaxWidth().testTag("gdrive-sync-card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.gdriveTitle, fontWeight = FontWeight.Bold)
            Text(strings.gdriveDescription, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (state.signedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(10.dp),
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.signedIn) strings.gdriveStatusSignedIn else strings.gdriveStatusSignedOut,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (state.signedIn) {
                Text(
                    gdriveLastSyncText(strings, state.lastSyncAt, state.lastSummary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("gdrive-last-sync"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.signedIn) {
                    Button(
                        onClick = { scope.launch { runCatching { service.syncNow() } } },
                        enabled = !busy,
                        modifier = Modifier.testTag("gdrive-sync-now"),
                    ) {
                        Text(
                            if (state.busy ==
                                GoogleDriveBusy.SYNCING
                            ) {
                                strings.gdriveSyncing
                            } else {
                                strings.gdriveSyncNow
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { scope.launch { runCatching { service.signOut() } } },
                        enabled = !busy,
                        modifier = Modifier.testTag("gdrive-sign-out"),
                    ) { Text(strings.gdriveSignOut) }
                } else {
                    Button(
                        onClick = { scope.launch { runCatching { service.signIn() } } },
                        enabled = !busy && state.configured,
                        modifier = Modifier.testTag("gdrive-sign-in"),
                    ) {
                        Text(
                            if (state.busy ==
                                GoogleDriveBusy.SIGNING_IN
                            ) {
                                strings.gdriveSigningIn
                            } else {
                                strings.gdriveSignIn
                            },
                        )
                    }
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            state.error?.let { err ->
                Text(
                    gdriveErrorText(strings, err),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("gdrive-error"),
                )
            }
        }
    }
}
