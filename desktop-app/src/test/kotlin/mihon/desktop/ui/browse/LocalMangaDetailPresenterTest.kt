package mihon.desktop.ui.browse

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.ui.library.ChapterBrowseAction
import mihon.desktop.ui.library.ChapterSortMode
import mihon.desktop.ui.library.ChapterSortState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The presenter runs in backgroundScope, which runCurrent drives and advanceUntilIdle does not. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalMangaDetailPresenterTest {

    @TempDir
    lateinit var tempDir: Path

    private fun withLibrary(
        block: suspend kotlinx.coroutines.test.TestScope.(SqlDelightLibraryRepository, Long) -> Unit,
    ) = runTest {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db")).use { library ->
            val mangaId = library.insertManga(
                MangaRecord(sourceId = SOURCE_ID, url = MANGA_URL, title = "Local manga", favorite = true),
            )
            (1..3).forEach { order ->
                library.insertChapter(
                    ChapterRecord(
                        mangaId = mangaId,
                        url = "/c$order",
                        name = "Chapter $order",
                        sourceOrder = order.toLong(),
                    ),
                )
            }
            block(this@runTest, library, mangaId)
        }
    }

    private fun kotlinx.coroutines.test.TestScope.presenter(library: SqlDelightLibraryRepository) =
        LocalMangaDetailPresenter(
            sourceId = SOURCE_ID,
            mangaUrl = MANGA_URL,
            repository = library,
            mutations = library,
            readerLibrary = library,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `loads the library entry and sorts its chapters`() = withLibrary { library, mangaId ->
        val presenter = presenter(library)
        presenter.load()
        runCurrent()

        val state = presenter.state.value
        state.loading shouldBe false
        state.manga?.id shouldBe mangaId
        state.chapters.map { it.name } shouldContainExactly listOf("Chapter 3", "Chapter 2", "Chapter 1")

        presenter.actions {}.onChapterSortChange(ChapterSortState(ChapterSortMode.SourceOrder, ascending = true))
        runCurrent()
        presenter.state.value.chapters.map { it.name } shouldContainExactly
            listOf("Chapter 1", "Chapter 2", "Chapter 3")
    }

    @Test
    fun `mark previous read writes every earlier chapter and reloads`() = withLibrary { library, mangaId ->
        val presenter = presenter(library)
        presenter.load()
        runCurrent()
        val second = presenter.state.value.allChapters.first { it.name == "Chapter 2" }

        presenter.actions {}.onMarkPreviousRead(second.id)
        runCurrent()

        library.chapterSnapshot(mangaId).filter { it.read }.map { it.name }.toSet() shouldBe
            setOf("Chapter 1", "Chapter 2")
        presenter.state.value.allChapters.count { it.read } shouldBe 2
    }

    @Test
    fun `chapter selection lives in the presenter state`() = withLibrary { library, _ ->
        val presenter = presenter(library)
        presenter.load()
        runCurrent()
        val actions = presenter.actions {}

        actions.onChapterBrowseAction(ChapterBrowseAction.ToggleSelectionMode)
        actions.onChapterBrowseAction(ChapterBrowseAction.SetSelectedChapters(setOf(1L)))
        runCurrent()

        presenter.state.value.chapterBrowse.isSelectionMode shouldBe true
        presenter.state.value.chapterBrowse.selectedChapterIds shouldBe setOf(1L)
    }

    @Test
    fun `a manga that left the library becomes an error state`() = runTest {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("empty.db")).use { library ->
            val presenter = presenter(library)
            presenter.load()
            runCurrent()
            presenter.state.value.loading shouldBe false
            presenter.state.value.errorMessage shouldNotBe null
        }
    }

    private companion object {
        const val SOURCE_ID = 0L
        const val MANGA_URL = "local:manifest"
    }
}
