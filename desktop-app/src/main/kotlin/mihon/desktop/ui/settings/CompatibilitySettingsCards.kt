package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import mihon.desktop.RenderBackend
import mihon.desktop.extension.DesktopDohProvider
import mihon.desktop.i18n.CompatibilityText
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences

@Composable
internal fun SystemTitleBarCard(
    presenter: SettingsPresenter,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    var useSystem by remember { mutableStateOf(presenter.preferences.value.useSystemTitleBar) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("system-title-bar-card")) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardHeading(strings.systemTitleBarTitle, strings.systemTitleBarSummary, Modifier.weight(1f))
            Switch(
                checked = useSystem,
                onCheckedChange = { checked ->
                    val updated = presenter.updatePreferences { it.copy(useSystemTitleBar = checked) }
                    useSystem = checked
                    onPreferencesChanged?.invoke(updated)
                },
                modifier = Modifier.testTag("system-title-bar-switch"),
            )
        }
    }
}

/** Pins Skiko's backend for drivers that misdraw; read once at the next start by [RenderBackend]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderBackendCard(preferenceStore: DesktopPreferenceStore) {
    val strings = LocalStrings.current
    val applied = remember(preferenceStore) { RenderBackend.load(preferenceStore) }
    var selected by remember(preferenceStore) { mutableStateOf(applied) }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("render-backend-card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeading(
                CompatibilityText.RenderTitle.resolve(strings),
                CompatibilityText.RenderSummary.resolve(strings),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RenderBackend.entries.forEach { backend ->
                    FilterChip(
                        selected = selected == backend,
                        onClick = {
                            RenderBackend.save(preferenceStore, backend)
                            selected = backend
                        },
                        label = { Text(backend.label().resolve(strings)) },
                        modifier = Modifier.testTag("render-backend-${backend.name.lowercase()}"),
                    )
                }
            }
            if (selected != applied) {
                Text(CompatibilityText.RenderRestart.resolve(strings), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** DoH choice inside the network card, so it is saved together with the proxy it depends on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DohProviderChips(selected: DesktopDohProvider, onSelect: (DesktopDohProvider) -> Unit) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardHeading(CompatibilityText.DohTitle.resolve(strings), CompatibilityText.DohSummary.resolve(strings))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopDohProvider.entries.forEach { provider ->
                FilterChip(
                    selected = selected == provider,
                    onClick = { onSelect(provider) },
                    label = { Text(provider.label(strings)) },
                    modifier = Modifier.testTag("doh-provider-${provider.name.lowercase()}"),
                )
            }
        }
    }
}

private fun DesktopDohProvider.label(strings: mihon.desktop.i18n.DesktopStrings): String = when (this) {
    DesktopDohProvider.OFF -> CompatibilityText.DohOff.resolve(strings)
    DesktopDohProvider.CLOUDFLARE -> "Cloudflare"
    DesktopDohProvider.GOOGLE -> "Google"
    DesktopDohProvider.QUAD9 -> "Quad9"
    DesktopDohProvider.ADGUARD -> "AdGuard"
}

@Composable
private fun CardHeading(title: String, summary: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun RenderBackend.label(): CompatibilityText = when (this) {
    RenderBackend.AUTO -> CompatibilityText.RenderAuto
    RenderBackend.DIRECT3D -> CompatibilityText.RenderDirect3d
    RenderBackend.OPENGL -> CompatibilityText.RenderOpenGl
    RenderBackend.SOFTWARE -> CompatibilityText.RenderSoftware
}
