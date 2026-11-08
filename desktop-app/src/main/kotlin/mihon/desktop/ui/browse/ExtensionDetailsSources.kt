package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.SourceState
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.extension.model.SourceDescriptor

internal fun LazyListScope.extensionSourceItems(
    strings: DesktopStrings,
    sources: List<SourceState>,
    onClickSourcePreferences: (Long) -> Unit,
    onClickSource: (SourceDescriptor) -> Unit,
    onToggleSourceEnabled: (Long, Boolean) -> Unit,
    onToggleSourceIncognito: (Long, Boolean) -> Unit,
) {
    item {
        Text(
            text = "${strings.browseTabSources} (${sources.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (sources.isEmpty()) {
        item {
            Text(
                text = strings.text(UiText.NoExtensionSources),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("extension-details-no-sources"),
            )
        }
    } else {
        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
                onToggleEnabled = { enabled -> onToggleSourceEnabled(source.source.id, enabled) },
                onToggleIncognito = { incognito -> onToggleSourceIncognito(source.source.id, incognito) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun SourceSwitchPreference(
    source: SourceState,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (SourceDescriptor) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleEnabled(!source.isEnabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("extension-source-${source.source.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceInfo(
            source = source,
            modifier = Modifier.weight(1f),
        )
        SourceSwitchActions(
            source = source,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickSource = onClickSource,
            onToggleEnabled = onToggleEnabled,
            onToggleIncognito = onToggleIncognito,
        )
    }
}

@Composable
private fun SourceInfo(
    source: SourceState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = source.source.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "${LocaleHelper.getSourceDisplayName(source.source.lang)} • ID: ${source.source.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceSwitchActions(
    source: SourceState,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (SourceDescriptor) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = { onClickSource(source.source) },
            modifier = Modifier.testTag("extension-source-browse-${source.source.id}"),
        ) {
            Text(strings.browseSourceBrowse)
        }
        if (source.isConfigurable) {
            IconButton(
                onClick = { onClickSourcePreferences(source.source.id) },
                modifier = Modifier.testTag("extension-source-preferences-${source.source.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.actionSettings,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = source.isEnabled,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.testTag("extension-source-enabled-${source.source.id}"),
        )
        IconButton(
            onClick = { onToggleIncognito(!source.isIncognito) },
            modifier = Modifier.testTag("extension-source-incognito-${source.source.id}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = strings.extensionIncognitoMode,
                tint = if (source.isIncognito) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}
