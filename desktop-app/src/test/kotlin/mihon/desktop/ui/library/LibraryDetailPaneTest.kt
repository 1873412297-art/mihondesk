package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class LibraryDetailPaneTest {
    private val items = (1L..60L).map { LibraryManga(it, 100 + it, "/$it", "Manga $it", null, 2, 1, "A") }

    private fun detail(id: Long) = MangaDetailUiState(
        manga = MangaDetails(
            id, 100 + id, "/$id", "Manga $id", null, "A", "D", "[]",
            0, null, true, 0, 0, 0, "ALWAYS_UPDATE", 0, null, "[]", 0, "", true, "{}", emptyList(),
        ),
        loading = false,
    )

    private class Harness {
        var selected by mutableStateOf<Long?>(7L)
        var backs = 0
        var reportedWidth: Int? = null
        var paneWidth by mutableStateOf(0)
    }

    private fun ComposeUiTest.screen(width: Dp, h: Harness = Harness()): Harness {
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    LibraryScreen(
                        state = LibraryUiState(false, items = items, selectedMangaId = h.selected),
                        detailState = h.selected?.let(::detail) ?: MangaDetailUiState(),
                        onQueryChange = {},
                        onMangaSelected = { h.selected = it },
                        onBackFromDetail = {
                            h.backs++
                            h.selected = null
                        },
                        onImportBackup = {},
                        onImportLocal = {},
                        detailPaneWidthDp = h.paneWidth,
                        onDetailPaneWidthChange = { h.reportedWidth = it },
                    )
                }
            }
        }
        return h
    }

    private val gridCard7 = hasText("Manga 7") and hasAnyAncestor(hasTestTag("library-grid-pane"))

    @Test
    fun `close button on the pane returns to the full-width grid`() = runDesktopComposeUiTest(1600, 1000) {
        val h = screen(1400.dp)
        val grid = onNodeWithTag("library-grid-pane").getBoundsInRoot()
        val pane = onNodeWithTag("library-detail-card").getBoundsInRoot()
        (pane.left - grid.right >= 16.dp) shouldBe true
        // No empty header strip: the action row starts at the card top and ends with the close button.
        val row = onNodeWithTag("manga-detail-action-row").getBoundsInRoot()
        (row.top - pane.top <= 16.dp) shouldBe true
        val close = onNodeWithTag("library-detail-close", useUnmergedTree = true).getBoundsInRoot()
        (close.right > pane.right - 64.dp) shouldBe true
        (close.top >= row.top - 1.dp && close.bottom <= row.bottom + 1.dp) shouldBe true
        onNodeWithTag("manga-detail-edit-info-button").assertIsDisplayed()
        onNodeWithTag("manga-detail-open-tracking-button").assertIsDisplayed()
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        h.backs shouldBe 1
        onNodeWithTag("library-detail-card").assertDoesNotExist()
        val full = onNodeWithTag("library-grid-pane").getBoundsInRoot()
        (full.right - full.left > 1300.dp) shouldBe true
    }

    @Test
    fun `escape closes the pane`() = runDesktopComposeUiTest(1600, 1000) {
        val h = screen(1400.dp)
        onNodeWithTag("library-detail-card").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        h.selected shouldBe null
    }

    @Test
    fun `clicking the open book again toggles the pane closed`() = runDesktopComposeUiTest(1600, 1000) {
        val h = screen(1400.dp)
        onNode(gridCard7, useUnmergedTree = true).performClick()
        waitForIdle()
        h.selected shouldBe null
        h.backs shouldBe 1
    }

    @Test
    fun `clicking empty grid space closes the pane`() = runDesktopComposeUiTest(1600, 1000) {
        val h = Harness()
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(1400.dp, 800.dp)) {
                    LibraryScreen(
                        state = LibraryUiState(false, items = items.take(1), selectedMangaId = h.selected),
                        detailState = h.selected?.let(::detail) ?: MangaDetailUiState(),
                        onQueryChange = {},
                        onMangaSelected = { h.selected = it },
                        onBackFromDetail = { h.selected = null },
                        onImportBackup = {},
                        onImportLocal = {},
                    )
                }
            }
        }
        onNodeWithTag("library-grid-pane").performTouchInput { click(Offset(width / 2f, height - 40f)) }
        waitForIdle()
        h.selected shouldBe null
    }

    @Test
    fun `dragging the divider resizes and remembers the width, far right closes`() = runDesktopComposeUiTest(
        1600,
        1000,
    ) {
        val h = screen(1400.dp)
        val before = onNodeWithTag("library-detail-card").getBoundsInRoot().let { it.right - it.left }
        onNodeWithTag("library-detail-divider").performTouchInput {
            down(center)
            moveBy(Offset(-150f, 0f))
            moveBy(Offset(-150f, 0f))
            up()
        }
        waitForIdle()
        val after = onNodeWithTag("library-detail-card").getBoundsInRoot().let { it.right - it.left }
        (after > before) shouldBe true
        ((h.reportedWidth ?: 0) > before.value.toInt()) shouldBe true
        onNodeWithTag("library-detail-divider").performTouchInput {
            down(center)
            moveBy(Offset(1200f, 0f))
            up()
        }
        waitForIdle()
        h.selected shouldBe null
    }

    @Test
    fun `remembered pane width is applied`() = runDesktopComposeUiTest(1600, 1000) {
        val h = Harness().apply { paneWidth = 700 }
        screen(1400.dp, h)
        val w = onNodeWithTag("library-detail-card").getBoundsInRoot().let { it.right - it.left }
        (w > 650.dp && w < 720.dp) shouldBe true
    }

    @Test
    fun `narrow windows open details as a full page with back`() = runDesktopComposeUiTest(1600, 1000) {
        screen(640.dp)
        onNodeWithTag("library-grid-pane").assertDoesNotExist()
        onNodeWithTag("manga-detail-back").assertExists()
    }

    @Test
    fun `closing keeps the grid scroll position`() = runDesktopComposeUiTest(1600, 1000) {
        val h = Harness().apply { selected = null }
        screen(1400.dp, h)
        onNodeWithTag("library-view-comfortable").performScrollToIndex(45)
        h.selected = 7
        waitForIdle()
        h.selected = null
        waitForIdle()
        onNodeWithText("Manga 46").assertIsDisplayed()
    }

    @Test
    fun `a narrow pane moves edit info and tracking into the overflow menu first`() =
        runDesktopComposeUiTest(1600, 1000) {
            val h = Harness().apply { paneWidth = 380 }
            screen(1400.dp, h)
            onNodeWithTag("manga-detail-edit-categories-button").assertIsDisplayed()
            onNodeWithContentDescription("Close").assertIsDisplayed()
            onNodeWithTag("manga-detail-more-button").performClick()
            waitForIdle()
            onNodeWithTag("manga-detail-menu-edit-info").assertExists()
            onNodeWithTag("manga-detail-menu-tracking").assertExists()
        }
}
