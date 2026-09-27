package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test

class BrowseFilterOptionsTest {

    @Test
    fun `isSourceNsfw detects NSFW from installed and available extensions`() {
        val s1 = SourceDescriptor(id = 1L, name = "Clean Source", lang = "en", className = "Clean")
        val s2 = SourceDescriptor(id = 2L, name = "NSFW Source 1", lang = "ja", className = "Nsfw1")
        val s3 = SourceDescriptor(id = 3L, name = "NSFW Source 2", lang = "zh", className = "Nsfw2")

        val installed = listOf(
            InstalledExtension(
                pkg = "ext.clean",
                manifest = ExtensionManifest(
                    id = "ext.clean",
                    name = "Clean",
                    version = "1",
                    versionCode = 1,
                    libVersion = 1.4,
                    lang = "en",
                    isNsfw = false,
                    sources = listOf(s1),
                ),
                installDir = "",
                packageFile = "",
                installedAt = 0L,
            ),
            InstalledExtension(
                pkg = "ext.nsfw1",
                manifest = ExtensionManifest(
                    id = "ext.nsfw1",
                    name = "Nsfw1",
                    version = "1",
                    versionCode = 1,
                    libVersion = 1.4,
                    lang = "ja",
                    isNsfw = true,
                    sources = listOf(s2),
                ),
                installDir = "",
                packageFile = "",
                installedAt = 0L,
            ),
        )

        val available = listOf(
            ExtensionStoreItem(
                pkg = "ext.nsfw2",
                name = "Nsfw2",
                version = "1",
                versionCode = 1,
                lang = "zh",
                isNsfw = true,
                sources = listOf(s3),
            ),
        )

        isSourceNsfw(1L, installed, available) shouldBe false
        isSourceNsfw(2L, installed, available) shouldBe true
        isSourceNsfw(3L, installed, available) shouldBe true
        isSourceNsfw(4L, installed, available) shouldBe false
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `SourcesListView filters by language and excludes hidden and NSFW sources`() = runComposeUiTest {
        val s1 = SourceDescriptor(id = 10L, name = "English Source", lang = "en", className = "En")
        val s2 = SourceDescriptor(id = 20L, name = "Chinese Source", lang = "zh", className = "Zh")
        val s3 = SourceDescriptor(id = 30L, name = "Japanese Source", lang = "ja", className = "Ja")
        val s4 = SourceDescriptor(id = 40L, name = "Adult Source", lang = "en", className = "Adult")

        val installed = listOf(
            InstalledExtension(
                pkg = "ext.adult",
                manifest = ExtensionManifest(
                    id = "ext.adult",
                    name = "Adult",
                    version = "1",
                    versionCode = 1,
                    libVersion = 1.4,
                    lang = "en",
                    isNsfw = true,
                    sources = listOf(s4),
                ),
                installDir = "",
                packageFile = "",
                installedAt = 0L,
            ),
        )

        var selectedLang: String? = null
        val hiddenIds = setOf(20L)

        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseScreen(
                    state = BrowseUiState(
                        selectedTab = BrowseTab.Sources,
                        sources = listOf(s1, s2, s3, s4),
                        hiddenSourceIds = hiddenIds,
                        showNsfw = false,
                        sourceLanguageFilter = selectedLang,
                        installedExtensions = installed,
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSourceLanguageFilterChange = { selectedLang = it },
                )
            }
        }

        onNodeWithTag("source-item-10").assertIsDisplayed()
        onNodeWithTag("source-item-20").assertDoesNotExist()
        onNodeWithTag("source-item-30").assertIsDisplayed()
        onNodeWithTag("source-item-40").assertDoesNotExist()

        onNodeWithTag("source-lang-chip-all").assertIsDisplayed()
        onNodeWithTag("source-lang-chip-en").assertIsDisplayed()
        onNodeWithTag("source-lang-chip-ja").assertIsDisplayed()

        onNodeWithTag("source-lang-chip-ja").performClick()
        selectedLang shouldBe "ja"
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `GlobalSearchScreen only-pinned chip toggles filter state`() = runComposeUiTest {
        var onlyPinned = false
        setContent {
            GlobalSearchScreen(
                query = "test",
                onQueryChange = {},
                onSearch = {},
                onBack = {},
                isSearching = false,
                sourceResults = emptyList(),
                onMangaSelected = { _, _ -> },
                onViewSource = {},
                onlyPinned = onlyPinned,
                onToggleOnlyPinned = { onlyPinned = it },
            )
        }

        onNodeWithTag("global-search-only-pinned").assertIsDisplayed()
        onNodeWithTag("global-search-only-pinned").performClick()
        onlyPinned shouldBe true
    }
}
