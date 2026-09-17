package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.ExtensionStoreService
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.jetbrains.skia.Image
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalTestApi::class)
class SourceFilterDraftTest {
    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_FILTER_DRAFT_APP")?.let { directory ->
            for (name in listOf(
                "mihon.desktop.ui.browse.BrowseContentViewKt",
                "mihon.desktop.ui.browse.SourceFilterDialogKt",
                "mihon.extension.ipc.FilterIpcKt",
            )) {
                val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                check(origin.startsWith(Path.of(directory))) { "Expected packaged $name, got $origin" }
                println("PACKAGED_FILTER_DRAFT $name $origin")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `cancel and reset are drafts until applied and source keeps custom filter type`(
        checkFeedback: Boolean,
    ) = runComposeUiTest {
        val runtime = DesktopRuntime.forTesting()
        val source = FilterSource()
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
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("source-filter-btn").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("source-filter-btn").performClick()
            onNodeWithTag("filter-checkbox-Completed").performClick()
            if (checkFeedback) onNodeWithTag("filter-checkbox-Completed").assertIsOn()
            onNodeWithTag("filter-group-checkbox-Action").performClick().assertIsNotSelected()
            System.getenv("MIHON_FILTER_DRAFT_EVIDENCE")?.let { location ->
                val target = Files.createDirectories(Path.of(location))
                Image.makeFromBitmap(
                    onNodeWithTag("source-filter-dialog").captureToImage().asSkiaBitmap(),
                ).use { image ->
                    image.encodeToData()!!.use { png -> Files.write(target.resolve("filter-draft.png"), png.bytes) }
                }
            }
            onNodeWithTag("filter-cancel-btn").performClick()
            onNodeWithTag("source-filter-btn").performClick()
            onNodeWithTag("filter-group-checkbox-Action").assertIsSelected()
            onNodeWithTag("filter-checkbox-Completed").assertIsOff().performClick()
            onNodeWithTag("filter-apply-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("manga-card-/true/1/0").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("next-page-btn").performClick()
            waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Page 2 (40)").fetchSemanticsNodes().isNotEmpty() }
            source.requests.toList() shouldBe listOf(1 to true, 2 to true)
            onNodeWithTag("source-filter-btn").performClick()
            onNodeWithTag("filter-reset-btn").performClick()
            onNodeWithTag("filter-checkbox-Completed").assertIsOff()
            onNodeWithTag("filter-cancel-btn").performClick()
            onNodeWithTag("source-filter-btn").performClick()
            onNodeWithTag("filter-checkbox-Completed").assertIsOn()
            onNodeWithTag("filter-reset-btn").performClick()
            onNodeWithTag("filter-apply-btn").performClick()
            waitUntil(timeoutMillis = 5000) {
                onAllNodesWithTag("manga-card-/false/1/0").fetchSemanticsNodes().isNotEmpty()
            }
            source.requests.toList() shouldBe listOf(1 to true, 2 to true, 1 to false)
            runtime.library.allMangaSnapshot() shouldBe emptyList()
        } finally {
            runBlocking { runtime.shutdown() }
        }
    }

    private class CompletedFilter : Filter.CheckBox("Completed")
    private class ActionFilter : Filter.CheckBox("Action", true)
    private class FilterSource : WindowsCatalogueSource {
        override val id = 9393L
        override val name = "Filter draft source"
        override val lang = "en"
        val requests = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        override fun getFilterList() = FilterList(CompletedFilter(), Filter.Group("Genres", listOf(ActionFilter())))
        override suspend fun getPopularManga(page: Int) = result(page, "popular")
        override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
            val completed = filters.filters.first() as CompletedFilter
            val group = filters.filters[1] as Filter.Group<*>
            (group.state.single() as ActionFilter).state shouldBe true
            requests.add(page to completed.state)
            return result(page, completed.state.toString())
        }
        private fun result(page: Int, label: String) = MangasPage(
            (0 until 20).map { SManga(url = "/$label/$page/$it", title = "$label $page-$it") },
            page < 2,
        )
        override suspend fun getMangaDetails(manga: SManga) = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
