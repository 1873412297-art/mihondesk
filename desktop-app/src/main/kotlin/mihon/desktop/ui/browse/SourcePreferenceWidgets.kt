package mihon.desktop.ui.browse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourcePreferenceType
import mihon.desktop.extension.SourcePreferencesSnapshot
import mihon.desktop.extension.decodeSourcePreferenceListValue
import mihon.desktop.extension.encodeSourcePreferenceListValue
import mihon.desktop.extension.getSourcePreferencesSnapshot
import mihon.desktop.extension.setSourcePreference
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.extension.model.SourceDescriptor

@Composable
internal fun PreferencesStateMessage(
    tag: String,
    title: String,
    detail: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag(tag),
            )
            if (detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
internal fun BooleanPreference(
    definition: SourcePreferenceDefinition,
    value: Boolean,
    onValueChange: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(definition.key, (!value).toString()) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition.title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (definition.summary.isNotBlank()) {
                Text(
                    text = definition.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = value,
            onCheckedChange = { onValueChange(definition.key, it.toString()) },
            modifier = Modifier.testTag("source-preference-switch-${definition.key}"),
        )
    }
}

@Composable
internal fun TextPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    val acceptsInput: (String) -> Boolean = when (definition.type) {
        SourcePreferenceType.Int, SourcePreferenceType.Long -> { input -> input.matches(Regex("-?\\d*")) }
        SourcePreferenceType.Float -> { input -> input.matches(Regex("-?\\d*\\.?\\d*")) }
        else -> { _ -> true }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> if (acceptsInput(input)) onValueChange(definition.key, input) },
            label = { Text(definition.title) },
            supportingText = if (definition.summary.isNotBlank()) {
                { Text(definition.summary) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("source-preference-field-${definition.key}"),
        )
    }
}

@Composable
internal fun SelectPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = definition.options.firstOrNull { it.value == value }?.label ?: value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = definition.options.isNotEmpty(),
                modifier = Modifier.testTag("source-preference-select-${definition.key}"),
            ) {
                Text(selectedLabel.ifBlank { "Select…" })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                definition.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onValueChange(definition.key, option.value)
                        },
                        modifier = Modifier.testTag(
                            "source-preference-select-option-${definition.key}-${option.value}",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ListPreference(
    definition: SourcePreferenceDefinition,
    value: String,
    onValueChange: (String, String) -> Unit,
) {
    val selected = remember(value) { decodeSourcePreferenceListValue(value).toSet() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (definition.options.isEmpty()) {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("source-preference-readonly-${definition.key}"),
            )
        } else {
            definition.options.forEach { option ->
                val checked = option.value in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updated = if (checked) selected - option.value else selected + option.value
                            onValueChange(definition.key, encodeSourcePreferenceListValue(updated))
                        }
                        .padding(vertical = 4.dp)
                        .testTag("source-preference-list-${definition.key}-${option.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            val updated = if (isChecked) selected + option.value else selected - option.value
                            onValueChange(definition.key, encodeSourcePreferenceListValue(updated))
                        },
                        modifier = Modifier.testTag(
                            "source-preference-list-checkbox-${definition.key}-${option.value}",
                        ),
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadOnlyPreference(
    definition: SourcePreferenceDefinition,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("source-preference-${definition.key}"),
    ) {
        Text(
            text = definition.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (definition.summary.isNotBlank()) {
            Text(
                text = definition.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("source-preference-readonly-${definition.key}"),
        )
    }
}
