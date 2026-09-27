package mihon.desktop.history

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopHistoryServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private lateinit var repo: mihon.desktop.library.db.SqlDelightLibraryRepository
    private lateinit var service: DesktopHistoryService

    private var mangaId1 = 0L
    private var mangaId2 = 0L
    private var chapterId1 = 0L
    private var chapterId2 = 0L

    @BeforeEach
    fun setUp() {
        repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("history-test.db"))

        mangaId1 = repo.insertManga(
            MangaRecord(
                id = 0L,
                sourceId = 1L,
                url = "/manga/one",
                title = "One Piece",
            ),
        )
        chapterId1 = repo.insertChapter(
            ChapterRecord(
                id = 0L,
                mangaId = mangaId1,
                url = "/chapter/1",
                name = "Chapter 1",
            ),
        )

        mangaId2 = repo.insertManga(
            MangaRecord(
                id = 0L,
                sourceId = 1L,
                url = "/manga/two",
                title = "Bleach",
            ),
        )
        chapterId2 = repo.insertChapter(
            ChapterRecord(
                id = 0L,
                mangaId = mangaId2,
                url = "/chapter/2",
                name = "Chapter 2",
            ),
        )

        val now = System.currentTimeMillis()
        repo.upsertHistory(
            HistoryRecord(
                chapterId = chapterId1,
                lastRead = now - 1000,
                readDuration = 60,
            ),
        )
        repo.upsertHistory(
            HistoryRecord(
                chapterId = chapterId2,
                lastRead = now,
                readDuration = 120,
            ),
        )

        service = DesktopHistoryService(repo, repo, scope)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        repo.close()
    }

    private suspend fun awaitState(predicate: (HistoryUiState) -> Boolean): HistoryUiState {
        return withTimeout(5000L) {
            while (true) {
                val current = service.state.value
                if (predicate(current)) return@withTimeout current
                delay(20)
            }
            error("Unreachable")
        }
    }

    @Test
    fun `loads initial history items and groups them`() = runBlocking {
        val state = awaitState { !it.isLoading && it.rawCount == 2 }
        state.rawCount shouldBe 2
        state.groups.isNotEmpty() shouldBe true
    }

    @Test
    fun `setQuery filters history items by search query`() = runBlocking {
        awaitState { !it.isLoading && it.rawCount == 2 }

        service.setQuery("Bleach")
        val state = awaitState { it.query == "Bleach" && it.rawCount == 1 }
        state.rawCount shouldBe 1

        service.setQuery("NonExistent")
        val emptyState = awaitState { it.query == "NonExistent" && it.rawCount == 0 }
        emptyState.rawCount shouldBe 0
    }

    @Test
    fun `deleteItem deletes single history record`() = runBlocking {
        awaitState { !it.isLoading && it.rawCount == 2 }

        service.deleteItem(chapterId1)
        val state = awaitState { it.rawCount == 1 }
        state.rawCount shouldBe 1
    }

    @Test
    fun `clearAll removes all history records`() = runBlocking {
        awaitState { !it.isLoading && it.rawCount == 2 }

        service.clearAll()
        val state = awaitState { it.rawCount == 0 }
        state.rawCount shouldBe 0
        state.groups shouldHaveSize 0
    }

    @Test
    fun `restoreItem restores previously deleted history item`() = runBlocking {
        val initial = awaitState { !it.isLoading && it.rawCount == 2 }
        val itemToRestore = initial.groups.flatMap { it.items }.first { it.chapterId == chapterId1 }

        service.deleteItem(chapterId1)
        awaitState { it.rawCount == 1 }

        service.restoreItem(itemToRestore)
        val restored = awaitState { it.rawCount == 2 }
        restored.rawCount shouldBe 2
    }
}
