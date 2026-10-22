package mihon.desktop.ui.palette

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.ui.settings.SettingsSection
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {
    private fun manga(id: Long, title: String, lastRead: Long = 0) = LibraryManga(
        id = id,
        sourceId = 1,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = 1,
        unreadCount = 0,
        lastRead = lastRead,
    )

    private val library = listOf(manga(1, "葬送的芙莉莲", 10), manga(2, "One Piece", 20), manga(3, "迷宫饭", 5))

    @Test
    fun `typing filters the bookshelf and Enter opens the best match`() = runComposeUiTest {
        var chosen: PaletteTarget? = null
        var dismissed = false
        setContent {
            CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                MihonDesktopTheme {
                    Box(Modifier.requiredSize(1000.dp, 800.dp)) {
                        CommandPalette(library, null, onDismiss = { dismissed = true }, onSelect = { chosen = it })
                    }
                }
            }
        }
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performTextInput("芙莉")
        waitForIdle()
        onNodeWithTag("palette-item-manga-1").assertExists()
        onNodeWithTag("palette-item-manga-2").assertDoesNotExist()
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(PaletteTarget.Manga(1), chosen)
        assertTrue(dismissed)
    }

    @Test
    fun `arrow keys move through commands and settings`() = runComposeUiTest {
        var chosen: PaletteTarget? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                MihonDesktopTheme {
                    Box(Modifier.requiredSize(1000.dp, 800.dp)) {
                        CommandPalette(emptyList(), null, onDismiss = {}, onSelect = { chosen = it })
                    }
                }
            }
        }
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performTextInput("阅读")
        waitForIdle()
        onNodeWithTag("palette-item-settings-Reader").assertExists()
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(PaletteTarget.Settings(SettingsSection.Reader), chosen)
    }

    @Test
    fun `source results are merged and grouped under each source`() = runComposeUiTest {
        val alpha = SourceDescriptor(id = 7, name = "Alpha源", lang = "zh", className = "A")
        val beta = SourceDescriptor(id = 8, name = "Beta源", lang = "zh", className = "B")
        val search = PaletteSourceSearch(
            sources = { listOf(alpha, beta) },
            search = { id, q -> listOf(SManga(url = "/$id", title = "$q@$id")) },
        )
        var chosen: PaletteTarget? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                MihonDesktopTheme {
                    Box(Modifier.requiredSize(1000.dp, 800.dp)) {
                        CommandPalette(emptyList(), search, onDismiss = {}, onSelect = { chosen = it })
                    }
                }
            }
        }
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performTextInput("海贼")
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(
                androidx.compose.ui.test.hasTestTag("palette-item-source-8-海贼@8"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("palette-section-source-7", useUnmergedTree = true).assertExists()
        onNodeWithTag("palette-section-source-8", useUnmergedTree = true).assertExists()
        onNodeWithTag(COMMAND_PALETTE_INPUT_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(PaletteTarget.SourceManga(beta, SManga(url = "/8", title = "海贼@8")), chosen)
    }
}
