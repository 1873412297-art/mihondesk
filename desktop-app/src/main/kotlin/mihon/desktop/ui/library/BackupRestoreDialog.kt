package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.backup.BackupImportStage
import mihon.desktop.ui.ImportStateDialog

@Composable
internal fun BackupRestoreDialog(state: BackupRestoreState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    when (state) {
        BackupRestoreState.Idle -> Unit
        is BackupRestoreState.Running -> AlertDialog(
            modifier = Modifier.testTag("backup-restore-dialog"),
            onDismissRequest = { if (state.canCancel) onCancel() },
            title = { Text(strings.importDialogTitle) },
            confirmButton = {
                TextButton(
                    onClick = onCancel,
                    enabled = state.canCancel,
                    modifier = Modifier.testTag("backup-restore-cancel"),
                ) { Text(strings.dialogCancel) }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = state.progress
                    val status = when {
                        state.cancelling -> strings.text(UiText.RestoreCancelling)
                        progress.stage == BackupImportStage.READING -> strings.text(UiText.RestoreReading)
                        progress.stage == BackupImportStage.VALIDATING -> strings.text(UiText.RestoreValidating)
                        progress.stage == BackupImportStage.COMMITTING -> strings.text(UiText.RestoreCommitting)
                        else -> strings.text(UiText.RestoreManga, progress.completedManga, progress.totalManga)
                    }
                    Text(status, modifier = Modifier.testTag("backup-restore-progress"))
                    if (progress.stage == BackupImportStage.RESTORING && progress.totalManga > 0 && !state.cancelling) {
                        LinearProgressIndicator(
                            progress = { (progress.completedManga.toFloat() / progress.totalManga).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.canCancel) Text(strings.text(UiText.RestoreCancelHint))
                }
            },
        )
        BackupRestoreState.Cancelled -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.text(UiText.RestoreCancelledTitle)) },
            text = { Text(strings.text(UiText.RestoreCancelledMessage)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
        )
        is BackupRestoreState.Finished -> when (state.outcome) {
            is ImportActionState.Failed, is ImportActionState.Rejected -> AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.importDialogFailedTitle) },
                text = {
                    val message = if (state.outcome is ImportActionState.Rejected) {
                        UiText.RestoreInvalid
                    } else {
                        UiText.RestoreFailed
                    }
                    Text(strings.text(message))
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(strings.dialogClose) } },
            )
            else -> ImportStateDialog(state.outcome, onDismiss)
        }
    }
}
