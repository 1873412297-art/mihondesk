package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

@Composable
internal fun ExtensionInstallStatus(state: BrowseUiState, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    val phase = state.installPhase
    if (state.isInstalling) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("extension-install-status"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                strings.text(
                    when (phase) {
                        ExtensionInstallPhase.Downloading -> UiText.ExtensionDownloading
                        ExtensionInstallPhase.Cancelling -> UiText.ExtensionCancelling
                        else -> UiText.ExtensionInstalling
                    },
                    state.installingName,
                ),
                Modifier.weight(1f).padding(horizontal = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (phase != ExtensionInstallPhase.Installing && phase != null) {
                TextButton(
                    onCancel,
                    enabled = phase == ExtensionInstallPhase.Downloading,
                    modifier = Modifier.testTag("extension-install-cancel"),
                ) {
                    Text(strings.dialogCancel)
                }
            }
        }
    } else if (state.installationCancelled) {
        Text(
            strings.text(UiText.ExtensionCancelled),
            Modifier.padding(vertical = 8.dp).testTag("extension-install-cancelled"),
        )
    }
}
