package mihon.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.upcoming.UPCOMING_BACK_BUTTON_TEST_TAG
import mihon.desktop.ui.upcoming.UPCOMING_SCREEN_TEST_TAG
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.ui.upcoming.UpcomingUiState
import mihon.desktop.ui.updates.UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG
import mihon.desktop.ui.updates.UPDATES_SCREEN_TEST_TAG
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopShellTest {
    @Test
    fun `rail hosts Stats under History and About under Settings`() {
        (RAIL_PRIMARY_DESTINATIONS + RAIL_SECONDARY_DESTINATIONS).contains(DesktopDestination.Stats) shouldBe false
        (RAIL_PRIMARY_DESTINATIONS + RAIL_SECONDARY_DESTINATIONS).contains(DesktopDestination.About) shouldBe false
        railSelectionFor(DesktopDestination.Stats) shouldBe DesktopDestination.History
        railSelectionFor(DesktopDestination.About) shouldBe DesktopDestination.Settings
        railSelectionFor(DesktopDestination.Library) shouldBe DesktopDestination.Library
        mihon.desktop.ui.common.ContentDensity.parse("compact") shouldBe mihon.desktop.ui.common.ContentDensity.Compact
        mihon.desktop.ui.common.ContentDensity.parse(null) shouldBe mihon.desktop.ui.common.ContentDensity.Standard
        mihon.desktop.ui.common.NavRailState.resolve(userCollapsed = false, narrow = true, overlayOpen = false) shouldBe
            mihon.desktop.ui.common.NavRailState.Collapsed
        mihon.desktop.ui.common.NavRailState.resolve(userCollapsed = false, narrow = true, overlayOpen = true) shouldBe
            mihon.desktop.ui.common.NavRailState.OverlayExpanded
        mihon.desktop.ui.common.NavRailState.resolve(userCollapsed = true, narrow = false, overlayOpen = false) shouldBe
            mihon.desktop.ui.common.NavRailState.Collapsed
        mihon.desktop.ui.common.NavRailState.Collapsed.showsLabels shouldBe false
        mihon.desktop.ui.common.NavRailState.Expanded.showsLabels shouldBe true
        mihon.desktop.ui.common.NavRailState.OverlayExpanded.showsLabels shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `menu toggle collapses the wide rail and has no density button`() =
        androidx.compose.ui.test.runDesktopComposeUiTest(width = 1400, height = 800) {
            mihon.desktop.ui.common.DesktopLayoutPrefs.bind(null)
            setContent {
                Box(modifier = Modifier.requiredSize(1400.dp, 800.dp)) {
                    DesktopShell(selected = DesktopDestination.Library, onDestinationSelected = {})
                }
            }
            onNodeWithTag("desktop-density-toggle").assertDoesNotExist()
            onNodeWithTag("desktop-rail-collapse").assertDoesNotExist()
            val before = onNodeWithTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG).getBoundsInRoot().width
            onNodeWithTag(DESKTOP_RAIL_TOGGLE_TEST_TAG).performClick()
            val collapsed = onNodeWithTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG).getBoundsInRoot().width
            assertTrue(collapsed < before, "collapsed rail must be narrower")
            onNodeWithTag(DESKTOP_RAIL_TOGGLE_TEST_TAG).performClick()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `narrow window auto collapses the rail and expands as overlay`() = runComposeUiTest {
        mihon.desktop.ui.common.DesktopLayoutPrefs.bind(null)
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                DesktopShell(selected = DesktopDestination.Library, onDestinationSelected = {})
            }
        }
        onNodeWithTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG).getBoundsInRoot().width shouldBe RAIL_COLLAPSED_WIDTH
        onNodeWithTag(DESKTOP_RAIL_SCRIM_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(DESKTOP_RAIL_TOGGLE_TEST_TAG).performClick()
        onNodeWithTag(DESKTOP_RAIL_SCRIM_TEST_TAG).assertExists()
        onNodeWithTag("rail-item-settings").assertExists()
        onNodeWithTag(DESKTOP_RAIL_SCRIM_TEST_TAG).performClick()
        onNodeWithTag(DESKTOP_RAIL_SCRIM_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `legacy ui density compact migrates to content density`() {
        val dir = java.nio.file.Files.createTempDirectory("density-migrate")
        val store = mihon.desktop.preferences.DesktopPreferenceStore(dir.resolve("prefs.properties"))
        store.update { setProperty("ui.density", "Compact") }
        mihon.desktop.ui.common.ContentDensity.load(store) shouldBe mihon.desktop.ui.common.ContentDensity.Compact
        store.property(mihon.desktop.ui.common.ContentDensity.PREF_KEY) shouldBe "Compact"
        store.property("ui.density") shouldBe null
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `compact content density never changes LocalDensity so the reader is unaffected`() = runComposeUiTest {
        var outer: androidx.compose.ui.unit.Density? = null
        var inner: androidx.compose.ui.unit.Density? = null
        var innerSpacing: mihon.desktop.ui.common.Spacing? = null
        setContent {
            outer = androidx.compose.ui.platform.LocalDensity.current
            mihon.desktop.ui.common.ProvideContentDensity(mihon.desktop.ui.common.ContentDensity.Compact) {
                inner = androidx.compose.ui.platform.LocalDensity.current
                innerSpacing = mihon.desktop.ui.common.LocalSpacing.current
            }
        }
        waitForIdle()
        inner shouldBe outer
        innerSpacing shouldBe mihon.desktop.ui.common.ContentDensity.Compact.spacing
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `standalone details render without a selected library row even on wide desktop`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1400.dp, 900.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    libraryState = LibraryUiState(loading = false, selectedMangaId = null),
                    standaloneMangaDetails = true,
                )
            }
        }
        onNodeWithTag("manga-detail-pane").assertExists()
        onNodeWithTag("library-grid-pane").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `completed downloads expose a reader action while unfinished downloads do not`() = runComposeUiTest {
        var readChapterId: Long? = null
        var readMangaId: Long? = null
        val queue = mihon.desktop.download.DownloadStatus.entries.mapIndexed { index, status ->
            mihon.desktop.download.DesktopDownload(
                chapterId = index + 1L,
                mangaId = 42L,
                sourceId = 100L,
                mangaTitle = "Downloaded manga",
                chapterName = status.name,
                chapterUrl = "/$index",
                status = status,
            )
        }
        setContent {
            Box(modifier = Modifier.requiredSize(1100.dp, 1200.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Downloads,
                    onDestinationSelected = {},
                    downloadsQueue = queue,
                    onReadDownloadedChapter = { mangaId, chapterId ->
                        readMangaId = mangaId
                        readChapterId = chapterId
                    },
                )
            }
        }
        queue.filter { it.status != mihon.desktop.download.DownloadStatus.COMPLETED }.forEach { item ->
            onNodeWithTag("download_read_${item.chapterId}").assertDoesNotExist()
        }
        val completed = queue.single { it.status == mihon.desktop.download.DownloadStatus.COMPLETED }
        onNodeWithTag("download_read_${completed.chapterId}").performClick()
        readChapterId shouldBe completed.chapterId
        readMangaId shouldBe completed.mangaId
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Library destination renders the real library screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    libraryState = LibraryUiState(loading = false),
                )
            }
        }

        onNodeWithTag("library-screen").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `selected headline has positive bounds beside the navigation rail`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Settings,
                    onDestinationSelected = {},
                )
            }
        }

        val headlineBounds = onNodeWithTag(DESKTOP_MAIN_HEADLINE_TEST_TAG).getBoundsInRoot()
        val railBounds = onNodeWithTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG).getBoundsInRoot()

        assertTrue(headlineBounds.width > 0.dp, "Selected headline must have positive width")
        assertTrue(
            headlineBounds.left >= railBounds.right,
            "Selected headline must be laid out beside the navigation rail",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Downloads destination renders downloads screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Downloads,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.tasks.DOWNLOADS_SCREEN_TEST_TAG).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Updates destination renders updates screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Updates,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.updates.UPDATES_SCREEN_TEST_TAG).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Stats destination renders stats screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Stats,
                    onDestinationSelected = {},
                    statsData = mihon.desktop.stats.DesktopStatsData(isLoading = false),
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.stats.STATS_SCREEN_TEST_TAG).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Incognito mode shows banner in DesktopShell`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    incognitoMode = true,
                )
            }
        }

        onNodeWithTag("incognito-banner").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `lock now button in the shell invokes the app lock callback`() = runComposeUiTest {
        var lockRequested = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 700.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    onLockNow = { lockRequested = true },
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG).performClick()
        assertTrue(lockRequested)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `lock now button is hidden when app lock is not configured`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `upcoming opened from updates restores updates when back is pressed`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1100.dp, 760.dp)) {
                var isUpcomingOpen by remember { mutableStateOf(false) }
                DesktopShell(
                    selected = DesktopDestination.Updates,
                    onDestinationSelected = {},
                    isUpcomingOpen = isUpcomingOpen,
                    onOpenUpcoming = { isUpcomingOpen = true },
                    onCloseUpcoming = { isUpcomingOpen = false },
                    upcomingContent = if (isUpcomingOpen) {
                        {
                            UpcomingScreen(
                                state = UpcomingUiState(loading = false),
                                onBack = { isUpcomingOpen = false },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(UPCOMING_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertDoesNotExist()

        onNodeWithTag(UPCOMING_BACK_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPCOMING_SCREEN_TEST_TAG).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no page shows the old strip or a continue reading row`() = runComposeUiTest {
        mihon.desktop.ui.common.DesktopLayoutPrefs.bind(null)
        val today = listOf(
            mihon.desktop.ui.home.TodayMangaUpdates(
                1,
                "M1",
                null,
                listOf(mihon.desktop.updates.UpdatedChapterItem(1, 11, "M1", "第11话", 11.0, 1L)),
            ),
        )
        var selected by mutableStateOf(DesktopDestination.Library)
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                DesktopShell(
                    selected = selected,
                    onDestinationSelected = {},
                    homeToday = today,
                )
            }
        }
        onNodeWithTag("home-today").assertDoesNotExist()
        onNodeWithTag("home-continue-5").assertDoesNotExist()
        selected = DesktopDestination.Updates
        waitForIdle()
        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag("home-today").assertDoesNotExist()
        selected = DesktopDestination.History
        waitForIdle()
        onNodeWithTag("history-continue-row").assertDoesNotExist()
        onNodeWithTag("history-continue-5").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history and stats begin with their page header and switch via header buttons`() = runComposeUiTest {
        mihon.desktop.ui.common.DesktopLayoutPrefs.bind(null)
        var selected by mutableStateOf(DesktopDestination.History)
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                DesktopShell(selected = selected, onDestinationSelected = { selected = it })
            }
        }
        onNodeWithTag("history-segments").assertDoesNotExist()
        onNodeWithTag("history-open-stats").performClick()
        waitForIdle()
        selected shouldBe DesktopDestination.Stats
        onNodeWithTag("history-segments").assertDoesNotExist()
        onNodeWithTag("stats-open-history").performClick()
        waitForIdle()
        selected shouldBe DesktopDestination.History
    }
}
