package mihon.desktop.ui.tasks

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
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadPage
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.PageStatus
import org.junit.jupiter.api.Test

class DownloadsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders empty state when queue is empty`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = emptyList(),
                    isRunning = false,
                    speedBytesPerSec = 0.0,
                    onPauseAll = {},
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = {},
                    onRetry = {},
                    onReadChapter = { _, _ -> },
                )
            }
        }

        onNodeWithText("No downloads in queue").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders download card and triggers pause and cancel actions`() = runComposeUiTest {
        var paused = false
        var cancelledId: Long? = null

        val download = DesktopDownload(
            chapterId = 555L,
            mangaId = 1L,
            sourceId = 2L,
            mangaTitle = "Chainsaw Man",
            chapterName = "Chapter 150",
            chapterUrl = "/ch150",
            status = DownloadStatus.DOWNLOADING,
            pages = listOf(
                DownloadPage(0, "p0", status = PageStatus.READY),
                DownloadPage(1, "p1", status = PageStatus.QUEUE),
            ),
            progress = 0.5f,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = listOf(download),
                    isRunning = true,
                    speedBytesPerSec = 1024.0 * 1024.0 * 2.5, // 2.5 MB/s
                    onPauseAll = { paused = true },
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = { cancelledId = it },
                    onRetry = {},
                    onReadChapter = { _, _ -> },
                )
            }
        }

        onNodeWithText("Chainsaw Man").assertExists()
        onNodeWithText("Chapter 150").assertExists()
        onNodeWithText("Downloading").assertExists()
        onNodeWithText("1 active items • 2.5 MB/s").assertExists()

        onNodeWithTag(DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG).performClick()
        paused shouldBe true

        onNodeWithText("Cancel").performClick()
        cancelledId shouldBe 555L
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `per-item pause and resume buttons trigger respective callbacks`() = runComposeUiTest {
        var pausedId: Long? = null
        var resumedId: Long? = null

        val queuedDownload = DesktopDownload(
            chapterId = 101L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "One Piece",
            chapterName = "Chapter 1000",
            chapterUrl = "/ch1000",
            status = DownloadStatus.QUEUED,
        )
        val pausedDownload = DesktopDownload(
            chapterId = 102L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "One Piece",
            chapterName = "Chapter 1001",
            chapterUrl = "/ch1001",
            status = DownloadStatus.PAUSED,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = listOf(queuedDownload, pausedDownload),
                    isRunning = true,
                    speedBytesPerSec = 0.0,
                    onPauseAll = {},
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = {},
                    onRetry = {},
                    onReadChapter = { _, _ -> },
                    onPause = { pausedId = it },
                    onResume = { resumedId = it },
                )
            }
        }

        onNodeWithTag("download-pause-101").performClick()
        pausedId shouldBe 101L

        onNodeWithTag("download-resume-102").performClick()
        resumedId shouldBe 102L
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `move up and move down buttons trigger respective callbacks`() = runComposeUiTest {
        var movedUpId: Long? = null
        var movedDownId: Long? = null

        val item1 = DesktopDownload(
            chapterId = 201L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Naruto",
            chapterName = "Chapter 1",
            chapterUrl = "/ch1",
            status = DownloadStatus.QUEUED,
        )
        val item2 = DesktopDownload(
            chapterId = 202L,
            mangaId = 1L,
            sourceId = 1L,
            mangaTitle = "Naruto",
            chapterName = "Chapter 2",
            chapterUrl = "/ch2",
            status = DownloadStatus.QUEUED,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = listOf(item1, item2),
                    isRunning = true,
                    speedBytesPerSec = 0.0,
                    onPauseAll = {},
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = {},
                    onRetry = {},
                    onReadChapter = { _, _ -> },
                    onMoveUp = { movedUpId = it },
                    onMoveDown = { movedDownId = it },
                )
            }
        }

        // item 1 can move down
        onNodeWithTag("download-move-down-201").performClick()
        movedDownId shouldBe 201L

        // item 2 can move up
        onNodeWithTag("download-move-up-202").performClick()
        movedUpId shouldBe 202L
    }
}
