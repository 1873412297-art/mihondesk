package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.extensionStoreLabel
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.i18n.trustStatusLabel
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.onSecondaryClick

@Composable
internal fun ExtensionItemRow(
    item: ExtensionStoreItem,
    installed: InstalledExtension?,
    trustStatus: ExtensionTrustStatus?,
    isInstalling: Boolean,
    installEnabled: Boolean,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit,
    onTrustExtension: (InstalledExtension) -> Unit,
    onRevokeExtension: (InstalledExtension) -> Unit,
) {
    val strings = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }
    val displayTrustStatus = if (installed != null) {
        trustStatus ?: installed.trustStatus
    } else if (ExtensionTrustStore.normalizeFingerprint(item.signingKey).isNotBlank()) {
        ExtensionTrustStatus.TRUSTED
    } else {
        ExtensionTrustStatus.UNKNOWN
    }
    val hasUpdate = installed != null && item.versionCode > installed.manifest.versionCode
    val disabled = installed != null && !installed.isEnabled

    BrowseItemCard(
        modifier = Modifier.testTag("extension-item-${item.pkg}"),
        onClick = installed?.let { { onExtensionSelected(it) } },
        onSecondaryClick = if (installed != null) ({ showMenu = true }) else null,
    ) {
        DesktopTooltipBox(text = item.pkg) {
            Box {
                ExtensionIcon(
                    name = item.name,
                    iconUri = extensionIconUri(installed, item),
                    size = 48.dp,
                    dimmed = disabled,
                )
                if (hasUpdate) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(
                            Alignment.TopEnd,
                        ).padding(1.dp).testTag("ext-update-badge-${item.pkg}"),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (disabled) MaterialTheme.colorScheme.outline else Color.Unspecified,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isNsfw) SmallBadge("18+", BadgeTone.Danger)
                if (installed != null) {
                    SmallBadge(
                        text = strings.trustStatusLabel(displayTrustStatus),
                        tone = when (displayTrustStatus) {
                            ExtensionTrustStatus.TRUSTED -> BadgeTone.Positive
                            ExtensionTrustStatus.INVALID -> BadgeTone.Danger
                            else -> BadgeTone.Neutral
                        },
                        textTag = "extension-trust-status-${item.pkg}",
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge(item.lang.uppercase())
                val version = if (hasUpdate) {
                    "v${installed!!.manifest.version} → v${item.version}"
                } else {
                    "v${installed?.manifest?.version ?: item.version}"
                }
                val storeLabel = extensionStoreLabel(
                    item.storeName,
                    item.repoUrl.ifBlank { installed?.repoUrl.orEmpty() },
                )
                val secondary = buildList {
                    add(version)
                    if (item.sources.size > 1) {
                        add(strings.text(UiText.ExtensionSources, item.sources.joinToString(", ") { it.name }))
                    }
                }.joinToString(" · ")
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (storeLabel != null) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = strings.extensionStoreLabel(storeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("extension-store-${item.pkg}"),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (installed == null) {
                FilledTonalButton(
                    onClick = { onRequestInstall(item) },
                    modifier = Modifier.testTag("install-btn-${item.pkg}"),
                    enabled = installEnabled,
                ) {
                    Text(strings.browseInstall)
                }
            } else {
                if (hasUpdate) {
                    Button(
                        onClick = { onRequestInstall(item) },
                        modifier = Modifier.testTag("update-btn-${item.pkg}"),
                        enabled = installEnabled,
                    ) {
                        Text(strings.browseUpdate)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                DesktopTooltipBox(text = strings.extensionInfo) {
                    IconButton(
                        onClick = { onExtensionSelected(installed) },
                        modifier = Modifier.testTag("settings-btn-${item.pkg}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.extensionInfo,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box {
                    DesktopTooltipBox(text = strings.browseMoreActions) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("ext-overflow-${item.pkg}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = strings.browseMoreActions,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (displayTrustStatus == ExtensionTrustStatus.TRUSTED) {
                            DropdownMenuItem(
                                text = { Text(strings.extensionRevoke) },
                                leadingIcon = { Icon(Icons.Rounded.GppMaybe, contentDescription = null) },
                                modifier = Modifier.testTag("revoke-btn-${item.pkg}"),
                                onClick = {
                                    showMenu = false
                                    onRevokeExtension(installed)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(strings.extensionTrust) },
                                leadingIcon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
                                modifier = Modifier.testTag("trust-btn-${item.pkg}"),
                                onClick = {
                                    showMenu = false
                                    onTrustExtension(installed)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (installed.isEnabled) strings.browseDisable else strings.browseEnable) },
                            leadingIcon = {
                                Icon(
                                    if (installed.isEnabled) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.testTag("toggle-btn-${item.pkg}"),
                            onClick = {
                                showMenu = false
                                onToggleEnabled(item.pkg, !installed.isEnabled)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(strings.browseUninstall, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier.testTag("uninstall-btn-${item.pkg}"),
                            onClick = {
                                showMenu = false
                                onUninstallExtension(item.pkg)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    item.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            },
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
