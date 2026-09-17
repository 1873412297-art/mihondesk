package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences

@Composable
internal fun BackupStorageLocation(
    preferenceStore: DesktopPreferenceStore,
    savedPath: String,
    onSaved: (DesktopPreferences) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(savedPath) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun edit(value: String) {
        draft = value
        error = null
        saved = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.backupLocation, fontWeight = FontWeight.Medium)
        Text(
            strings.text(UiText.BackupPathCurrent, savedPath.ifBlank { strings.backupLocationDefault }),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = ::edit,
            label = { Text(strings.backupLocation) },
            placeholder = { Text(strings.backupLocationDefault) },
            modifier = Modifier.fillMaxWidth().testTag("backup-storage-input"),
            singleLine = true,
            enabled = !saving,
            isError = error != null,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val input = draft
                    saving = true
                    scope.launch {
                        try {
                            val validation = withContext(Dispatchers.IO) { validateStoragePath(input) }
                            error = validation.error
                            if (validation.error == null) {
                                val updated = withContext(Dispatchers.IO) {
                                    preferenceStore.updatePreferences { it.copy(backupStoragePath = validation.path) }
                                }
                                draft = updated.backupStoragePath
                                saved = true
                                onSaved(updated)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = UiText.BackupPathSaveFailed
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && draft != savedPath,
                modifier = Modifier.testTag("backup-storage-save"),
            ) {
                Text(strings.text(if (saving) UiText.CheckingDownloadPath else UiText.SaveBackupPath))
            }
            OutlinedButton(
                onClick = {
                    chooseStorageDirectory(draft, null, strings.backupLocation)?.let { edit(it.toString()) }
                },
                enabled = !saving,
                modifier = Modifier.testTag("backup-storage-choose"),
            ) { Text(strings.settingsDownloadChooseFolder) }
            if (draft.isNotBlank()) {
                TextButton(
                    onClick = { edit("") },
                    enabled = !saving,
                    modifier = Modifier.testTag("backup-storage-default"),
                ) { Text(strings.settingsDownloadUseDefault) }
            }
        }
        error?.let {
            Text(
                strings.text(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("backup-storage-error"),
            )
        }
        if (saved) {
            Text(
                strings.text(UiText.BackupPathSaved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("backup-storage-saved"),
            )
        }
        Text(
            strings.text(UiText.BackupPathHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
