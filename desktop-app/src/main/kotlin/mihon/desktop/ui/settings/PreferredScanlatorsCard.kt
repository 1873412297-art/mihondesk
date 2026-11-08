package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore

/** Preferred scanlator list, saved to the store on every keystroke. */
@Composable
internal fun PreferredScanlatorsCard(preferenceStore: DesktopPreferenceStore) {
    val strings = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var preferredScanlators by remember {
                mutableStateOf(
                    preferenceStore.property(mihon.desktop.ui.library.PREFERRED_SCANLATORS_KEY).orEmpty(),
                )
            }
            Text(strings.text(UiText.PreferredScanlators), fontWeight = FontWeight.Bold)
            Text(
                strings.text(UiText.PreferredScanlatorsHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedTextField(
                value = preferredScanlators,
                onValueChange = { value ->
                    preferredScanlators = value
                    preferenceStore.update {
                        setProperty(mihon.desktop.ui.library.PREFERRED_SCANLATORS_KEY, value)
                    }
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("settings-preferred-scanlators"),
            )
        }
    }
}
