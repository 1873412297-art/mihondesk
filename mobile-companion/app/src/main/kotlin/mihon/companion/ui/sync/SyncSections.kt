package mihon.companion.ui.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mihon.companion.R
import mihon.companion.ui.components.SectionLabel
import mihon.companion.ui.relativeTime

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) { Column { content() } }
}

@Composable
internal fun AccountSection(state: SyncUiState, onAction: (SyncAction) -> Unit) {
    val status = state.status
    SectionLabel(stringResource(R.string.sync_section_account))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                status.accountEmail?.let { stringResource(R.string.sync_signed_in_as, it) }
                    ?: stringResource(R.string.sync_signed_out),
                style = MaterialTheme.typography.titleSmall,
            )
            if (status.signedIn) StatusLine(state)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.signedIn) {
                    Button(onClick = {
                        onAction(SyncAction.SyncNow)
                    }, enabled = !status.running, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.sync_now))
                    }
                    TextButton(onClick = {
                        onAction(SyncAction.SignOut)
                    }) { Text(stringResource(R.string.sync_sign_out)) }
                } else {
                    Button(onClick = { onAction(SyncAction.SignIn) }, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.sync_sign_in))
                    }
                }
            }
        }
        if (status.signedIn) {
            SettingRow(
                title = stringResource(R.string.sync_auto),
                body = stringResource(R.string.sync_auto_desc),
                onClick = { onAction(SyncAction.SetAutoSync(!status.autoSync)) },
                trailing = {
                    Switch(checked = status.autoSync, onCheckedChange = { onAction(SyncAction.SetAutoSync(it)) })
                },
            )
        }
    }
    Text(
        stringResource(R.string.sync_drive_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatusLine(state: SyncUiState) {
    val status = state.status
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (status.running) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.sync_running), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        return
    }
    val last = if (status.lastSyncAt > 0) {
        stringResource(R.string.sync_last, relativeTime(status.lastSyncAt))
    } else {
        stringResource(R.string.sync_never)
    }
    Text(last, style = MaterialTheme.typography.bodySmall, color = muted)
    status.lastReport?.takeIf { it.success }?.let { report ->
        Text(
            stringResource(R.string.sync_result, report.pulledChangesetCount, report.mangaInserted, report.mangaMerged),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
    if (state.pendingChanges > 0) {
        Text(
            stringResource(R.string.library_pending, state.pendingChanges),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
    status.lastError?.let {
        Text(
            stringResource(R.string.sync_error, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun MihonSection(enabled: Boolean, onImport: () -> Unit, onExport: () -> Unit) {
    SectionLabel(stringResource(R.string.sync_section_mihon), Modifier.padding(top = 8.dp))
    Card {
        SettingRow(
            stringResource(R.string.mihon_import),
            stringResource(R.string.mihon_import_desc),
            Icons.Outlined.FileDownload,
            enabled,
            onImport,
        )
        SettingRow(
            stringResource(R.string.mihon_export),
            stringResource(R.string.mihon_export_desc),
            Icons.Outlined.FileUpload,
            enabled,
            onExport,
        )
    }
}

@Composable
internal fun AdvancedSection(deviceId: String, onRepull: () -> Unit) {
    SectionLabel(stringResource(R.string.sync_section_advanced), Modifier.padding(top = 16.dp))
    Card {
        SettingRow(
            stringResource(R.string.sync_repull),
            stringResource(R.string.sync_repull_desc),
            Icons.Outlined.CloudDownload,
            onClick = onRepull,
        )
    }
    Text(
        stringResource(R.string.device_id, deviceId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    body: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(
            enabled = enabled,
            onClick = onClick,
        ).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
