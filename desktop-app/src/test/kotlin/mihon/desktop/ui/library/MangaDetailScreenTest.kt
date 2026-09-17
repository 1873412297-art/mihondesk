package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MangaDetailScreenTest {
    @Test
    fun `chapter indicator follows live queue without reloading chapter metadata`() = runComposeUiTest {
        val queue = mutableStateOf(
            listOf(
                DesktopDownload(
                    72,
                    7,
                    107,
                    "Real title",
                    "Second in repository",
                    "/72",
                    status = DownloadStatus.DOWNLOADING,
                    progress = 0.35f,
                ),
                DesktopDownload(
                    999,
                    8,
                    107,
                    "Other manga",
                    "Other chapter",
                    "/999",
                    status = DownloadStatus.DOWNLOADING,
                    progress = 0.9f,
                ),
            ),
        )
        val base = detailState()
        base.withDownloadProgress(queue.value, true).chapterDownloads.keys shouldBe setOf(72L)
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(1280.dp, 1000.dp)) {
                    MangaDetailScreen(base.withDownloadProgress(queue.value, true), onBack = {})
                }
            }
        }
        val indicator = onNodeWithTag("chapter-download-indicator-72", useUnmergedTree = true)
        indicator.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current shouldBe 0.35f
        runOnIdle { queue.value = listOf(queue.value.first().copy(status = DownloadStatus.PAUSED)) }
        indicator.fetchSemanticsNode().config[SemanticsProperties.StateDescription] shouldBe "Paused"
        runOnIdle { queue.value = listOf(queue.value.first().copy(status = DownloadStatus.COMPLETED)) }
        indicator.fetchSemanticsNode().config[SemanticsProperties.StateDescription] shouldBe "Completed"
        runOnIdle { queue.value = emptyList() }
        indicator.fetchSemanticsNode().config[SemanticsProperties.StateDescription] shouldBe "Not downloaded"
        onNodeWithText("Second in repository").assertExists()
    }

    @Test
    fun `wide library shows adjacent detail with real metadata and chapter semantics in repository order`() =
        runComposeUiTest {
            setScreen(width = 1280.dp)

            onNodeWithTag("library-grid-pane").assertExists()
            onNodeWithTag("manga-detail-pane").assertExists()
            val libraryBounds = onNodeWithTag("library-grid-pane").getBoundsInRoot()
            val detailBounds = onNodeWithTag("manga-detail-pane").getBoundsInRoot()
            (libraryBounds.right - libraryBounds.left > detailBounds.right - detailBounds.left) shouldBe true
            onNodeWithTag("manga-detail-title").assertTextContains("Real title")
            onNodeWithText("Author name").assertExists()
            onNodeWithText("Real description").assertExists()
            onNodeWithText("Drama · Mystery").assertExists()
            onNodeWithText("Favorites").assertExists()
            onNodeWithText("Private notes").assertExists()
            onAllNodesWithTag("chapter-row")[0].assertTextContains("Second in repository")
            onAllNodesWithTag("chapter-row")[1].assertTextContains("First in repository")
            onNodeWithText("Read · Bookmarked · Page 7").assertExists()
        }

    @Test
    fun `narrow selection pushes detail and back returns to library`() = runComposeUiTest {
        var selected: Long? = 7
        var chapterId: Long? = null
        setScreen(width = 900.dp, onBack = { selected = null }, onRead = { chapterId = it })

        onNodeWithTag("library-grid-pane").assertDoesNotExist()
        onNodeWithTag("manga-detail-pane").assertExists()
        onNodeWithTag("manga-detail-back").performClick()

        selected shouldBe null
        onAllNodesWithTag("chapter-reader-action")[0].performClick()
        chapterId shouldBe 72L
    }

    @Test
    fun `missing detail does not render stale content`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MangaDetailScreen(MangaDetailUiState(manga = null, loading = false), onBack = {})
            }
        }

        onNodeWithText("Real title").assertDoesNotExist()
        onNodeWithTag("manga-detail-missing").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        width: androidx.compose.ui.unit.Dp,
        onBack: () -> Unit = {},
        onRead: (Long) -> Unit = {},
    ) {
        val item = LibraryManga(7, 107, "/7", "Real title", null, 2, 1, "Author name")
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    LibraryScreen(
                        state = LibraryUiState(false, items = listOf(item), selectedMangaId = 7),
                        detailState = detailState(),
                        onQueryChange = {},
                        onMangaSelected = {},
                        onBackFromDetail = onBack,
                        onReadChapter = onRead,
                        onImportBackup = {},
                        onImportLocal = {},
                    )
                }
            }
        }
    }

    private fun detailState() = MangaDetailUiState(
        manga = MangaDetails(
            7, 107, "/7", "Real title", null, "Author name", "Real description", "[\"Drama\",\"Mystery\"]",
            0, null, true, 0, 0, 0, "ALWAYS_UPDATE", 0, null, "[]", 0, "Private notes", true, "{}",
            listOf(CategoryRecord(1, "Favorites")),
        ),
        chapters = listOf(
            chapter(72, "Second in repository", read = true, bookmark = true, page = 7),
            chapter(71, "First in repository"),
        ),
        readerAvailability = mapOf(72L to ChapterReaderAvailability.Readable),
        loading = false,
    )

    private fun chapter(id: Long, name: String, read: Boolean = false, bookmark: Boolean = false, page: Long = 0) =
        LibraryChapter(id, 7, "/$id", name, null, read, bookmark, page, 0, 0, 1.0, 0, 0, 0, "{}")
}
