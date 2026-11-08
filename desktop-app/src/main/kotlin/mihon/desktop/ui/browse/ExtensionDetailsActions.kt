package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.extensionStoreLabel
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.i18n.trustStatusLabel
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun ExtensionActionButtons(
    extension: InstalledExtension,
    updateItem: ExtensionStoreItem?,
    sources: List<SourceState>,
    showTrustButton: Boolean,
    showRevokeButton: Boolean,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onTrustClick: () -> Unit,
    onRevokeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExtensionInstallActions(
            extension = extension,
            updateItem = updateItem,
            showTrustButton = showTrustButton,
            showRevokeButton = showRevokeButton,
            onClickUninstall = onClickUninstall,
            onClickUpdate = onClickUpdate,
            onTrustClick = onTrustClick,
            onRevokeClick = onRevokeClick,
        )
        ExtensionRuntimeActions(
            extension = extension,
            sources = sources,
            onToggleEnabled = onToggleEnabled,
            onClearCookies = onClearCookies,
            onOpenSourcePreferences = onOpenSourcePreferences,
        )
    }
}

@Composable
private fun RowScope.ExtensionInstallActions(
    extension: InstalledExtension,
    updateItem: ExtensionStoreItem?,
    showTrustButton: Boolean,
    showRevokeButton: Boolean,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onTrustClick: () -> Unit,
    onRevokeClick: () -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedButton(
        modifier = Modifier
            .weight(1f)
            .testTag("extension-details-uninstall"),
        onClick = onClickUninstall,
    ) {
        Text(strings.extensionUninstall)
    }
    if (updateItem != null) {
        Button(
            modifier = Modifier
                .weight(1f)
                .testTag("extension-details-update"),
            onClick = onClickUpdate,
        ) {
            Text(strings.extensionUpdate)
        }
    }
    if (showTrustButton) {
        Button(
            modifier = Modifier
                .weight(1f)
                .testTag("extension-details-trust"),
            onClick = onTrustClick,
        ) {
            Text(strings.extensionTrust)
        }
    }
    if (showRevokeButton) {
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .testTag("extension-details-revoke"),
            onClick = onRevokeClick,
        ) {
            Text(strings.extensionRevoke)
        }
    }
}

@Composable
private fun RowScope.ExtensionRuntimeActions(
    extension: InstalledExtension,
    sources: List<SourceState>,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedButton(
        modifier = Modifier
            .weight(1f)
            .testTag("extension-details-toggle-enabled"),
        onClick = { onToggleEnabled(!extension.isEnabled) },
    ) {
        Text(if (extension.isEnabled) "Disable" else "Enable")
    }
    OutlinedButton(
        modifier = Modifier
            .weight(1f)
            .testTag("extension-details-clear-cookies"),
        onClick = onClearCookies,
    ) {
        Text(strings.extensionClearCookies)
    }
    if (sources.isNotEmpty()) {
        OutlinedButton(
            modifier = Modifier.testTag("extension-details-source-preferences"),
            onClick = { sources.firstOrNull()?.source?.id?.let(onOpenSourcePreferences) },
        ) {
            Text(strings.sourcePreferencesTitle)
        }
    }
}

@Composable
internal fun ExtensionIncognitoPreference(
    isExtensionIncognito: Boolean,
    onExtIncognitoChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    TextPreferenceWidget(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = strings.extensionIncognitoMode,
        subtitle = strings.extensionIncognitoSummary,
        icon = Icons.Rounded.VisibilityOff,
        widget = {
            Switch(
                checked = isExtensionIncognito,
                onCheckedChange = onExtIncognitoChange,
                modifier = Modifier.testTag("extension-details-incognito"),
            )
        },
        onPreferenceClick = { onExtIncognitoChange(!isExtensionIncognito) },
    )
}

@Composable
internal fun ExtensionMetadataRow(
    extension: InstalledExtension,
    effectiveTrustStatus: ExtensionTrustStatus,
    updateItem: ExtensionStoreItem?,
    dateFormatter: DateTimeFormatter,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExtensionStateTags(
            extension = extension,
            effectiveTrustStatus = effectiveTrustStatus,
            dateFormatter = dateFormatter,
        )
        ExtensionStoreTag(extension = extension, updateItem = updateItem)
    }
}

@Composable
private fun ExtensionStateTags(
    extension: InstalledExtension,
    effectiveTrustStatus: ExtensionTrustStatus,
    dateFormatter: DateTimeFormatter,
) {
    val strings = LocalStrings.current
    Text(
        text = if (extension.isEnabled) "Enabled" else "Disabled",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.testTag("extension-details-enabled"),
    )
    Text(
        text = "•",
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        text = strings.text(UiText.TrustState, strings.trustStatusLabel(effectiveTrustStatus)),
        style = MaterialTheme.typography.labelMedium,
        color = when (effectiveTrustStatus) {
            ExtensionTrustStatus.TRUSTED -> MaterialTheme.colorScheme.primary
            ExtensionTrustStatus.INVALID -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.testTag("extension-details-trust-label"),
    )
    Text(
        text = strings.trustStatusLabel(effectiveTrustStatus),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.testTag("extension-details-trust-status"),
    )
    if (extension.installedAt > 0L) {
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = dateFormatter.format(Instant.ofEpochMilli(extension.installedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.testTag("extension-details-install-date"),
        )
    }
}

@Composable
private fun ExtensionStoreTag(
    extension: InstalledExtension,
    updateItem: ExtensionStoreItem?,
) {
    val strings = LocalStrings.current
    val storeLabel = extensionStoreLabel(
        updateItem?.storeName.orEmpty(),
        updateItem?.repoUrl?.takeIf { it.isNotBlank() } ?: extension.repoUrl,
    )
    if (storeLabel != null) {
        Text(
            text = strings.extensionStoreLabel(storeLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.testTag("extension-details-repo"),
        )
    }
}
