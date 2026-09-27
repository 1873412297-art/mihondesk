package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderPageIndicatorTest {

    @Test
    fun `page change while chrome is hidden shows a transient page indicator`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = PageTurnSession(testReaderState(pageCount = 6, selectedIndex = 0))
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-page-indicator").assertDoesNotExist()

        // Reveal-then-idle: click once so the hide timer starts, then wait the chrome out.
        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(2_600)
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, false))

        // Wheel navigation is the hidden-chrome reading path: it turns the page without
        // revealing chrome, so the fading page indicator appears instead.
        onNodeWithTag("reader-gesture-area").performMouseInput {
            moveTo(center)
            scroll(1f)
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        // Let the indicator effect publish its state and the frame apply it.
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, false))

        onNodeWithTag("reader-page-indicator").assertIsDisplayed()
        onNodeWithTag("reader-page-indicator").assertTextContains("3 / 6")

        // ~1s visible window plus the fade, then the overlay is gone.
        mainClock.advanceTimeBy(1_400)
        waitForIdle()
        onNodeWithTag("reader-page-indicator").assertDoesNotExist()
    }

    @Test
    fun `page change while chrome is visible keeps the indicator hidden`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = PageTurnSession(testReaderState(pageCount = 6, selectedIndex = 0))
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, true))
        onNodeWithTag("reader-page-indicator").assertDoesNotExist()
    }

    /**
     * [TestReaderSession] leaves [ReaderAction.Next]/[ReaderAction.Previous] as reducer no-ops;
     * production sessions move the selected page themselves. Emulate that so wheel-driven page
     * turns propagate to the reader state.
     */
    private class PageTurnSession(initial: ReaderState) : ReaderSession {
        val mutable = MutableStateFlow(initial)
        override val state: StateFlow<ReaderState> = mutable

        override suspend fun open(chapterId: Long) = Unit

        override fun dispatch(action: ReaderAction) {
            val current = mutable.value
            mutable.value = runCatching {
                when (action) {
                    ReaderAction.Next -> current.reduce(
                        ReaderAction.SelectPage((current.selectedIndex + 1).coerceAtMost(current.pages.lastIndex)),
                    )

                    ReaderAction.Previous -> current.reduce(
                        ReaderAction.SelectPage((current.selectedIndex - 1).coerceAtLeast(0)),
                    )

                    else -> current.reduce(action)
                }
            }.getOrDefault(current)
        }

        override suspend fun retry(pageId: mihon.reader.model.PageId) = Unit

        override suspend fun flushProgress() = Unit

        override suspend fun closeAndFlush() = Unit

        override fun cancelWithoutFlush() = Unit
    }
}
