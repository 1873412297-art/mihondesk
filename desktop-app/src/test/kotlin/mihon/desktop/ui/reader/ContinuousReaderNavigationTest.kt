package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ContinuousReaderNavigationTest {
    @Test
    fun `external page selection scrolls an already composed long chapter`() = runComposeUiTest {
        var state by mutableStateOf(chapter(7))
        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                ReaderCanvas(state, onAction = { state = state.reduce(it) })
            }
        }
        waitForIdle()
        runOnIdle { state = state.reduce(ReaderAction.SelectPage(173)) }
        waitForIdle()

        onNodeWithTag(pageTag(173)).assertIsDisplayed()
        runOnIdle { state.viewportAnchor.pageIndex shouldBe 173 }
        onNodeWithTag(pageTag(0)).assertDoesNotExist()
    }

    @Test
    fun `replacing chapter restores its anchor without retaining the old list position`() = runComposeUiTest {
        var state by mutableStateOf(chapter(7, 90))
        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                ReaderCanvas(state, onAction = { state = state.reduce(it) })
            }
        }
        waitForIdle()
        runOnIdle { state = chapter(8, 12) }
        waitForIdle()

        onNodeWithTag(pageTag(12)).assertIsDisplayed()
        runOnIdle { state.viewportAnchor.pageIndex shouldBe 12 }
    }

    @Test
    fun `list scrolling updates the core anchor without snapping back`() = runComposeUiTest {
        var state by mutableStateOf(chapter(7))
        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                ReaderCanvas(state, onAction = { state = state.reduce(it) })
            }
        }
        waitForIdle()
        onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        waitForIdle()

        onNodeWithTag(pageTag(40)).assertIsDisplayed()
        runOnIdle { state.viewportAnchor.pageIndex shouldBe 40 }
    }

    @Test
    fun `entering continuous mode restores current anchor rather than replaying an older jump`() = runComposeUiTest {
        var state by mutableStateOf(
            chapter(7).reduce(ReaderAction.SelectPage(20))
                .reduce(ReaderAction.SetViewportAnchor(ReaderPosition(40, 30))),
        )
        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                ReaderCanvas(state, onAction = { state = state.reduce(it) })
            }
        }
        waitForIdle()

        onNodeWithTag(pageTag(40)).assertIsDisplayed()
        runOnIdle { state.viewportAnchor shouldBe ReaderPosition(40, 30) }
    }

    @Test
    fun `delayed core viewport echo does not rewind an ongoing list scroll`() = runComposeUiTest {
        var state by mutableStateOf(chapter(7).reduce(ReaderAction.SelectPage(20)))
        var delayReports = false
        val reports = mutableListOf<ReaderAction.SetViewportAnchor>()
        setContent {
            Box(Modifier.requiredSize(800.dp, 600.dp)) {
                ReaderCanvas(state, onAction = { action ->
                    if (delayReports && action is ReaderAction.SetViewportAnchor) {
                        reports += action
                    } else {
                        state = state.reduce(action)
                    }
                })
            }
        }
        waitForIdle()
        runOnIdle { delayReports = true }
        onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        waitForIdle()
        onNode(hasScrollToIndexAction()).performScrollToIndex(60)
        waitForIdle()
        runOnIdle { state = state.reduce(reports.first { it.position.pageIndex == 40 }) }
        waitForIdle()

        onNodeWithTag(pageTag(60)).assertIsDisplayed()
        runOnIdle { state = state.reduce(reports.last { it.position.pageIndex == 60 }) }
        waitForIdle()
        runOnIdle { state.viewportAnchor.pageIndex shouldBe 60 }
    }

    private fun chapter(id: Long, index: Int = 0): ReaderState = ReaderState.ready(
        chapterId = id,
        pages = List(256) { page -> PageDescriptor(PageId(id.toString(), "$page.jpg"), 800, 1200) },
        selectedIndex = index,
    ).copy(mode = ReadingMode.VERTICAL, viewportAnchor = ReaderPosition(index))
}
