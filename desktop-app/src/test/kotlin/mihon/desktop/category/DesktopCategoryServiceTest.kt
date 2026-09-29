package mihon.desktop.category

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopCategoryServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private lateinit var repo: mihon.desktop.library.db.SqlDelightLibraryRepository
    private lateinit var service: DesktopCategoryService

    @BeforeEach
    fun setUp() {
        repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("category-test.db"))
        service = DesktopCategoryService(repo, repo, scope)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        repo.close()
    }

    private suspend fun awaitCategories(predicate: (List<DesktopCategory>) -> Boolean): List<DesktopCategory> {
        return withTimeout(5000L) {
            while (true) {
                val current = service.categories.value
                if (predicate(current)) return@withTimeout current
                delay(20)
            }
            error("Unreachable")
        }
    }

    @Test
    fun `createCategory adds new category and increments sort order`() = runBlocking {
        service.createCategory("Action")
        val categories = awaitCategories { it.any { c -> c.name == "Action" } }
        val action = categories.first { it.name == "Action" }
        action.order shouldBe 0L

        service.createCategory("Comedy")
        val updated = awaitCategories { it.size == 2 }
        val comedy = updated.first { it.name == "Comedy" }
        comedy.order shouldBe 1L
    }

    @Test
    fun `createCategory with blank string is ignored`() = runBlocking {
        service.createCategory("   ")
        delay(100)
        service.categories.value shouldHaveSize 0
    }

    @Test
    fun `renameCategory updates category name`() = runBlocking {
        service.createCategory("OldName")
        val initial = awaitCategories { it.isNotEmpty() }
        val id = initial.first().id

        service.renameCategory(id, "NewName")
        val updated = awaitCategories { it.any { c -> c.name == "NewName" } }
        updated.first { it.id == id }.name shouldBe "NewName"
    }

    @Test
    fun `reorderCategory updates category order`() = runBlocking {
        service.createCategory("Cat1")
        val initial = awaitCategories { it.isNotEmpty() }
        val id = initial.first().id

        service.reorderCategory(id, 42L)
        val updated = awaitCategories { it.any { c -> c.order == 42L } }
        updated.first { it.id == id }.order shouldBe 42L
    }

    @Test
    fun `deleteCategory removes the category`() = runBlocking {
        service.createCategory("ToDelete")
        val initial = awaitCategories { it.isNotEmpty() }
        val id = initial.first().id

        service.deleteCategory(id)
        val updated = awaitCategories { it.isEmpty() }
        updated shouldHaveSize 0
    }

    @Test
    fun `setMangaCategories links manga to categories`() = runBlocking {
        service.createCategory("Shonen")
        val categories = awaitCategories { it.isNotEmpty() }
        val catId = categories.first().id

        val mangaId = repo.insertManga(
            MangaRecord(
                id = 0L,
                sourceId = 1L,
                url = "/manga/1",
                title = "Test Manga",
            ),
        )

        service.setMangaCategories(mangaId, listOf(catId))
        withTimeout(5000L) {
            while (true) {
                val links = repo.mangaCategoryLinksSnapshot()
                if (links[mangaId]?.contains(catId) == true) break
                delay(20)
            }
        }

        val links = repo.mangaCategoryLinksSnapshot()
        links[mangaId] shouldBe listOf(catId)
    }
}
