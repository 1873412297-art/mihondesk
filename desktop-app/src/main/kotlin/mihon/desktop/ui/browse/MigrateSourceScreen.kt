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

@Composable
private fun BatchMigrateDialog(
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
private fun BatchMigrationReportDialog(
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

@Composable
private fun MigrateMangaDialog(
    manga: LibraryManga,
    availableTargetSources: List<SourceDescriptor>,
    onDismiss: () -> Unit,
    onSearchTargetSource: suspend (sourceId: Long, query: String) -> List<SManga>,
    onAutoMatchTargetSource: (
        suspend (
            sourceId: Long,
            manga: LibraryManga,
        ) -> MigrationMatcher.MatchEvaluation
    )? = null,
    onConfirmMigration: (targetSource: SourceDescriptor, targetManga: SManga) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedTargetSource by remember {
        mutableStateOf(availableTargetSources.firstOrNull())
    }
    var query by remember { mutableStateOf(manga.title) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SManga>>(emptyList()) }
    var evaluation by remember { mutableStateOf<MigrationMatcher.MatchEvaluation?>(null) }
    var selectedCandidate by remember { mutableStateOf<SManga?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun triggerSearch(targetId: Long, searchTitle: String, isAutoMatch: Boolean) {
        searchJob?.cancel()
        isSearching = true
        selectedCandidate = null
        searchJob = scope.launch {
            try {
                if (isAutoMatch && onAutoMatchTargetSource != null) {
                    val eval = onAutoMatchTargetSource(targetId, manga)
                    evaluation = eval
                    searchResults = eval.candidates.map { it.manga }
                    if (eval.isUniqueHighConfidence) {
                        selectedCandidate = eval.bestMatch?.manga
                    }
                } else {
                    val results = onSearchTargetSource(targetId, searchTitle)
                    searchResults = results
                    val eval = MigrationMatcher.evaluateCandidates(manga.title, results)
                    evaluation = eval
                    if (eval.isUniqueHighConfidence) {
                        selectedCandidate = eval.bestMatch?.manga
                    }
                }
            } catch (_: Exception) {
                searchResults = emptyList()
                evaluation = null
            } finally {
                isSearching = false
            }
        }
    }

    // Auto-match when target source changes
    LaunchedEffect(selectedTargetSource) {
        val target = selectedTargetSource ?: return@LaunchedEffect
        triggerSearch(target.id, query, isAutoMatch = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.migrateDialogTitle(manga.title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(440.dp).testTag("migrate-manga-dialog")) {
                Text(
                    text = strings.migrateDialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Source selector
                if (availableTargetSources.isEmpty()) {
                    Text(strings.migrateNoOtherSources, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.migrateTargetSourceLabel, style = MaterialTheme.typography.bodyMedium)
                        availableTargetSources.forEach { source ->
                            OutlinedButton(
                                onClick = {
                                    selectedTargetSource = source
                                    selectedCandidate = null
                                },
                                enabled = selectedTargetSource?.id != source.id,
                            ) {
                                Text(source.name)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search query, trigger and auto-match
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).testTag("migrate-query-input"),
                        singleLine = true,
                        placeholder = { Text(strings.migrateSearchPlaceholder) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val target = selectedTargetSource ?: return@Button
                            triggerSearch(target.id, query, isAutoMatch = false)
                        },
                        enabled = !isSearching && selectedTargetSource != null,
                        modifier = Modifier.testTag("migrate-search-submit"),
                    ) {
                        Text(strings.browseSearchButton)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            val target = selectedTargetSource ?: return@OutlinedButton
                            query = manga.title
                            triggerSearch(target.id, manga.title, isAutoMatch = true)
                        },
                        enabled = !isSearching && selectedTargetSource != null,
                        modifier = Modifier.testTag("migrate-auto-match-btn"),
                    ) {
                        Text(strings.migrateAutoMatchButton)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(searchResults) { index, candidate ->
                            val isSelected = selectedCandidate?.url == candidate.url
                            val scoredCandidate = evaluation?.candidates?.find { it.manga.url == candidate.url }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCandidate = candidate }
                                    .padding(8.dp)
                                    .testTag("migrate-candidate-item-$index"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    MangaCover(
                                        thumbnailUrl = candidate.thumbnailUrl,
                                        contentDescription = candidate.title,
                                        modifier = Modifier.width(36.dp).height(50.dp),
                                        headers = coverHeaders(selectedTargetSource?.baseUrl, candidate.thumbnailUrl),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (scoredCandidate != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (scoredCandidate.isExactMatch) {
                                                    Text(
                                                        text = strings.migrateExactMatchBadge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                } else if (scoredCandidate.isHighConfidence) {
                                                    Text(
                                                        text = strings.migrateHighConfidenceBadge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Text(
                                        text = strings.migrateSelectedBadge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetSource = selectedTargetSource ?: return@Button
                    val candidate = selectedCandidate ?: return@Button
                    onConfirmMigration(targetSource, candidate)
                },
                enabled = selectedTargetSource != null && selectedCandidate != null,
                modifier = Modifier.testTag("confirm-migration-btn"),
            ) {
                Text(strings.migrateConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogCancel)
            }
        },
    )
}
