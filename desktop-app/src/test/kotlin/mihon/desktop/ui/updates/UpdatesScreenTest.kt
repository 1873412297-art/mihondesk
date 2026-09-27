package mihon.desktop.ui.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.updates.UpdatedChapterItem
import org.junit.jupiter.api.Test

class UpdatesScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders empty state when no updates`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                UpdatesScreen(
                    updatedChapters = emptyList(),
                    isUpdating = false,
                    lastResult = null,
                    onCheckForUpdates = {},
                    onReadChapter = {},
                )
            }
        }

        onNodeWithText("No recent chapter updates").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `upcoming entry point invokes callback`() = runComposeUiTest {
        var opened = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                UpdatesScreen(
                    updatedChapters = emptyList(),
                    isUpdating = false,
                    lastResult = null,
                    onCheckForUpdates = {},
                    onReadChapter = {},
                    onOpenUpcoming = { opened = true },
                )
            }
        }

        onNodeWithText("Upcoming").assertExists()
        onNodeWithTag(UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG).performClick()
        opened shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders updated chapters and clicks check and read`() = runComposeUiTest {
        var checkedForUpdates = false
        var readChapterId: Long? = null

        val updates = listOf(
            UpdatedChapterItem(
                mangaId = 1L,
                chapterId = 777L,
                mangaTitle = "Jujutsu Kaisen",
                chapterName = "Chapter 260",
                chapterNumber = 260.0,
                dateFetch = System.currentTimeMillis(),
            ),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                UpdatesScreen(
                    updatedChapters = updates,
                    isUpdating = false,
                    lastResult = null,
                    onCheckForUpdates = { checkedForUpdates = true },
                    onReadChapter = { readChapterId = it },
                )
            }
        }

        onNodeWithText("Jujutsu Kaisen").assertExists()
        onNodeWithText("Chapter 260").assertExists()
        onNodeWithText("Today").assertExists()

        onNodeWithTag(UPDATES_CHECK_NOW_BUTTON_TEST_TAG).performClick()
        checkedForUpdates shouldBe true

        onNodeWithText("Read").performClick()
        readChapterId shouldBe 777L
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `unread undownloaded row exposes mark read and download actions`() = runComposeUiTest {
        var markedRead: Pair<Long, Long>? = null
        var downloaded: Pair<Long, Long>? = null
        val updates = listOf(
            UpdatedChapterItem(
                mangaId = 9L,
                chapterId = 55L,
                mangaTitle = "Dandadan",
                chapterName = "Chapter 12",
                chapterNumber = 12.0,
                dateFetch = System.currentTimeMillis(),
                read = false,
                downloaded = false,
            ),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                UpdatesScreen(
                    updatedChapters = updates,
                    isUpdating = false,
                    lastResult = null,
                    onCheckForUpdates = {},
                    onReadChapter = {},
                    onMarkChapterRead = { mangaId, chapterId -> markedRead = mangaId to chapterId },
                    onDownloadChapter = { mangaId, chapterId -> downloaded = mangaId to chapterId },
                )
            }
        }

        onNodeWithTag(UPDATES_MARK_READ_TEST_TAG_PREFIX + 55L).performClick()
        markedRead shouldBe (9L to 55L)
        onNodeWithTag(UPDATES_DOWNLOAD_TEST_TAG_PREFIX + 55L).performClick()
        downloaded shouldBe (9L to 55L)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `read and downloaded rows hide their row actions`() = runComposeUiTest {
        val updates = listOf(
            UpdatedChapterItem(
                mangaId = 9L,
                chapterId = 55L,
                mangaTitle = "Dandadan",
                chapterName = "Chapter 12",
                chapterNumber = 12.0,
                dateFetch = System.currentTimeMillis(),
                read = true,
                downloaded = true,
            ),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                UpdatesScreen(
                    updatedChapters = updates,
                    isUpdating = false,
                    lastResult = null,
                    onCheckForUpdates = {},
                    onReadChapter = {},
                )
            }
        }

        onNodeWithTag(UPDATES_MARK_READ_TEST_TAG_PREFIX + 55L).assertDoesNotExist()
        onNodeWithTag(UPDATES_DOWNLOAD_TEST_TAG_PREFIX + 55L).assertDoesNotExist()
    }
}
