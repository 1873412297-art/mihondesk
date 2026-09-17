package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class DownloadAnimationTest {
    @Test
    fun `progress fills through intermediate frames and retry resets the old progress`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val item = mutableStateOf(download(DownloadStatus.DOWNLOADING, 0.2f))
        setDownloadContent { item.value }
        mainClock.advanceTimeByFrame()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("下载").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(Color.White, titleLayouts.single().layoutInput.style.color)
        fun progress(
            tag: String,
        ) = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        val indicatorTag = "download-status-indicator-5"
        assertEquals(0.2f, progress(indicatorTag), 0.001f)
        saveRendering("01-progress-20")
        runOnIdle { item.value = download(DownloadStatus.DOWNLOADING, 0.8f) }
        mainClock.advanceTimeBy(64)
        assertTrue(progress(indicatorTag) > 0.2f && progress(indicatorTag) < 0.8f)
        assertTrue(progress("download-progress-5") > 0.2f && progress("download-progress-5") < 0.8f)
        saveRendering("02-progress-intermediate")
        mainClock.advanceTimeBy(2000)
        assertEquals(0.8f, progress(indicatorTag), 0.001f)
        saveRendering("03-progress-80")
        runOnIdle { item.value = item.value.copy(status = DownloadStatus.ERROR) }
        mainClock.advanceTimeByFrame()
        assertEquals(
            "出错了",
            onNodeWithTag(indicatorTag).fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
        saveRendering("04-error")
        runOnIdle { item.value = item.value.copy(status = DownloadStatus.DOWNLOADING, progress = 0f) }
        mainClock.advanceTimeByFrame()
        assertEquals(
            ProgressBarRangeInfo.Indeterminate,
            onNodeWithTag(indicatorTag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo],
        )
        runOnIdle { item.value = download(DownloadStatus.COMPLETED, 1f) }
        mainClock.advanceTimeByFrame()
        assertEquals(1f, progress(indicatorTag), 0.001f)
        saveRendering("05-complete")
    }

    @Test
    fun `queued pixels animate but paused pixels stay unchanged`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val item = mutableStateOf(download(DownloadStatus.QUEUED, 0f))
        setDownloadContent { item.value }
        mainClock.advanceTimeBy(64)
        val queuedBefore = indicatorPixels()
        mainClock.advanceTimeBy(240)
        assertTrue(!queuedBefore.contentEquals(indicatorPixels()), "Queued indicator must move")
        runOnIdle { item.value = item.value.copy(status = DownloadStatus.PAUSED, progress = 0.4f) }
        mainClock.advanceTimeBy(64)
        val pausedBefore = indicatorPixels()
        saveRendering("06-paused")
        mainClock.advanceTimeBy(500)
        assertTrue(pausedBefore.contentEquals(indicatorPixels()), "Paused indicator must remain still")
    }

    @Test
    fun `queue indicator becomes still when downloader pauses and finishes as completed`() = runComposeUiTest {
        val running = mutableStateOf(true)
        val status = mutableStateOf(DownloadStatus.QUEUED)
        setContent {
            ProvideDesktopStrings(AppLanguage.SimplifiedChinese) {
                MihonDesktopTheme(themeMode = ThemeMode.Dark, isAmoled = true) {
                    Box(Modifier.requiredSize(1024.dp, 720.dp)) {
                        DownloadsScreen(
                            queue = listOf(
                                DesktopDownload(5, 1, 2, "Test manga", "Chapter 1", "/1", status = status.value),
                            ),
                            isRunning = running.value, speedBytesPerSec = 0.0,
                            onPauseAll = {}, onResumeAll = {}, onClearCompleted = {}, onCancel = {}, onRetry = {},
                            onReadChapter = { _, _ -> },
                        )
                    }
                }
            }
        }
        val indicator = onNodeWithTag("download-status-indicator-5")
        indicator.assertIsDisplayed()
        assertEquals(
            ProgressBarRangeInfo.Indeterminate,
            indicator.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo],
        )
        runOnIdle { running.value = false }
        assertEquals("已暂停", indicator.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        runOnIdle { status.value = DownloadStatus.COMPLETED }
        assertEquals("已完成", indicator.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    private fun download(status: DownloadStatus, progress: Float) = DesktopDownload(
        5, 1, 2, "下载动画测试", "第 1 章", "/1", status = status, progress = progress,
        pages = List(10) {
            mihon.desktop.download.DownloadPage(
                it,
                "/$it",
                status = if (it <
                    progress * 10
                ) {
                    mihon.desktop.download.PageStatus.READY
                } else {
                    mihon.desktop.download.PageStatus.QUEUE
                },
            )
        },
    )

    private fun ComposeUiTest.setDownloadContent(item: () -> DesktopDownload) {
        System.getenv("MIHON_DOWNLOAD_ANIMATION_APP")?.let { directory ->
            val code = Path.of(
                Class.forName(
                    "mihon.desktop.ui.common.DownloadIndicatorKt",
                ).protectionDomain.codeSource.location.toURI(),
            )
            assertTrue(code.startsWith(Path.of(directory)), "Expected packaged code: $code")
            println("DOWNLOAD_ANIMATION_CLASSES=$code")
        }
        setContent {
            ProvideDesktopStrings(AppLanguage.SimplifiedChinese) {
                MihonDesktopTheme(themeMode = ThemeMode.Dark, isAmoled = true) {
                    Box(Modifier.requiredSize(1024.dp, 720.dp)) {
                        DownloadsScreen(
                            queue = listOf(item()), isRunning = true, speedBytesPerSec = 1024.0,
                            onPauseAll = {}, onResumeAll = {}, onClearCompleted = {}, onCancel = {}, onRetry = {},
                            onReadChapter = { _, _ -> },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.indicatorPixels(): ByteArray = Image.makeFromBitmap(
        onNodeWithTag("download-status-indicator-5").captureToImage().asSkiaBitmap(),
    ).use { image -> image.encodeToData()!!.use { it.bytes } }

    private fun ComposeUiTest.saveRendering(name: String) {
        System.getenv("MIHON_DOWNLOAD_ANIMATION_EVIDENCE")?.let { directory ->
            val target = Path.of(directory)
            Files.createDirectories(target)
            Image.makeFromBitmap(
                onNodeWithTag(DOWNLOADS_SCREEN_TEST_TAG).captureToImage().asSkiaBitmap(),
            ).use { image ->
                image.encodeToData()!!.use { Files.write(target.resolve("$name.png"), it.bytes) }
            }
        }
    }
}
