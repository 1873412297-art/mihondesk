package mihon.desktop.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.desktop.history.DesktopHistoryGroup
import mihon.desktop.history.HistoryGrouper
import mihon.desktop.library.model.HistoryWithDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `clearing an unmatched search does not clear reading history`() = runComposeUiTest {
        var query = "missing"
        var deleted = false
        setContent {
            HistoryScreen(emptyList(), query, { query = it }, {}, { deleted = true }, { deleted = true })
        }
        onNodeWithTag("history-clear-search").performClick()
        assertEquals("", query)
        assertEquals(false, deleted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history screen displays groups and invokes resume and delete`() = runComposeUiTest {
        var resumedChapterId: Long? = null
        var deletedChapterId: Long? = null
        var cleared = false

        val sampleItem = HistoryWithDetails(
            chapterId = 101L,
            mangaId = 1L,
            mangaTitle = "Chainsaw Man",
            mangaThumbnailUrl = null,
            mangaSourceId = 1L,
            chapterName = "Chapter 1",
            chapterNumber = 1.0,
            lastPageRead = 5L,
            read = false,
            lastRead = System.currentTimeMillis(),
            readDuration = 120000L,
        )

        val groups = HistoryGrouper.group(listOf(sampleItem))

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                HistoryScreen(
                    groups = groups,
                    query = "",
                    onQueryChange = {},
                    onReadChapter = { resumedChapterId = it },
                    onDeleteItem = { deletedChapterId = it },
                    onClearAll = { cleared = true },
                )
            }
        }

        onNodeWithTag("history-screen").assertExists()
        onNodeWithTag("history-group-Today").assertExists()
        onNodeWithText("Chainsaw Man").assertExists()

        // Resume by clicking the whole row; there is no separate resume button.
        onNodeWithTag("history-resume-101").assertDoesNotExist()
        onNodeWithTag("history-item-101").performClick()
        assertEquals(101L, resumedChapterId)

        // Delete
        onNodeWithTag("history-delete-101").performClick()
        assertEquals(101L, deletedChapterId)

        // Clear all button & confirmation dialog
        onNodeWithTag("history-clear-all-button").performClick()
        onNodeWithTag("history-confirm-clear-button").performClick()
        assertTrue(cleared)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history screen empty state displays when no items`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                HistoryScreen(
                    groups = emptyList(),
                    query = "",
                    onQueryChange = {},
                    onReadChapter = {},
                    onDeleteItem = {},
                    onClearAll = {},
                )
            }
        }

        onNodeWithTag("history-empty-state").assertExists()
        onNodeWithText("No reading history recorded yet").assertExists()
    }

    private val sample = HistoryWithDetails(
        chapterId = 7L,
        mangaId = 3L,
        mangaTitle = "Dandadan",
        mangaThumbnailUrl = null,
        mangaSourceId = 1L,
        chapterName = "Chapter 9",
        chapterNumber = 9.0,
        lastPageRead = 5L,
        read = false,
        lastRead = System.currentTimeMillis(),
        readDuration = 0L,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history has no continue reading row and rows show a large cover and a delete icon`() = runComposeUiTest {
        var deleted: Long? = null
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                HistoryScreen(
                    groups = HistoryGrouper.group(listOf(sample)),
                    query = "",
                    onQueryChange = {},
                    onReadChapter = {},
                    onDeleteItem = { deleted = it },
                    onClearAll = {},
                )
            }
        }
        onNodeWithTag("history-continue-row").assertDoesNotExist()
        val cover = onNodeWithTag("history-cover-7", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(64.dp, cover.right - cover.left)
        assertEquals(90.dp, cover.bottom - cover.top)
        onNodeWithContentDescription("Delete").performClick()
        assertEquals(7L, deleted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `row subtitle shows page progress with a bar only when the page count is known`() = runComposeUiTest {
        var known by androidx.compose.runtime.mutableStateOf(true)
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                HistoryScreen(
                    groups = HistoryGrouper.group(listOf(sample)),
                    query = "",
                    onQueryChange = {},
                    onReadChapter = {},
                    onDeleteItem = {},
                    onClearAll = {},
                    pageCountFor = { if (known) 20 else null },
                )
            }
        }
        onNodeWithText("Chapter 9 · Page 6 of 20", substring = true).assertExists()
        onNodeWithTag("history-progress-7", useUnmergedTree = true).assertExists()
        known = false
        waitForIdle()
        onNodeWithText("Chapter 9 · Page 6", substring = true).assertExists()
        onNodeWithTag("history-progress-7", useUnmergedTree = true).assertDoesNotExist()
    }
}
