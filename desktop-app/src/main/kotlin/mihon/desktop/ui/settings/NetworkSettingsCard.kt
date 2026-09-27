package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.DesktopNetworkSettingsStore
import mihon.desktop.extension.DesktopProxyMode
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.preferences.DesktopPreferenceStore

@Composable
fun NetworkSettingsCard(preferences: DesktopPreferenceStore) {
    val strings = LocalStrings.current
    val store = remember(preferences) { DesktopNetworkSettingsStore(preferences) }
    var policy by remember(store) { mutableStateOf(store.load()) }
    var port by remember { mutableStateOf(policy.proxyPort.toString()) }
    var connectTimeout by remember { mutableStateOf(policy.connectTimeoutSeconds.toString()) }
    var readTimeout by remember { mutableStateOf(policy.readTimeoutSeconds.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("network-settings-card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                strings.networkSettingsTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                strings.networkSettingsDescription,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopProxyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = policy.proxyMode == mode,
                        onClick = {
                            policy = policy.copy(proxyMode = mode)
                            saved = false
                        },
                        label = {
                            Text(
                                when (mode) {
                                    DesktopProxyMode.SYSTEM -> strings.networkSettingsProxySystem
                                    DesktopProxyMode.DIRECT -> strings.networkSettingsProxyDirect
                                    DesktopProxyMode.HTTP -> "HTTP"
                                    DesktopProxyMode.SOCKS -> "SOCKS"
                                },
                            )
                        },
                    )
                }
            }
            if (policy.proxyMode == DesktopProxyMode.HTTP || policy.proxyMode == DesktopProxyMode.SOCKS) {
                OutlinedTextField(
                    value = policy.proxyHost,
                    onValueChange = {
                        policy = policy.copy(proxyHost = it)
                        saved = false
                    },
                    label = { Text(strings.networkSettingsProxyHost) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        saved = false
                    },
                    label = { Text(strings.networkSettingsPort) },
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = {
                        connectTimeout = it
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.networkSettingsConnectTimeout) },
                )
                OutlinedTextField(
                    value = readTimeout,
                    onValueChange = {
                        readTimeout = it
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.networkSettingsReadTimeout) },
                )
            }
            OutlinedTextField(
                value = policy.userAgent,
                onValueChange = {
                    policy = policy.copy(userAgent = it)
                    saved = false
                },
                label = { Text("User-Agent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) Text(strings.networkSettingsSaved)
            Button(onClick = {
                error = null
                try {
                    val updated = policy.copy(
                        proxyPort = port.toIntOrNull() ?: 0,
                        connectTimeoutSeconds = connectTimeout.toIntOrNull() ?: 0,
                        readTimeoutSeconds = readTimeout.toIntOrNull() ?: 0,
                    )
                    store.save(updated)
                    policy = updated
                    saved = true
                } catch (failure: Exception) {
                    error = failure.message
                }
            }) { Text(strings.networkSettingsSave) }
        }
    }
}
