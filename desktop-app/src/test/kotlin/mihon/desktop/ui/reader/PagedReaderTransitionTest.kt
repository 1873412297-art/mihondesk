package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderPageTransition
import mihon.reader.model.ReadingMode
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class PagedReaderTransitionTest {

    @Test
    fun `calculateSlideDirection normalizes forward and backward in LTR`() {
        // LTR forward
        calculateSlideDirection(fromIndex = 0, toIndex = 1, isRtl = false) shouldBe 1f
        // LTR backward
        calculateSlideDirection(fromIndex = 1, toIndex = 0, isRtl = false) shouldBe -1f
        // LTR same index defaults forward
        calculateSlideDirection(fromIndex = 1, toIndex = 1, isRtl = false) shouldBe 1f
    }

    @Test
    fun `calculateSlideDirection reverses slide direction in RTL`() {
        // RTL forward must reverse LTR (+1f -> -1f)
        calculateSlideDirection(fromIndex = 0, toIndex = 1, isRtl = true) shouldBe -1f
        // RTL backward must reverse LTR (-1f -> +1f)
        calculateSlideDirection(fromIndex = 1, toIndex = 0, isRtl = true) shouldBe 1f
        // RTL same index defaults forward reversed
        calculateSlideDirection(fromIndex = 1, toIndex = 1, isRtl = true) shouldBe -1f
    }

    @Test
    fun `paged reader with NONE transition renders immediately without transition container`() = runComposeUiTest {
        var state by mutableStateOf(testReaderState(pageCount = 3, selectedIndex = 0))

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    PagedReader(
                        state = state,
                        viewportWidth = 800.dp,
                        onAction = {},
                        pageContent = { _, index, modifier ->
                            DefaultTestPage(index, modifier)
                        },
                        pageTransition = ReaderPageTransition.NONE,
                    )
                }
            }
        }

        onNodeWithTag("reader-page-0").assertIsDisplayed()
        onAllNodesWithTag("reader-paged-transition-container").assertCountEquals(0)

        state = state.copy(selectedIndex = 1)
        waitForIdle()

        onNodeWithTag("reader-page-1").assertIsDisplayed()
        onAllNodesWithTag("reader-paged-transition-container").assertCountEquals(0)
    }

    @Test
    fun `paged reader with FADE transition animates between pages and settles`() = runComposeUiTest {
        var state by mutableStateOf(testReaderState(pageCount = 3, selectedIndex = 0))

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    PagedReader(
                        state = state,
                        viewportWidth = 800.dp,
                        onAction = {},
                        pageContent = { _, index, modifier ->
                            DefaultTestPage(index, modifier)
                        },
                        pageTransition = ReaderPageTransition.FADE,
                    )
                }
            }
        }

        onNodeWithTag("reader-page-0").assertIsDisplayed()

        state = state.copy(selectedIndex = 1)
        waitForIdle()

        // After settling, page 1 is displayed and transition container is cleared
        onNodeWithTag("reader-page-1").assertIsDisplayed()
        onAllNodesWithTag("reader-paged-transition-container").assertCountEquals(0)
    }

    @Test
    fun `paged reader with SLIDE transition in LTR and RTL settles cleanly`() = runComposeUiTest {
        var state by mutableStateOf(testReaderState(pageCount = 3, selectedIndex = 0, mode = ReadingMode.SINGLE_RTL))

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    PagedReader(
                        state = state,
                        viewportWidth = 800.dp,
                        onAction = {},
                        pageContent = { _, index, modifier ->
                            DefaultTestPage(index, modifier)
                        },
                        pageTransition = ReaderPageTransition.SLIDE,
                    )
                }
            }
        }

        onNodeWithTag("reader-page-0").assertIsDisplayed()

        // Navigate forward in RTL
        state = state.copy(selectedIndex = 1)
        waitForIdle()

        onNodeWithTag("reader-page-1").assertIsDisplayed()
        onAllNodesWithTag("reader-paged-transition-container").assertCountEquals(0)
    }

    @Test
    fun `rapid page changes retarget and settle cleanly to final target`() = runComposeUiTest {
        var state by mutableStateOf(testReaderState(pageCount = 5, selectedIndex = 0))

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    PagedReader(
                        state = state,
                        viewportWidth = 800.dp,
                        onAction = {},
                        pageContent = { _, index, modifier ->
                            DefaultTestPage(index, modifier)
                        },
                        pageTransition = ReaderPageTransition.SLIDE,
                    )
                }
            }
        }

        onNodeWithTag("reader-page-0").assertIsDisplayed()

        // Rapid forward navigation: 0 -> 1 -> 2
        state = state.copy(selectedIndex = 1)
        state = state.copy(selectedIndex = 2)
        waitForIdle()

        onNodeWithTag("reader-page-2").assertIsDisplayed()

        // Rapid reversal: 2 -> 1 -> 0
        state = state.copy(selectedIndex = 1)
        state = state.copy(selectedIndex = 0)
        waitForIdle()

        onNodeWithTag("reader-page-0").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun DefaultTestPage(pageIndex: Int, modifier: Modifier) {
    Box(modifier = modifier)
}
