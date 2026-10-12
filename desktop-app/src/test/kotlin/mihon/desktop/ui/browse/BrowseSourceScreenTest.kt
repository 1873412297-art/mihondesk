package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class BrowseSourceScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `failed pagination waits for explicit retry instead of requesting again on scroll`() = runComposeUiTest {
        var requests = 0
        var retries = 0
        setContent {
            BrowseSourceScreen(
                state = BrowseSourceUiState(
                    source = testSource,
                    hasNextPage = true,
                    mangas = listOf(SManga(url = "/one", title = "One")),
                    errorMessage = "TLS: handshake failed",
                ),
                onBack = {}, onModeChange = {}, onQueryChange = {}, onSearch = {}, onPageChange = {},
                onMangaSelected = {}, onLoadMore = { requests++ }, onRetry = { retries++ },
            )
        }
        waitForIdle()
        requests shouldBe 0
        onNodeWithText("Retry").performClick()
        retries shouldBe 1
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `site block is explained without promising a verification retry`() = runComposeUiTest {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                mihon.desktop.i18n.LocalStrings provides mihon.desktop.i18n.SimplifiedChineseStrings,
            ) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        errorMessage = "HTTP error 403",
                        networkFailure = mihon.extension.ipc.NetworkFailure(
                            mihon.extension.ipc.NetworkFailureKind.SITE_BLOCKED,
                            403,
                            "vapi.ezmanga.org",
                        ),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                    onOpenWebPage = {},
                )
            }
        }
        onNodeWithText("网站已封锁访问。", substring = true).assertIsDisplayed()
        onNodeWithText("vapi.ezmanga.org · HTTP 403", substring = true).assertIsDisplayed()
        onNodeWithTag("source-open-webpage").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `hovering a cover reveals the library toggle and right-click offers the same action`() = runComposeUiTest {
        val toggled = mutableListOf<String>()
        setContent {
            BrowseSourceScreen(
                state = BrowseSourceUiState(
                    source = testSource,
                    mangas = listOf(SManga(url = "/one", title = "One"), SManga(url = "/two", title = "Two")),
                    inLibraryUrls = setOf("/two"),
                ),
                onBack = {},
                onModeChange = {},
                onQueryChange = {},
                onSearch = {},
                onPageChange = {},
                onMangaSelected = {},
                onToggleLibrary = { toggled += it.url },
            )
        }
        onNodeWithTag("browse-library-toggle-/one").assertDoesNotExist()
        onNodeWithTag("manga-card-/one").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag("browse-library-toggle-/one").assertIsDisplayed().performClick()
        toggled shouldBe listOf("/one")

        onNodeWithTag("manga-card-/two").performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Remove from Library").assertIsDisplayed().performClick()
        toggled shouldBe listOf("/one", "/two")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `pending library request keeps the toggle visible and disabled`() = runComposeUiTest {
        setContent {
            BrowseSourceScreen(
                state = BrowseSourceUiState(
                    source = testSource,
                    mangas = listOf(SManga(url = "/one", title = "One")),
                    libraryPendingUrls = setOf("/one"),
                ),
                onBack = {},
                onModeChange = {},
                onQueryChange = {},
                onSearch = {},
                onPageChange = {},
                onMangaSelected = {},
                onToggleLibrary = {},
            )
        }
        onNodeWithTag("browse-library-toggle-/one").assertIsDisplayed().assertIsNotEnabled()
    }

    private val testSource = SourceDescriptor(
        id = 123L,
        name = "Test Manga Source",
        lang = "en",
        className = "ext.test.TestSource",
        supportsLatest = true,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `displays manga grid and triggers page and selection callbacks`() = runComposeUiTest {
        var selectedManga: SManga? = null
        var changedPage: Int? = null
        var changedMode: SourceListingMode? = null

        val sampleManga = SManga(
            url = "/manga/one",
            title = "Awesome Manga",
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        mangas = listOf(sampleManga),
                        inLibraryUrls = setOf("/manga/one"),
                        hasNextPage = true,
                        page = 1,
                    ),
                    onBack = {},
                    onModeChange = { changedMode = it },
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = { changedPage = it },
                    onMangaSelected = { selectedManga = it },
                )
            }
        }

        onNodeWithText("Awesome Manga").assertIsDisplayed()
        onNodeWithText("IN LIBRARY").assertIsDisplayed()

        // Test card click
        onNodeWithTag("manga-card-/manga/one").performClick()
        selectedManga shouldBe sampleManga

        // Test next page click
        onNodeWithTag("next-page-btn").performClick()
        changedPage shouldBe 2

        // Test mode switch click
        onNodeWithTag("mode-latest-chip").performClick()
        changedMode shouldBe SourceListingMode.Latest
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `local source empty state shows import action`() = runComposeUiTest {
        var importClicked = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = SourceDescriptor(
                            id = 0L,
                            name = "Local source",
                            lang = "other",
                            className = "mihon.desktop.extension.builtin.BundledLocalSource",
                            supportsLatest = true,
                        ),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                    onImportLocal = { importClicked = true },
                )
            }
        }

        onNodeWithTag("local-source-import-btn").assertIsDisplayed()
        onNodeWithTag("local-source-import-btn").performClick()
        importClicked shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `local manga cards display chapter count`() = runComposeUiTest {
        val sampleManga = SManga(
            url = "local:alpha",
            title = "Alpha",
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = SourceDescriptor(
                            id = 0L,
                            name = "Local source",
                            lang = "other",
                            className = "mihon.desktop.extension.builtin.BundledLocalSource",
                            supportsLatest = true,
                        ),
                        mangas = listOf(sampleManga),
                        chapterCounts = mapOf(sampleManga.url to 3L),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                )
            }
        }

        onNodeWithTag("manga-chapter-count-${sampleManga.url}", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("3 chapters").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `unsupported mode renders friendly prompt without showing error card`() = runComposeUiTest {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                mihon.desktop.i18n.LocalStrings provides mihon.desktop.i18n.SimplifiedChineseStrings,
            ) {
                BrowseSourceScreen(
                    state = BrowseSourceUiState(
                        source = testSource,
                        unsupportedMode = SourceListingMode.Popular,
                    ),
                    onBack = {},
                    onModeChange = {},
                    onQueryChange = {},
                    onSearch = {},
                    onPageChange = {},
                    onMangaSelected = {},
                )
            }
        }

        onNodeWithTag("source-unsupported-message").assertIsDisplayed()
        onNodeWithText("该图源不支持浏览，请使用搜索").assertIsDisplayed()
    }
}
