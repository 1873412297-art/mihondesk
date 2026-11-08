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
internal fun MigrateMangaDialog(
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
