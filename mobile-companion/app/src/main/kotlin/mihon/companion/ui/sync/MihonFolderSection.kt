package mihon.companion.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mihon.companion.R
import mihon.companion.mihonfolder.MihonFolderStatus
import mihon.companion.mihonfolder.MihonImportOutcome
import mihon.companion.ui.components.SectionLabel
import mihon.companion.ui.relativeTime

@Composable
internal fun MihonFolderSection(status: MihonFolderStatus, enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    SectionLabel(stringResource(R.string.mihon_folder_section), Modifier.padding(top = 8.dp))
    Card {
        SettingRow(
            title = stringResource(if (status.configured) R.string.mihon_folder_change else R.string.mihon_folder_pick),
            body = status.folderName?.let { stringResource(R.string.mihon_folder_current, it) }
                ?: stringResource(R.string.mihon_folder_desc),
            icon = Icons.Outlined.FolderOpen,
            enabled = enabled,
            onClick = onPick,
        )
        if (status.configured) FolderDetails(status, onClear)
    }
    Text(
        stringResource(R.string.mihon_folder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun FolderDetails(status: MihonFolderStatus, onClear: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val last =
            status.lastFile?.let { stringResource(R.string.mihon_folder_last, relativeTime(status.lastReadAt), it) }
                ?: stringResource(R.string.mihon_folder_none_yet)
        Text(last, style = MaterialTheme.typography.bodySmall, color = muted)
        problemText(status.problem)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row {
            TextButton(onClick = onClear) { Text(stringResource(R.string.mihon_folder_clear)) }
        }
    }
}

@Composable
private fun problemText(problem: MihonImportOutcome?): String? = when (problem) {
    MihonImportOutcome.NoBackups -> stringResource(R.string.mihon_folder_no_backups)
    MihonImportOutcome.PermissionLost -> stringResource(R.string.mihon_folder_lost)
    is MihonImportOutcome.Failed -> stringResource(R.string.mihon_folder_error, problem.message)
    else -> null
}
