package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val sortedScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selected by remember(excludedScanlators) {
        mutableStateOf(excludedScanlators.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("scanlator-filter-dialog"),
        title = { Text(strings.text(UiText.ExcludeScanlators)) },
        text = {
            if (sortedScanlators.isEmpty()) {
                Text(strings.text(UiText.NoScanlators))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        strings.text(UiText.ExcludeScanlatorsHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(sortedScanlators, key = { it }) { scanlator ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scanlator-filter-option-$scanlator")
                                    .clickable {
                                        selected = if (scanlator in selected) {
                                            selected - scanlator
                                        } else {
                                            selected + scanlator
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = scanlator in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + scanlator else selected - scanlator
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(scanlator, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(strings.dialogCancel)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sortedScanlators.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selected = if (selected.isEmpty()) availableScanlators.toSet() else emptySet()
                        },
                        modifier = Modifier.testTag("scanlator-filter-toggle-all"),
                    ) {
                        Text(if (selected.isEmpty()) strings.chapterBatchSelectAll else strings.text(UiText.Clear))
                    }
                }
                TextButton(
                    onClick = {
                        onConfirm(selected)
                        onDismissRequest()
                    },
                    modifier = Modifier.testTag("scanlator-filter-confirm"),
                ) {
                    Text(strings.dialogOk)
                }
            }
        },
    )
}

@Composable
internal fun SetChapterSettingsAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (applyToExistingManga: Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    var applyToExisting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("chapter-settings-set-default-dialog"),
        title = { Text(strings.text(UiText.DefaultChapterSettings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.text(UiText.ApplyChapterDefaults))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chapter-settings-apply-existing")
                        .clickable { applyToExisting = !applyToExisting }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = applyToExisting,
                        onCheckedChange = { applyToExisting = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.text(UiText.ApplyExistingManga), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(strings.dialogCancel)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmed(applyToExisting) },
                modifier = Modifier.testTag("chapter-settings-set-default-confirm"),
            ) {
                Text(strings.dialogOk)
            }
        },
    )
}
