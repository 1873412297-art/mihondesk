package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.sync.DiscoveredSyncService
import eu.kanade.tachiyomi.data.sync.LanSyncDiscovery
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.util.system.toast
import mihon.sync.transport.http.SyncPairingCode

/**
 * Full-screen dialog that shows discovered Mihon sync servers on the local network.
 *
 * - When `token != null`: one-tap pairing via [onPaired].
 * - When `token == null`: pre-fills host+port and shows guidance to scan or paste.
 */
@Composable
fun LanDiscoveryDialog(
    onDismiss: () -> Unit,
    onPaired: (SyncPairingCode) -> Unit,
    onPreFill: (host: String, port: Int) -> Unit,
) {
    val context = LocalContext.current
    val discovery = remember { LanSyncDiscovery(context) }

    // Start discovery when dialog opens; stop when it closes.
    LaunchedEffect(Unit) {
        discovery.refresh()
    }
    DisposableEffect(Unit) {
        onDispose { discovery.close() }
    }

    val services by discovery.discoveries().collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SyncStrings.discoverDevices) },
        text = {
            Column {
                if (services.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text(SyncStrings.discoverDevicesSearching)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        SyncStrings.discoverDevicesEmpty,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(services) { service ->
                            ServiceItem(
                                service = service,
                                onClick = {
                                    if (service.token != null) {
                                        val code = SyncPairingCode(
                                            host = service.host,
                                            port = service.port,
                                            token = service.token,
                                        )
                                        onPaired(code)
                                        onDismiss()
                                    } else {
                                        onPreFill(service.host, service.port)
                                        context.toast(SyncStrings.discoverDevicesNoQuickPair)
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { discovery.refresh() }) {
                Text(SyncStrings.discoverDevicesRefresh)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(SyncStrings.cancel)
            }
        },
    )
}

@Composable
private fun ServiceItem(service: DiscoveredSyncService, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = service.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${service.host}:${service.port}" +
                if (service.token != null) "" else " · ${SyncStrings.discoverDevicesNoQuickPair}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
