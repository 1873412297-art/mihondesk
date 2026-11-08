package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.nio.file.Path

/** Download folder picker and validation; the saved path updates the pane through [onSaved]. */
@Composable
internal fun DownloadStoragePathCard(
    presenter: SettingsPresenter,
    activePath: String,
    downloadsDir: Path?,
    onSaved: (DesktopPreferences) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pathDraft by remember { mutableStateOf(activePath) }
    var pathError by remember { mutableStateOf<UiText?>(null) }
    var pathSaved by remember { mutableStateOf(false) }
    var isSavingPath by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.settingsDownloadLocation, fontWeight = FontWeight.Bold)
            DownloadActivePath(downloadsDir)
            DownloadPathRow(
                downloadsDir = downloadsDir,
                pathDraft = pathDraft,
                pathError = pathError,
                isSavingPath = isSavingPath,
                canSave = pathDraft != activePath,
                onDraftChange = {
                    pathDraft = it
                    pathError = null
                    pathSaved = false
                },
                onSave = {
                    val draft = pathDraft
                    isSavingPath = true
                    scope.saveDownloadPath(presenter, draft) { error, updated ->
                        pathError = error
                        if (updated != null) {
                            pathDraft = updated.downloadStoragePath
                            pathSaved = true
                            onSaved(updated)
                        }
                        isSavingPath = false
                    }
                },
                onUseDefault = {
                    pathDraft = ""
                    pathError = null
                    pathSaved = false
                },
            )
            DownloadPathStatus(pathError, pathSaved)
        }
    }
}

/** Validates off the UI thread, then persists; reports the failure or the updated preferences. */
private fun CoroutineScope.saveDownloadPath(
    presenter: SettingsPresenter,
    draft: String,
    onResult: (UiText?, DesktopPreferences?) -> Unit,
) {
    launch {
        try {
            val validation = withContext(Dispatchers.IO) { validateStoragePath(draft) }
            if (validation.error != null) {
                onResult(validation.error, null)
                return@launch
            }
            val updated = withContext(Dispatchers.IO) {
                presenter.updatePreferences { it.copy(downloadStoragePath = validation.path) }
            }
            onResult(null, updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onResult(UiText.DownloadPathSaveFailed, null)
        }
    }
}

@Composable
private fun DownloadActivePath(downloadsDir: Path?) {
    val strings = LocalStrings.current
    Text(
        text = downloadsDir?.let { strings.settingsDownloadActivePath(it.toString()) }
            ?: strings.settingsDefaultStorageFolder,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DownloadPathRow(
    downloadsDir: Path?,
    pathDraft: String,
    pathError: UiText?,
    isSavingPath: Boolean,
    canSave: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onUseDefault: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = pathDraft,
            onValueChange = onDraftChange,
            label = { Text(strings.settingsDownloadCustomPath) },
            placeholder = { Text(strings.settingsDownloadCustomPathPlaceholder) },
            modifier = Modifier.weight(1f).testTag("download-storage-input"),
            singleLine = true,
            enabled = !isSavingPath,
            isError = pathError != null,
        )
        OutlinedButton(
            onClick = {
                chooseStorageDirectory(pathDraft, downloadsDir, strings.settingsDownloadChooseFolder)
                    ?.let { onDraftChange(it.toString()) }
            },
            modifier = Modifier.testTag("download-storage-choose"),
            enabled = !isSavingPath,
        ) {
            Text(strings.settingsDownloadChooseFolder)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSave,
            enabled = !isSavingPath && canSave,
            modifier = Modifier.testTag("download-storage-save"),
        ) { Text(strings.text(if (isSavingPath) UiText.CheckingDownloadPath else UiText.SaveDownloadPath)) }
        if (pathDraft.isNotBlank()) {
            TextButton(
                onClick = onUseDefault,
                modifier = Modifier.testTag("download-storage-default"),
                enabled = !isSavingPath,
            ) {
                Text(strings.settingsDownloadUseDefault)
            }
        }
    }
}

@Composable
private fun DownloadPathStatus(pathError: UiText?, pathSaved: Boolean) {
    val strings = LocalStrings.current
    pathError?.let {
        Text(
            strings.text(it),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("download-storage-error"),
        )
    }
    if (pathSaved) {
        Text(
            strings.text(UiText.DownloadPathSaved),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("download-storage-saved"),
        )
    }
    Text(
        strings.text(UiText.DownloadPathHint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
