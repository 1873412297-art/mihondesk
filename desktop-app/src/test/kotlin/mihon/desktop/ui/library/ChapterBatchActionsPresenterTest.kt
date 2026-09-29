package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ChapterBatchActionsPresenterTest {
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `batchBookmarkChapters updates bookmark status across selected chapters off UI thread`() = runBlocking {
        val repository = TestLibraryRepository()
        val mangaId = 1L
        repository.addManga(
            mangaRecord(mangaId, "Test Manga"),
            chapters = listOf(
                chapterRecord(id = 101, mangaId = mangaId, name = "Ch 1", number = 1.0, bookmark = false),
                chapterRecord(id = 102, mangaId = mangaId, name = "Ch 2", number = 2.0, bookmark = false),
                chapterRecord(id = 103, mangaId = mangaId, name = "Ch 3", number = 3.0, bookmark = true),
            ),
        )

        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { !it.loading && it.items.isNotEmpty() }
        presenter.selectManga(mangaId)
        presenter.awaitDetail { it.manga?.id == mangaId && it.allChapters.size == 3 }

        // Bookmark chapters 101 and 102
        presenter.batchBookmarkChapters(setOf(101L, 102L), bookmark = true)

        val updated = presenter.awaitDetail { detail ->
            detail.allChapters.filter { it.bookmark }.map { it.id }.toSet() == setOf(101L, 102L, 103L)
        }
        updated.allChapters.all { it.bookmark } shouldBe true

        val snapshot = repository.chapterSnapshot(mangaId)
        snapshot.find { it.id == 101L }?.bookmark shouldBe true
        snapshot.find { it.id == 102L }?.bookmark shouldBe true
        snapshot.find { it.id == 103L }?.bookmark shouldBe true

        // Now un-bookmark chapters 102 and 103
        presenter.batchBookmarkChapters(setOf(102L, 103L), bookmark = false)

        val cleared = presenter.awaitDetail { detail ->
            detail.allChapters.filter { it.bookmark }.map { it.id }.toSet() == setOf(101L)
        }
        cleared.allChapters.find { it.id == 101L }?.bookmark shouldBe true
        cleared.allChapters.find { it.id == 102L }?.bookmark shouldBe false
        cleared.allChapters.find { it.id == 103L }?.bookmark shouldBe false

        presenter.close()
    }

    @Test
    fun `batchMarkChaptersRead marks chapters read and unread off UI thread`() = runBlocking {
        val repository = TestLibraryRepository()
        val mangaId = 2L
        repository.addManga(
            mangaRecord(mangaId, "Test Manga 2"),
            chapters = listOf(
                chapterRecord(id = 201, mangaId = mangaId, name = "Ch 1", number = 1.0, read = false),
                chapterRecord(id = 202, mangaId = mangaId, name = "Ch 2", number = 2.0, read = false),
                chapterRecord(
                    id = 203,
                    mangaId = mangaId,
                    name = "Ch 3",
                    number = 3.0,
                    read = true,
                    lastPageRead = 15L,
                ),
            ),
        )

        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { !it.loading && it.items.isNotEmpty() }
        presenter.selectManga(mangaId)
        presenter.awaitDetail { it.manga?.id == mangaId && it.allChapters.size == 3 }

        // Mark 201 and 202 as read
        presenter.batchMarkChaptersRead(setOf(201L, 202L), read = true)

        val updated = presenter.awaitDetail { detail ->
            detail.allChapters.filter { it.read }.map { it.id }.toSet() == setOf(201L, 202L, 203L)
        }
        updated.allChapters.all { it.read } shouldBe true

        val snapshot = repository.chapterSnapshot(mangaId)
        snapshot.find { it.id == 201L }?.read shouldBe true
        snapshot.find { it.id == 202L }?.read shouldBe true

        // Mark 202 and 203 as unread (should also reset lastPageRead to 0)
        presenter.batchMarkChaptersRead(setOf(202L, 203L), read = false)

        val unreadState = presenter.awaitDetail { detail ->
            detail.allChapters.filter { it.read }.map { it.id }.toSet() == setOf(201L)
        }
        unreadState.allChapters.find { it.id == 201L }?.read shouldBe true
        unreadState.allChapters.find { it.id == 202L }?.read shouldBe false
        unreadState.allChapters.find { it.id == 203L }?.read shouldBe false

        val unreadSnapshot = repository.chapterSnapshot(mangaId)
        val ch203 = unreadSnapshot.find { it.id == 203L } ?: error("missing chapter 203")
        ch203.read shouldBe false
        ch203.lastPageRead shouldBe 0L

        presenter.close()
    }

    @Test
    fun `markPreviousChaptersRead marks preceding chapters as read off UI thread`() = runBlocking {
        val repository = TestLibraryRepository()
        val mangaId = 3L
        repository.addManga(
            mangaRecord(mangaId, "Test Manga 3"),
            chapters = listOf(
                chapterRecord(id = 301, mangaId = mangaId, name = "Ch 1", number = 1.0, sourceOrder = 2, read = false),
                chapterRecord(id = 302, mangaId = mangaId, name = "Ch 2", number = 2.0, sourceOrder = 1, read = false),
                chapterRecord(id = 303, mangaId = mangaId, name = "Ch 3", number = 3.0, sourceOrder = 0, read = false),
            ),
        )

        val presenter = LibraryPresenter(repository, scope)
        presenter.awaitState { !it.loading && it.items.isNotEmpty() }
        presenter.selectManga(mangaId)
        presenter.awaitDetail { it.manga?.id == mangaId && it.allChapters.size == 3 }

        // Mark previous chapters read relative to Ch 2 (id 302, number 2.0, sourceOrder 1)
        // Chapter 1 (id 301) has higher sourceOrder (2 > 1) and lower chapterNumber (1.0 < 2.0), so it is previous
        presenter.markPreviousChaptersRead(302L)

        val updated = presenter.awaitDetail { detail ->
            detail.allChapters.find { it.id == 301L }?.read == true
        }
        updated.allChapters.find { it.id == 301L }?.read shouldBe true
        updated.allChapters.find { it.id == 302L }?.read shouldBe false
        updated.allChapters.find { it.id == 303L }?.read shouldBe false

        val snapshot = repository.chapterSnapshot(mangaId)
        snapshot.find { it.id == 301L }?.read shouldBe true
        snapshot.find { it.id == 302L }?.read shouldBe false
        snapshot.find { it.id == 303L }?.read shouldBe false

        presenter.close()
    }

    private suspend fun LibraryPresenter.awaitState(predicate: (LibraryUiState) -> Boolean): LibraryUiState =
        withTimeout(5_000) { state.first(predicate) }

    private suspend fun LibraryPresenter.awaitDetail(predicate: (MangaDetailUiState) -> Boolean): MangaDetailUiState =
        withTimeout(5_000) { detailState.first(predicate) }

    private fun mangaRecord(id: Long, title: String) = MangaRecord(
        id = id,
        sourceId = 100L,
        url = "/$id",
        title = title,
        favorite = true,
    )

    private fun chapterRecord(
        id: Long,
        mangaId: Long,
        name: String,
        number: Double,
        sourceOrder: Long = 0L,
        read: Boolean = false,
        lastPageRead: Long = 0L,
        bookmark: Boolean = false,
    ) = ChapterRecord(
        id = id,
        mangaId = mangaId,
        url = "/chapter/$id",
        name = name,
        scanlator = null,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        dateFetch = 0L,
        dateUpload = 0L,
        lastModifiedAt = 0L,
        version = 1L,
    )
}
