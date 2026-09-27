package mihon.desktop.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import mihon.desktop.backup.DesktopBackupOptions
import mihon.desktop.i18n.LocalStrings

enum class BackupOptionsMode {
    Export,
    Restore,
}

@Composable
fun BackupOptionsDialog(
    mode: BackupOptionsMode,
    initialOptions: DesktopBackupOptions = DesktopBackupOptions(),
    onConfirm: (DesktopBackupOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var options by remember(initialOptions) { mutableStateOf(initialOptions) }

    val title = when (mode) {
        BackupOptionsMode.Export -> strings.backupOptionsTitleExport
        BackupOptionsMode.Restore -> strings.backupOptionsTitleRestore
    }
    val confirmText = when (mode) {
        BackupOptionsMode.Export -> strings.backupOptionsConfirmExport
        BackupOptionsMode.Restore -> strings.backupOptionsConfirmRestore
    }

    AlertDialog(
        modifier = Modifier.testTag("backup-options-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.backupOptionsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                BackupOptionItem(
                    label = strings.backupOptionChapterState,
                    checked = options.chapterState,
                    testTag = "backup-option-chapter-state",
                    onCheckedChange = { options = options.copy(chapterState = it) },
                )

                BackupOptionItem(
                    label = strings.backupOptionCategories,
                    checked = options.categories,
                    testTag = "backup-option-categories",
                    onCheckedChange = { options = options.copy(categories = it) },
                )

                BackupOptionItem(
                    label = strings.backupOptionTracking,
                    checked = options.tracking,
                    testTag = "backup-option-tracking",
                    onCheckedChange = { options = options.copy(tracking = it) },
                )

                BackupOptionItem(
                    label = strings.backupOptionHistory,
                    checked = options.history,
                    testTag = "backup-option-history",
                    onCheckedChange = { options = options.copy(history = it) },
                )

                BackupOptionItem(
                    label = strings.backupOptionReadProgress,
                    checked = options.readProgress,
                    testTag = "backup-option-read-progress",
                    onCheckedChange = { options = options.copy(readProgress = it) },
                )

                BackupOptionItem(
                    label = strings.backupOptionSettings,
                    checked = options.settings,
                    testTag = "backup-option-settings",
                    onCheckedChange = { options = options.copy(settings = it) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(options) },
                enabled = options.hasAnySelected(),
                modifier = Modifier.testTag("backup-options-confirm"),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("backup-options-cancel"),
            ) {
                Text(strings.dialogCancel)
            }
        },
    )
}

@Composable
private fun BackupOptionItem(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
