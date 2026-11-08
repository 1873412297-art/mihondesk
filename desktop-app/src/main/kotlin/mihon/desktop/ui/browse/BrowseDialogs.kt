package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustRequest
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.extensionStoreLabel
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

/** Confirms installing an extension, including the network domains and signing key it declares. */
@Composable
internal fun InstallConfirmationDialog(
    item: ExtensionStoreItem,
    strings: DesktopStrings,
    onDismiss: () -> Unit,
    onConfirm: (ExtensionStoreItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.browseInstallExtensionTitle(item.name)) },
        text = { InstallConfirmationBody(item, strings) },
        confirmButton = {
            Button(
                onClick = { onConfirm(item.copy(trustOnInstall = true)) },
                modifier = Modifier.testTag("confirm-install-button"),
            ) {
                Text(strings.browseTrustAndInstall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.dialogCancel) }
        },
    )
}

@Composable
private fun InstallConfirmationBody(item: ExtensionStoreItem, strings: DesktopStrings) {
    Column {
        Text(strings.browsePackageLabel(item.pkg))
        Text(strings.browseVersionLabel(item.version))
        Text(strings.browseLanguageLabel(item.lang.uppercase()))
        extensionStoreLabel(item)?.let { store -> Text(strings.extensionStoreLabel(store)) }
        if (item.declaredDomains.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(strings.browseNetworkDomainsHeader, fontWeight = FontWeight.SemiBold)
            item.declaredDomains.forEach { domain ->
                Text(" • $domain", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val usableSigningKey = ExtensionTrustStore.normalizeFingerprint(item.signingKey)
        if (usableSigningKey.isNotBlank()) {
            Text(
                text = strings.text(UiText.RepositorySigningKey, usableSigningKey),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = strings.text(UiText.NoRepositoryKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("unsigned-extension-warning"),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.browseNetworkPermissionNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Asks for explicit trust when the installer blocked an install on an unknown signer. */
@Composable
internal fun TrustRetryDialog(
    request: ExtensionTrustRequest,
    strings: DesktopStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.text(UiText.TrustExtensionSignature)) },
        text = {
            Column {
                Text(strings.browsePackageLabel(request.pkg))
                Text(
                    text = if (request.fingerprints.isEmpty()) {
                        strings.text(UiText.UnsignedExtensionHint)
                    } else {
                        strings.text(UiText.SignerFingerprint, request.fingerprints.joinToString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (request.reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(request.reason, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("confirm-trust-install-button")) {
                Text(strings.browseTrustAndInstall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-trust-button")) {
                Text(strings.dialogCancel)
            }
        },
    )
}

/** Lists, adds and removes extension repositories. */
@Composable
internal fun RepositoryManagementDialog(
    repositories: List<String>,
    strings: DesktopStrings,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newRepoUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.browseManageRepositories) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = { newRepoUrl = it },
                        placeholder = { Text("https://example.com/repo") },
                        modifier = Modifier.weight(1f).testTag("new-repo-input"),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newRepoUrl.isNotBlank()) {
                                onAdd(newRepoUrl.trim())
                                newRepoUrl = ""
                            }
                        },
                        modifier = Modifier.testTag("add-repo-button"),
                    ) {
                        Text(strings.browseAddRepository)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(strings.browseConfiguredRepositories, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(repositories) { repo -> RepositoryRow(repo, strings, onRemove) }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(strings.dialogClose) }
        },
    )
}

@Composable
private fun RepositoryRow(repo: String, strings: DesktopStrings, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = repo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(
            onClick = { onRemove(repo) },
            modifier = Modifier.testTag("remove-repo-$repo"),
        ) {
            Text(strings.browseRemoveRepository, color = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}
