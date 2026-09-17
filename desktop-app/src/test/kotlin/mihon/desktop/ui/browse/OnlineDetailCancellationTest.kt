package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalTestApi::class)
class OnlineDetailCancellationTest {
    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_DETAIL_APP")?.let { directory ->
            for (name in listOf(
                "mihon.desktop.ui.browse.BrowseContentViewKt",
                "mihon.desktop.ui.library.MangaDetailScreenKt",
                "mihon.desktop.extension.OnlineMangaSyncService",
            )) {
                val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                check(origin.startsWith(Path.of(directory))) { "Expected packaged $name, got $origin" }
                println("PACKAGED_DETAIL_CANCELLATION $name $origin")
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "details,false,false",
        "chapters,false,false",
        "details,true,false",
        "chapters,true,false",
        "chapters,false,true",
    )
    fun `leaving a pending detail never opens it after navigation`(stage: String, late: Boolean, dispose: Boolean) =
        runComposeUiTest {
            val runtime = DesktopRuntime.forTesting()
            val source = DelayedSource(stage, late)
            val visible = mutableStateOf(true)
            val opened = mutableListOf<Long>()
            try {
                runtime.preferences.update {
                    setProperty(ExtensionStoreService.PREF_KEY_REPOSITORIES, "http://127.0.0.1:9/repo")
                }
                runtime.sourceManager.registerBuiltinSource(source)
                setContent {
                    Box(Modifier.requiredSize(1024.dp, 720.dp)) {
                        if (visible.value) BrowseContentView(runtime, onOpenMangaDetail = { opened.add(it) })
                    }
                }
                waitUntil(timeoutMillis = 15000) {
                    onAllNodesWithTag("source-item-${source.id}").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("source-item-${source.id}").performClick()
                waitUntil(timeoutMillis = 15000) {
                    onAllNodesWithTag("manga-card-/slow").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("manga-card-/slow").performClick()
                waitUntil(timeoutMillis = 5000) { source.started.get() }
                onNodeWithTag("manga-detail-loading").assertIsDisplayed()
                if (dispose) {
                    runOnIdle { visible.value = false }
                } else {
                    onNodeWithTag("manga-detail-back").assertIsDisplayed()
                    if (stage == "details") {
                        onNodeWithTag("manga-detail-pane").performKeyInput { pressKey(Key.Escape) }
                    } else {
                        onNodeWithTag("manga-detail-back").performClick()
                    }
                    waitUntil(timeoutMillis = 5000) {
                        onAllNodesWithTag("manga-card-/fast").fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithTag("manga-card-/fast").performClick()
                    waitUntil(timeoutMillis = 5000) { opened.size == 1 }
                }
                if (!late) waitUntil(timeoutMillis = 1500) { source.finished.get() }
                source.release.complete(Unit)
                waitUntil(timeoutMillis = 5000) { source.finished.get() }
                // Drain the request continuation and any queued Compose callbacks.
                waitForIdle()
                opened.size shouldBe if (dispose) 0 else 1
                runtime.library.allMangaSnapshot().map { it.url } shouldBe if (dispose) emptyList() else listOf("/fast")
                runtime.library.allMangaSnapshot().all { !it.favorite } shouldBe true
                onNodeWithTag("manga-detail-error").assertDoesNotExist()
            } finally {
                source.release.complete(Unit)
                runOnIdle { visible.value = false }
                runBlocking { runtime.shutdown() }
            }
        }

    private class DelayedSource(private val stage: String, private val late: Boolean) : WindowsCatalogueSource {
        override val id = 9191L
        override val name = "Delayed detail source"
        override val lang = "en"
        val started = AtomicBoolean()
        val finished = AtomicBoolean()
        val release = CompletableDeferred<Unit>()
        private suspend fun block(manga: SManga, currentStage: String) {
            if (manga.url != "/slow" || currentStage != stage) return
            started.set(true)
            try {
                if (late) withContext(NonCancellable) { release.await() } else release.await()
            } finally {
                finished.set(true)
            }
        }
        override suspend fun getPopularManga(page: Int) = MangasPage(
            listOf(SManga(url = "/slow", title = "Slow manga"), SManga(url = "/fast", title = "Fast manga")),
            false,
        )
        override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList) = getPopularManga(page)
        override suspend fun getMangaDetails(manga: SManga): SManga {
            block(manga, "details")
            return manga.copy(initialized = true)
        }
        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            block(manga, "chapters")
            return listOf(SChapter(url = "${manga.url}/1", name = "Chapter 1", chapterNumber = 1f))
        }
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
