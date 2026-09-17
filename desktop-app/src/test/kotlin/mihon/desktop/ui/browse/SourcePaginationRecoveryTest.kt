package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.ExtensionStoreService
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalTestApi::class)
class SourcePaginationRecoveryTest {
    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_PAGINATION_APP")?.let { directory ->
            for (name in listOf(
                "mihon.desktop.ui.browse.BrowseContentViewKt",
                "mihon.desktop.ui.browse.BrowseSourceScreenKt",
            )) {
                val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                check(origin.startsWith(Path.of(directory))) { "Expected packaged $name, got $origin" }
                println("PACKAGED_SOURCE_PAGINATION $name $origin")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `retry appends failed page and pagination ignores unsubmitted query`(editDraft: Boolean) = runComposeUiTest {
        val runtime = DesktopRuntime.forTesting()
        val source = PagedSource()
        try {
            runtime.preferences.update {
                setProperty(ExtensionStoreService.PREF_KEY_REPOSITORIES, "http://127.0.0.1:9/repo")
            }
            runtime.sourceManager.registerBuiltinSource(source)
            setContent { Box(Modifier.requiredSize(1024.dp, 720.dp)) { BrowseContentView(runtime) } }
            waitUntil(timeoutMillis = 15000) {
                onAllNodesWithTag("source-item-${source.id}").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("source-item-${source.id}").performClick()
            waitUntil(timeoutMillis = 5000) { onAllNodesWithTag("manga-grid").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("source-search-input").performTextReplacement("submitted")
            onNodeWithTag("source-search-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("manga-card-/submitted/1/0").fetchSemanticsNodes().isNotEmpty()
            }
            if (editDraft) onNodeWithTag("source-search-input").performTextReplacement("draft")
            onNodeWithTag("next-page-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithText("Page 2 (40)").fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            source.requests.take(2) shouldBe listOf(1 to "submitted", 2 to "submitted")
            onNodeWithTag("next-page-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("source-error-details").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Retry").performClick()
            waitUntil(timeoutMillis = 5000) { source.requests.size >= 4 }
            waitForIdle()
            source.requests.toList() shouldBe
                listOf(1 to "submitted", 2 to "submitted", 3 to "submitted", 3 to "submitted")
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithText("Page 3 (60)").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("source-error-details").assertDoesNotExist()
            // Submitting the draft starts a fresh first page, rather than appending.
            onNodeWithTag("source-search-input").performTextReplacement("fresh")
            onNodeWithTag("source-search-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("manga-card-/fresh/1/0").fetchSemanticsNodes().isNotEmpty()
            }
            source.requests.last() shouldBe (1 to "fresh")
            onNodeWithText("Page 1 (20)").assertExists()
            runtime.library.allMangaSnapshot() shouldBe emptyList()
        } finally {
            runBlocking { runtime.shutdown() }
        }
    }

    private class PagedSource : WindowsCatalogueSource {
        override val id = 9292L
        override val name = "Pagination source"
        override val lang = "en"
        val requests = CopyOnWriteArrayList<Pair<Int, String>>()
        override suspend fun getPopularManga(page: Int) = result(page, "popular")
        override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
            requests.add(page to query)
            if (page == 3 && requests.count { it.first == 3 } == 1) throw IOException("HTTP 503")
            return result(page, query)
        }
        private fun result(page: Int, query: String) = MangasPage(
            (0 until 20).map { SManga(url = "/$query/$page/$it", title = "$query $page-$it") },
            page < 3,
        )
        override suspend fun getMangaDetails(manga: SManga) = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
