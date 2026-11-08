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

data class SourceWithMangaCount(
    val sourceId: Long,
    val sourceName: String,
    val mangaCount: Int,
)

@Composable
fun MigrateSourceScreen(
    sourcesWithCounts: List<SourceWithMangaCount>,
    selectedSource: SourceWithMangaCount?,
    mangasForSelectedSource: List<LibraryManga>,
    availableTargetSources: List<SourceDescriptor>,
    onSelectSource: (SourceWithMangaCount) -> Unit,
    onBackToSourceList: () -> Unit,
    onSearchTargetSource: suspend (sourceId: Long, query: String) -> List<SManga>,
    onAutoMatchTargetSource: (
        suspend (
            sourceId: Long,
            manga: LibraryManga,
        ) -> MigrationMatcher.MatchEvaluation
    )? = null,
    onPerformMigration: (oldManga: LibraryManga, targetSource: SourceDescriptor, targetManga: SManga) -> Unit,
    batchMigrationState: BatchMigrationState = BatchMigrationState.Idle,
    onStartBatchMigration: ((targetSource: SourceDescriptor) -> Unit)? = null,
    onCancelBatchMigration: (() -> Unit)? = null,
    onDismissBatchReport: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    var activeMigrateManga by remember { mutableStateOf<LibraryManga?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }

    val targetSourcesForSelected = remember(availableTargetSources, selectedSource) {
        if (selectedSource == null) {
            emptyList()
        } else {
            availableTargetSources.filter { it.id != selectedSource.sourceId }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("migrate-source-screen"),
    ) {
        if (selectedSource == null) {
            // Level 1: List sources that have manga in library
            Text(
                text = strings.migrateSelectSourceHeader,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (sourcesWithCounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.migrateNoMangaInLibrary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("migration-sources-list")) {
                    items(sourcesWithCounts, key = { it.sourceId }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSource(item) }
                                .padding(16.dp)
                                .testTag("migrate-source-item-${item.sourceId}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = item.sourceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = strings.migrateMangaCount(item.mangaCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            OutlinedButton(onClick = { onSelectSource(item) }) {
                                Text(strings.migrateViewManga)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
            // Level 2: List manga belonging to this source
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onBackToSourceList, modifier = Modifier.testTag("migrate-back-btn")) {
                        Text(strings.migrateBackToSources)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = selectedSource.sourceName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = strings.migrateSelectMangaHeader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                // Batch Migration Trigger Button
                Button(
                    onClick = { showBatchDialog = true },
                    enabled = mangasForSelectedSource.isNotEmpty() &&
                        targetSourcesForSelected.isNotEmpty() &&
                        batchMigrationState !is BatchMigrationState.Running,
                    modifier = Modifier.testTag("migrate-all-btn"),
                ) {
                    Text(strings.migrateAllButton)
                }
            }

            // Running progress card if batch migration is in progress
            if (batchMigrationState is BatchMigrationState.Running) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("batch-migration-progress-card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.migrateBatchProgressCardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(
                                onClick = { onCancelBatchMigration?.invoke() },
                                modifier = Modifier.testTag("batch-migration-cancel-btn"),
                            ) {
                                Text(strings.migrateBatchCancel)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.migrateBatchProgress(
                                current = batchMigrationState.currentIndex + 1,
                                total = batchMigrationState.totalCount,
                                title = batchMigrationState.currentManga.title,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { batchMigrationState.progress },
                            modifier = Modifier.fillMaxWidth().testTag("batch-migration-progress-bar"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.migrateBatchCounts(
                                success = batchMigrationState.successCount,
                                failed = batchMigrationState.failureCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            val selectedBaseUrl = remember(availableTargetSources, selectedSource) {
                availableTargetSources.find { it.id == selectedSource.sourceId }?.baseUrl
            }
            val migrateCoverHeaders = remember(selectedBaseUrl) {
                coverHeaders(selectedBaseUrl, null)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().testTag("migration-manga-list")) {
                items(mangasForSelectedSource, key = { it.id }) { manga ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeMigrateManga = manga }
                            .padding(12.dp)
                            .testTag("migrate-manga-item-${manga.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            MangaCover(
                                thumbnailUrl = manga.thumbnailUrl,
                                contentDescription = manga.title,
                                modifier = Modifier.width(48.dp).height(68.dp),
                                headers = migrateCoverHeaders,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = manga.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = manga.author ?: strings.mangaDetailStatusUnknown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Button(
                            onClick = { activeMigrateManga = manga },
                            modifier = Modifier.testTag("migrate-action-btn-${manga.id}"),
                        ) {
                            Text(strings.migrateAction)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // Batch Migration Dialog
    if (showBatchDialog && selectedSource != null) {
        BatchMigrateDialog(
            sourceName = selectedSource.sourceName,
            mangaCount = mangasForSelectedSource.size,
            availableTargetSources = targetSourcesForSelected,
            onDismiss = { showBatchDialog = false },
            onConfirm = { targetSource ->
                showBatchDialog = false
                onStartBatchMigration?.invoke(targetSource)
            },
        )
    }

    // Batch Migration Report Dialog (failure review)
    if (batchMigrationState is BatchMigrationState.Completed && batchMigrationState.failures.isNotEmpty()) {
        BatchMigrationReportDialog(
            report = batchMigrationState,
            onDismiss = { onDismissBatchReport?.invoke() },
            onManualMigrate = { failedManga ->
                activeMigrateManga = failedManga
                onDismissBatchReport?.invoke()
            },
        )
    }

    // Single Manga Migration Dialog
    activeMigrateManga?.let { mangaToMigrate ->
        MigrateMangaDialog(
            manga = mangaToMigrate,
            availableTargetSources = availableTargetSources.filter { it.id != mangaToMigrate.sourceId },
            onDismiss = { activeMigrateManga = null },
            onSearchTargetSource = onSearchTargetSource,
            onAutoMatchTargetSource = onAutoMatchTargetSource,
            onConfirmMigration = { targetSource, targetManga ->
                onPerformMigration(mangaToMigrate, targetSource, targetManga)
                activeMigrateManga = null
            },
        )
    }
}
