package mihon.desktop.ui.browse

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GlobalSearchLifecycleTest {
    @Test
    fun `late search cannot overwrite new results and closing clears loading`(@TempDir dir: Path) = runBlocking {
        val startedOld = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val finishedOld = CompletableDeferred<Unit>()
        val startedClosing = CompletableDeferred<Unit>()
        val releaseClosing = CompletableDeferred<Unit>()
        val source = object : WindowsCatalogueSource {
            override val id = 4242L
            override val name = "Controlled source"
            override val lang = "en"
            override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
                if (query == "old") {
                    withContext(NonCancellable) {
                        startedOld.complete(Unit)
                        releaseOld.await()
                        finishedOld.complete(Unit)
                    }
                }
                if (query == "closing") {
                    startedClosing.complete(Unit)
                    releaseClosing.await()
                }
                return MangasPage(listOf(SManga(url = "/$query", title = query)), false)
            }
            override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
            override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
            override suspend fun getMangaDetails(manga: SManga) = manga
            override suspend fun getChapterList(manga: SManga) = emptyList<SChapter>()
            override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
        }
        withPresenter(dir, source) { presenter, owner ->
            try {
                withTimeout(5_000) {
                    presenter.openGlobalSearch()
                    presenter.setGlobalSearchQuery("old")
                    presenter.performGlobalSearch()
                    startedOld.await()
                    val oldJobs = owner.children.toList()
                    presenter.setGlobalSearchQuery("new")
                    presenter.performGlobalSearch()
                    presenter.state.first {
                        !it.isGlobalSearching && it.globalSearchResults.single().mangas.singleOrNull()?.title == "new"
                    }
                    releaseOld.complete(Unit)
                    finishedOld.await()
                    oldJobs.forEach { it.join() }
                    presenter.state.value.globalSearchResults.single().mangas.single().title shouldBe "new"
                    presenter.setGlobalSearchQuery("closing")
                    presenter.performGlobalSearch()
                    startedClosing.await()
                    presenter.closeGlobalSearch()
                    presenter.state.value.isGlobalSearching shouldBe false
                    presenter.state.value.globalSearchResults.any { it.isLoading } shouldBe false
                }
            } finally {
                releaseOld.complete(Unit)
                releaseClosing.complete(Unit)
            }
        }
    }

    @Test
    fun `search with no enabled sources never leaves the progress indicator running`(@TempDir dir: Path) = runBlocking {
        withPresenter(dir, null) { presenter, _ ->
            presenter.setGlobalSearchQuery("query")
            presenter.performGlobalSearch()
            presenter.state.value.isGlobalSearching shouldBe false
            presenter.state.value.globalSearchResults shouldBe emptyList()
        }
    }

    private suspend fun withPresenter(
        dir: Path,
        source: WindowsCatalogueSource?,
        block: suspend (BrowsePresenter, kotlinx.coroutines.Job) -> Unit,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        DesktopLibraryDatabaseFactory.open(dir.resolve("library.db")).use { repository ->
            val preferences = DesktopPreferenceStore(dir.resolve("preferences.properties"))
            val installer = DesktopExtensionInstaller(dir.resolve("extensions").toFile(), preferences)
            DesktopSourceManager(
                installer,
                preferenceStore = preferences,
                libraryRepository = repository,
            ).use { manager ->
                manager.getSources().forEach { manager.setSourceEnabled(it.id, false) }
                source?.let(manager::registerBuiltinSource)
                val presenter = BrowsePresenter(
                    manager,
                    installer,
                    ExtensionStoreService(preferences),
                    repository,
                    preferences,
                    scope,
                )
                try {
                    withTimeout(5_000) {
                        presenter.state.first { !it.isLoading && it.sources.size == if (source == null) 0 else 1 }
                    }
                    block(presenter, job)
                } finally {
                    job.cancelAndJoin()
                }
            }
        }
    }
}
