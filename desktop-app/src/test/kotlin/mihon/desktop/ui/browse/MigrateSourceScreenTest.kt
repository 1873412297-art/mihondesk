package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.browse.migration.BatchMigrationFailure
import mihon.desktop.ui.browse.migration.BatchMigrationFailureReason
import mihon.desktop.ui.browse.migration.BatchMigrationState
import mihon.desktop.ui.browse.migration.MigrationMatcher
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class MigrateSourceScreenTest {

    private val source1 = SourceWithMangaCount(sourceId = 100L, sourceName = "Source A", mangaCount = 2)
    private val manga1 = LibraryManga(
        id = 1L,
        sourceId = 100L,
        url = "/manga/1",
        title = "One Piece",
        thumbnailUrl = null,
        chapterCount = 10L,
        unreadCount = 2L,
    )
    private val targetSource = SourceDescriptor(
        id = 200L,
        name = "Source B",
        lang = "en",
        className = "SourceB",
        baseUrl = "https://sourceb.com",
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `level 1 renders sources list with manga count`() = runComposeUiTest {
        var selectedSource: SourceWithMangaCount? = null

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                MigrateSourceScreen(
                    sourcesWithCounts = listOf(source1),
                    selectedSource = null,
                    mangasForSelectedSource = emptyList(),
                    availableTargetSources = listOf(targetSource),
                    onSelectSource = { selectedSource = it },
                    onBackToSourceList = {},
                    onSearchTargetSource = { _, _ -> emptyList() },
                    onPerformMigration = { _, _, _ -> },
                )
            }
        }

        onNodeWithTag("migration-sources-list").assertIsDisplayed()
        onNodeWithTag("migrate-source-item-100").assertIsDisplayed().performClick()
        selectedSource?.sourceId shouldBe 100L
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `level 2 renders manga list and opens batch migration dialog`() = runComposeUiTest {
        var startedBatch = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                MigrateSourceScreen(
                    sourcesWithCounts = listOf(source1),
                    selectedSource = source1,
                    mangasForSelectedSource = listOf(manga1),
                    availableTargetSources = listOf(targetSource),
                    onSelectSource = {},
                    onBackToSourceList = {},
                    onSearchTargetSource = { _, _ -> emptyList() },
                    onPerformMigration = { _, _, _ -> },
                    onStartBatchMigration = { startedBatch = true },
                )
            }
        }

        onNodeWithTag("migration-manga-list").assertIsDisplayed()
        onNodeWithTag("migrate-all-btn").assertIsDisplayed().performClick()

        // Batch migration dialog is displayed
        onNodeWithTag("batch-migrate-dialog").assertIsDisplayed()
        onNodeWithTag("confirm-batch-migration-btn").assertIsDisplayed().performClick()
        startedBatch shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows batch migration progress card when running`() = runComposeUiTest {
        var cancelled = false

        val runningState = BatchMigrationState.Running(
            targetSource = targetSource,
            currentIndex = 0,
            totalCount = 2,
            currentManga = manga1,
            successCount = 0,
            failureCount = 0,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                MigrateSourceScreen(
                    sourcesWithCounts = listOf(source1),
                    selectedSource = source1,
                    mangasForSelectedSource = listOf(manga1),
                    availableTargetSources = listOf(targetSource),
                    onSelectSource = {},
                    onBackToSourceList = {},
                    onSearchTargetSource = { _, _ -> emptyList() },
                    onPerformMigration = { _, _, _ -> },
                    batchMigrationState = runningState,
                    onCancelBatchMigration = { cancelled = true },
                )
            }
        }

        onNodeWithTag("batch-migration-progress-card").assertIsDisplayed()
        onNodeWithTag("batch-migration-progress-bar").assertIsDisplayed()
        onNodeWithTag("batch-migration-cancel-btn").assertIsDisplayed().performClick()
        cancelled shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows batch migration report dialog when completed with failures`() = runComposeUiTest {
        var dismissed = false

        val completedState = BatchMigrationState.Completed(
            targetSource = targetSource,
            totalCount = 2,
            successCount = 1,
            failures = listOf(
                BatchMigrationFailure(
                    manga = manga1,
                    reason = BatchMigrationFailureReason.NoMatchFound,
                ),
            ),
            wasCancelled = false,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                MigrateSourceScreen(
                    sourcesWithCounts = listOf(source1),
                    selectedSource = source1,
                    mangasForSelectedSource = listOf(manga1),
                    availableTargetSources = listOf(targetSource),
                    onSelectSource = {},
                    onBackToSourceList = {},
                    onSearchTargetSource = { _, _ -> emptyList() },
                    onPerformMigration = { _, _, _ -> },
                    batchMigrationState = completedState,
                    onDismissBatchReport = { dismissed = true },
                )
            }
        }

        onNodeWithTag("batch-migration-report-dialog").assertIsDisplayed()
        onNodeWithTag("batch-report-failure-item-1").assertIsDisplayed()
        onNodeWithTag("close-batch-report-btn").assertIsDisplayed().performClick()
        dismissed shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `single migration dialog auto matches and displays candidate badges`() = runComposeUiTest {
        val targetCandidate = SManga(
            title = "One Piece",
            url = "/target/1",
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                MigrateSourceScreen(
                    sourcesWithCounts = listOf(source1),
                    selectedSource = source1,
                    mangasForSelectedSource = listOf(manga1),
                    availableTargetSources = listOf(targetSource),
                    onSelectSource = {},
                    onBackToSourceList = {},
                    onSearchTargetSource = { _, _ -> listOf(targetCandidate) },
                    onAutoMatchTargetSource = { _, manga ->
                        MigrationMatcher.evaluateCandidates(manga.title, listOf(targetCandidate))
                    },
                    onPerformMigration = { _, _, _ -> },
                )
            }
        }

        // Click single manga migrate button
        onNodeWithTag("migrate-action-btn-1").assertIsDisplayed().performClick()

        // Single dialog displays
        onNodeWithTag("migrate-manga-dialog").assertIsDisplayed()
        onNodeWithTag("migrate-auto-match-btn").assertIsDisplayed()
        onNodeWithTag("confirm-migration-btn").assertIsDisplayed()
    }
}
