package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import mihon.sync.transport.http.SyncPairingCode

class SyncPairingConfirmScreen(
    private val host: String,
    private val port: Int,
    private val token: String,
) : Screen() {

    constructor(code: SyncPairingCode) : this(code.host, code.port, code.token)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val syncPreferences = remember { context.appGraph.syncPreferences }

        Scaffold(
            topBar = {
                AppBar(
                    title = SyncStrings.confirmPairingTitle,
                    navigateUp = { navigator.pop() },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = SyncStrings.confirmPairingMessage,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = SyncStrings.confirmPairingTarget,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$host:$port",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(SyncStrings.cancel)
                    }

                    Button(
                        onClick = {
                            val code = SyncPairingCode(host, port, token)
                            syncPreferences.updateFromPairingCode(code)
                            context.toast(SyncStrings.discoverDevicesPairSuccess.format(host))
                            navigator.pop()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(SyncStrings.confirmPairingButton)
                    }
                }
            }
        }
    }
}
