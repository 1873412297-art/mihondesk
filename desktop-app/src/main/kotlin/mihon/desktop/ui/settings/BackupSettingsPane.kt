package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences

@Composable
internal fun BackupSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    backupScheduler: mihon.desktop.backup.DesktopBackupScheduler?,
    syncScheduler: mihon.desktop.sync.DesktopSyncScheduler? = null,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val preferences = presenter.preferences.value
    var backupInProgress by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionBackup,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsBackupTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsBackupDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.testTag("settings-export-backup-button"),
                    ) {
                        Text(strings.settingsExportBackupButton)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.testTag("settings-import-backup-button"),
                    ) {
                        Text(strings.settingsImportBackupButton)
                    }
                }
            }
        }

        // Automated Periodic Backups Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.backupAutoTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.backupAutoDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency / Interval
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.backupIntervalOff,
                            6 to strings.backupInterval6Hours,
                            12 to strings.backupInterval12Hours,
                            24 to strings.backupIntervalDaily,
                            48 to strings.backupInterval2Days,
                            168 to strings.backupIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.backupIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = presenter.updatePreferences {
                                            it.copy(backupIntervalHours = hours)
                                        }

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                BackupStorageLocation(presenter, preferences.backupStoragePath) { updated ->
                    onPreferencesChanged?.invoke(updated)
                }

                // Retention Count
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.backupRetention, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 20).forEach { count ->
                            val isSelected = preferences.backupRetentionCount == count
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = presenter.updatePreferences {
                                            it.copy(backupRetentionCount = count)
                                        }

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("backup-retention-$count"),
                                ) {
                                    Text("$count")
                                }
                            }
                        }
                    }
                }

                // Last auto backup info & Trigger Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timestampFormatter = remember(strings) {
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDateTime(
                                java.time.format.FormatStyle.MEDIUM,
                                java.time.format.FormatStyle.SHORT,
                            )
                            .withLocale(strings.locale)
                    }
                    val lastBackupStr = if (preferences.lastAutoBackupEpochMillis > 0L) {
                        java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(preferences.lastAutoBackupEpochMillis),
                            java.time.ZoneId.systemDefault(),
                        ).format(timestampFormatter)
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.backupLastAutoBackup} $lastBackupStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (backupScheduler != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    backupInProgress = true
                                    try {
                                        val path = backupScheduler.performBackup(isManual = false)
                                        presenter.reloadPreferences()
                                        backupMessage = strings.backupExportSuccess(path.toString())
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        backupMessage = strings.text(UiText.BackupFailed, e.message.orEmpty())
                                    } finally {
                                        backupInProgress = false
                                    }
                                }
                            },
                            enabled = !backupInProgress,
                            modifier = Modifier.testTag("backup-now-button"),
                        ) {
                            Text(strings.backupNow)
                        }
                    }
                }

                backupMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        syncScheduler?.googleDrive?.let { GoogleDriveSyncCard(it) }
    }
}
