package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class DownloadExperienceTest {
    private val raw = "1 page(s) failed; page 3: mihon.extension.ipc.IpcException: " +
        "Brokered HTTP request failed: TLS: Remote host terminated the handshake"
    private fun item(id: Long, title: String, status: DownloadStatus) = DesktopDownload(
        id,
        1,
        2,
        title,
        "Chapter $id",
        "/$id",
        status = status,
        error = raw.takeIf { status == DownloadStatus.ERROR },
    )

    @ParameterizedTest
    @CsvSource("1024,false", "1024,true", "640,false", "640,true")
    fun `error actions remain visible in desktop light and dark layouts`(width: Int, dark: Boolean) = runComposeUiTest {
        System.getenv("MIHON_EXPERIENCE_APP")?.let {
            val origin = Path.of(
                Class.forName("mihon.desktop.ui.tasks.DownloadsScreenKt").protectionDomain.codeSource.location.toURI(),
            )
            check(origin.startsWith(Path.of(it))) { "Expected installed UI, got $origin" }
        }
        var retries = 0
        var retried: Long? = null
        var cancelled: Long? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, isAmoled = dark) {
                    Box(Modifier.requiredSize(width.dp, 720.dp)) {
                        DownloadsScreen(
                            listOf(item(1, "测试漫画：安全连接失败后保留已下载的图片并支持重试", DownloadStatus.ERROR)), false, 0.0,
                            {
                            }, {
                            }, {
                            }, {
                                cancelled = it
                            }, { retried = it }, { _, _ -> }, onRetryAllFailed = { retries++ },
                        )
                    }
                }
            }
        }
        onNodeWithTag("downloads-retry-failed").assertIsDisplayed().performClick()
        onNodeWithTag("download-retry-1").assertIsDisplayed().performClick()
        onNodeWithTag("download-cancel-1").assertIsDisplayed().performClick()
        retries shouldBe 1
        retried shouldBe 1L
        cancelled shouldBe 1L
        System.getenv("MIHON_EXPERIENCE_EVIDENCE")?.let { directory ->
            val target = Path.of(directory)
            Files.createDirectories(target)
            Image.makeFromBitmap(
                onNodeWithTag(DOWNLOADS_SCREEN_TEST_TAG).captureToImage().asSkiaBitmap(),
            ).use { image ->
                image.encodeToData()!!.use {
                    Files.write(target.resolve("downloads-$width-${if (dark) "dark" else "light"}.png"), it.bytes)
                }
            }
        }
    }

    @Test
    fun `TLS failure has a Chinese explanation and expandable original details`() = runComposeUiTest {
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText() = copied
            override fun setText(annotatedString: AnnotatedString) {
                copied = annotatedString
            }
        }
        setContent {
            CompositionLocalProvider(
                LocalStrings provides SimplifiedChineseStrings,
                LocalClipboardManager provides clipboard,
            ) {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    DownloadsScreen(
                        listOf(item(1, "Example", DownloadStatus.ERROR)), false, 0.0,
                        {}, {}, {}, {}, {}, { _, _ -> },
                    )
                }
            }
        }
        onNodeWithText("安全连接建立失败").assertExists()
        onNodeWithText(raw).assertDoesNotExist()
        onNodeWithTag("download-error-1-details").performClick()
        onNodeWithText(raw).assertExists()
        onNodeWithTag("download-error-1-copy").assertExists()
        onNodeWithTag("download-error-1-copy").performClick()
        copied?.text shouldBe raw
        onNodeWithText("已复制").assertExists()
        onNodeWithTag("download-error-1-details").performClick()
        onNodeWithText(raw).assertDoesNotExist()
    }

    @Test
    fun `filters and search have a recoverable empty state`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    DownloadsScreen(
                        listOf(
                            item(1, "Failed book", DownloadStatus.ERROR),
                            item(2, "Waiting book", DownloadStatus.QUEUED),
                        ),
                        false, 0.0,
                        {}, {}, {}, {}, {}, { _, _ -> },
                    )
                }
            }
        }
        onNodeWithTag("download-filter-ERROR").performClick()
        onNodeWithText("Failed book").assertExists()
        onNodeWithText("Waiting book").assertDoesNotExist()
        onNodeWithTag("downloads-search").performTextInput("unmatched")
        onNodeWithText("没有符合条件的下载任务").assertExists()
        onNodeWithText("清除筛选").performClick()
        onNodeWithText("Waiting book").assertExists()
    }
}
