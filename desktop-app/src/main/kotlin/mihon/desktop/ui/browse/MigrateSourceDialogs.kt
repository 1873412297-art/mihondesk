package mihon.desktop.ui.browse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.browse.migration.BatchMigrationFailureReason
import mihon.desktop.ui.browse.migration.BatchMigrationState
import mihon.desktop.ui.browse.migration.MigrationMatcher
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.coverHeaders
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga

@Composable
internal fun BatchMigrateDialog(
    sourceName: String,
    mangaCount: Int,
    availableTargetSources: List<SourceDescriptor>,
    onDismiss: () -> Unit,
    onConfirm: (targetSource: SourceDescriptor) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedTargetSource by remember {
        mutableStateOf(availableTargetSources.firstOrNull())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.migrateAllDialogTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("batch-migrate-dialog")) {
                Text(
                    text = strings.migrateAllDialogSubtitle(mangaCount, sourceName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.migrateTargetSourceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (availableTargetSources.isEmpty()) {
                    Text(strings.migrateNoOtherSources, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableTargetSources.forEach { source ->
                            OutlinedButton(
                                onClick = { selectedTargetSource = source },
                                enabled = selectedTargetSource?.id != source.id,
                                modifier = Modifier.testTag("batch-target-source-${source.id}"),
                            ) {
                                Text(source.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = selectedTargetSource ?: return@Button
                    onConfirm(target)
                },
                enabled = selectedTargetSource != null,
                modifier = Modifier.testTag("confirm-batch-migration-btn"),
            ) {
                Text(strings.migrateConfirm)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel-batch-migration-btn"),
            ) {
                Text(strings.dialogCancel)
            }
        },
    )
}

@Composable
internal fun BatchMigrationReportDialog(
    report: BatchMigrationState.Completed,
    onDismiss: () -> Unit,
    onManualMigrate: (LibraryManga) -> Unit,
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.migrateBatchReportTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .testTag("batch-migration-report-dialog"),
            ) {
                val summaryText = buildString {
                    append(strings.migrateBatchReportSummary(report.successCount, report.failures.size))
                    if (report.wasCancelled) {
                        append(" ")
                        append(strings.migrateBatchCancelled(report.successCount, report.totalCount))
                    }
                }
                Text(text = summaryText, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.migrateBatchFailuresListHeader,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(report.failures) { failure ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("batch-report-failure-item-${failure.manga.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = failure.manga.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val reasonLabel = when (failure.reason) {
                                    BatchMigrationFailureReason.NoMatchFound ->
                                        strings.migrateFailureNoMatch
                                    BatchMigrationFailureReason.AmbiguousMatches ->
                                        strings.migrateFailureAmbiguous
                                    BatchMigrationFailureReason.MigrationError ->
                                        strings.migrateFailureError(failure.detailMessage ?: "Unknown error")
                                }
                                Text(
                                    text = reasonLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            OutlinedButton(
                                onClick = { onManualMigrate(failure.manga) },
                                modifier = Modifier.testTag("batch-report-manual-migrate-${failure.manga.id}"),
                            ) {
                                Text(strings.migrateManualMigrate)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close-batch-report-btn"),
            ) {
                Text(strings.dialogClose)
            }
        },
    )
}
