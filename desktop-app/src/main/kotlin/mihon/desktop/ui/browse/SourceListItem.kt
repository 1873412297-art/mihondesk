package mihon.desktop.ui.browse

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.onSecondaryClick
import mihon.extension.model.SourceDescriptor

@Composable
internal fun SourceListItem(
    source: SourceDescriptor,
    isPinned: Boolean,
    iconUri: String? = null,
    isNsfw: Boolean = false,
    installedExtensions: List<InstalledExtension> = emptyList(),
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHide: () -> Unit = {},
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val associatedExtension = remember(source.id, installedExtensions) {
        installedExtensions.find { ext -> ext.manifest.sources.any { it.id == source.id } }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val currentOnSourceSelected by rememberUpdatedState(onSourceSelected)
    val onPopularClick = remember(source.id) {
        { currentOnSourceSelected(source, SourceListingMode.Popular) }
    }
    val onLatestClick = remember(source.id) {
        { currentOnSourceSelected(source, SourceListingMode.Latest) }
    }
    val currentOnTogglePin by rememberUpdatedState(onTogglePin)
    val onPinClick = remember(source.id) {
        { currentOnTogglePin(source.id) }
    }
    val currentOnExtensionSelected by rememberUpdatedState(onExtensionSelected)
    val onExtClick = remember(associatedExtension) {
        {
            val ext = associatedExtension
            if (ext != null) currentOnExtensionSelected(ext)
        }
    }
    val pinLabel = (if (isPinned) strings.browseUnpin else strings.browsePin).replace("☆", "").replace("★", "").trim()

    BrowseItemCard(
        modifier = Modifier.testTag("source-item-${source.id}"),
        onClick = onPopularClick,
        onSecondaryClick = { showContextMenu = true },
        interactionSource = interactionSource,
    ) {
        DesktopTooltipBox(text = strings.browseSourceId(source.id.toString())) {
            ExtensionIcon(name = source.name, iconUri = iconUri, size = 48.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (source.id == BundledLocalSource.ID) {
                    SmallBadge(strings.browseLocalBadge, BadgeTone.Primary)
                }
                if (isNsfw) SmallBadge("18+", BadgeTone.Danger)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge(source.lang.uppercase())
                val extName = associatedExtension?.manifest?.name
                val secondary = listOfNotNull(
                    extName?.takeIf { !it.equals(source.name, ignoreCase = true) },
                    associatedExtension?.manifest?.version?.let { "v$it" },
                ).joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (associatedExtension != null && hovered) {
                DesktopTooltipBox(text = strings.extensionInfo) {
                    IconButton(onClick = onExtClick, modifier = Modifier.testTag("source-ext-btn-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.extensionInfo,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (source.supportsLatest) {
                DesktopTooltipBox(text = strings.browseSourceLatest) {
                    IconButton(onClick = onLatestClick, modifier = Modifier.testTag("latest-btn-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = strings.browseSourceLatest,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            DesktopTooltipBox(text = pinLabel) {
                IconButton(onClick = onPinClick, modifier = Modifier.testTag("pin-btn-${source.id}")) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = pinLabel,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box {
                DesktopTooltipBox(text = strings.browseMoreActions) {
                    IconButton(onClick = {
                        showContextMenu = true
                    }, modifier = Modifier.testTag("source-overflow-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = strings.browseMoreActions,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.browseSourceBrowse) },
                        onClick = {
                            showContextMenu = false
                            onPopularClick()
                        },
                    )
                    if (source.supportsLatest) {
                        DropdownMenuItem(
                            text = { Text(strings.browseSourceLatest) },
                            onClick = {
                                showContextMenu = false
                                onLatestClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(pinLabel) },
                        onClick = {
                            showContextMenu = false
                            onPinClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browseHideSource) },
                        modifier = Modifier.testTag("source-context-hide-${source.id}"),
                        onClick = {
                            showContextMenu = false
                            onHide()
                        },
                    )
                    if (associatedExtension != null) {
                        DropdownMenuItem(
                            text = { Text(strings.text(UiText.ContextExtensionSettings)) },
                            onClick = {
                                showContextMenu = false
                                onExtClick()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                strings.browseSourceId(source.id.toString()),
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
