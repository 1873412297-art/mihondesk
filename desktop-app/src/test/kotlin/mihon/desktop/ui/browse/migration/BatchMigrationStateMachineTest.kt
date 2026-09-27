package mihon.desktop.ui.browse.migration

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.library.model.LibraryManga
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class BatchMigrationStateMachineTest {

    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private val targetSource = SourceDescriptor(
        id = 200L,
        name = "Target Source",
        lang = "en",
        className = "TargetSource",
        baseUrl = "https://example.com",
    )

    @Test
    fun `starts idle and completes when list is empty`() = runBlocking {
        val runner = BatchMigrationRunner(scope, stringsProvider = { EnglishStrings })
        runner.state.value shouldBe BatchMigrationState.Idle

        runner.runBatchMigration(
            mangas = emptyList(),
            targetSource = targetSource,
            searchFn = { emptyList() },
            migrateFn = { _, _ -> },
            delayMs = 0L,
        )

        val completed = runner.state.value as BatchMigrationState.Completed
        completed.totalCount shouldBe 0
        completed.successCount shouldBe 0
        completed.failures shouldHaveSize 0
        completed.wasCancelled shouldBe false
    }

    @Test
    fun `migrates matching manga and records successes`() = runBlocking {
        val runner = BatchMigrationRunner(scope, stringsProvider = { EnglishStrings })
        val manga1 = sampleManga(1L, "One Piece")
        val manga2 = sampleManga(2L, "Frieren")
        val migrated = mutableListOf<String>()

        runner.runBatchMigration(
            mangas = listOf(manga1, manga2),
            targetSource = targetSource,
            searchFn = { query ->
                listOf(sManga(query, "/$query"))
            },
            migrateFn = { old, _ ->
                migrated.add(old.title)
            },
            delayMs = 0L,
        )

        migrated shouldBe listOf("One Piece", "Frieren")
        val completed = runner.state.value as BatchMigrationState.Completed
        completed.totalCount shouldBe 2
        completed.successCount shouldBe 2
        completed.failures shouldHaveSize 0
        completed.isAllSuccess shouldBe true
    }

    @Test
    fun `does not halt on single failures and collects failures report`() = runBlocking {
        val runner = BatchMigrationRunner(scope, stringsProvider = { EnglishStrings })
        val manga1 = sampleManga(1L, "One Piece")
        val manga2 = sampleManga(2L, "Unknown Title")
        val manga3 = sampleManga(3L, "Ambiguous Title")
        val manga4 = sampleManga(4L, "Error Manga")
        val migrated = mutableListOf<String>()

        runner.runBatchMigration(
            mangas = listOf(manga1, manga2, manga3, manga4),
            targetSource = targetSource,
            searchFn = { query ->
                when (query) {
                    "One Piece" -> listOf(sManga("One Piece", "/op"))
                    "Unknown Title" -> emptyList()
                    "Ambiguous Title" -> listOf(sManga("Ambiguous Title", "/a1"), sManga("Ambiguous Title", "/a2"))
                    "Error Manga" -> listOf(sManga("Error Manga", "/err"))
                    else -> emptyList()
                }
            },
            migrateFn = { old, _ ->
                if (old.title == "Error Manga") {
                    throw IllegalStateException("Network timeout")
                }
                migrated.add(old.title)
            },
            delayMs = 0L,
        )

        migrated shouldBe listOf("One Piece")
        val completed = runner.state.value as BatchMigrationState.Completed
        completed.totalCount shouldBe 4
        completed.successCount shouldBe 1
        completed.failures shouldHaveSize 3

        completed.failures[0].manga.id shouldBe 2L
        completed.failures[0].reason shouldBe BatchMigrationFailureReason.NoMatchFound

        completed.failures[1].manga.id shouldBe 3L
        completed.failures[1].reason shouldBe BatchMigrationFailureReason.AmbiguousMatches

        completed.failures[2].manga.id shouldBe 4L
        completed.failures[2].reason shouldBe BatchMigrationFailureReason.MigrationError
        completed.failures[2].detailMessage shouldBe "Network timeout"
    }

    @Test
    fun `cancellation stops running batch and transitions to cancelled completed state`() = runBlocking {
        val runner = BatchMigrationRunner(scope, stringsProvider = { EnglishStrings })
        val manga1 = sampleManga(1L, "One Piece")
        val manga2 = sampleManga(2L, "Bleach")
        val manga3 = sampleManga(3L, "Naruto")
        val migrated = mutableListOf<String>()

        val job = runner.start(
            mangas = listOf(manga1, manga2, manga3),
            targetSource = targetSource,
            searchFn = { query ->
                listOf(sManga(query, "/$query"))
            },
            migrateFn = { old, _ ->
                migrated.add(old.title)
            },
            delayMs = 200L,
        )

        // Wait until first item is processed, then cancel
        var tries = 0
        while (migrated.isEmpty() && tries++ < 50) {
            delay(20)
        }
        runner.cancel()
        job.join()

        val completed = runner.state.value as BatchMigrationState.Completed
        completed.wasCancelled shouldBe true
        completed.successCount shouldBe 1
    }

    @Test
    fun `reset returns state to Idle`() = runBlocking {
        val runner = BatchMigrationRunner(scope, stringsProvider = { EnglishStrings })
        runner.runBatchMigration(
            mangas = listOf(sampleManga(1L, "One Piece")),
            targetSource = targetSource,
            searchFn = { listOf(sManga("One Piece", "/op")) },
            migrateFn = { _, _ -> },
            delayMs = 0L,
        )
        (runner.state.value is BatchMigrationState.Completed) shouldBe true

        runner.reset()
        runner.state.value shouldBe BatchMigrationState.Idle
    }

    private fun sampleManga(id: Long, title: String) = LibraryManga(
        id = id,
        sourceId = 100L,
        url = "/manga/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 10L,
        unreadCount = 2L,
    )

    private fun sManga(title: String, url: String) = SManga(
        title = title,
        url = url,
    )
}
