package mihon.desktop.ui.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.input.ReaderShortcutAction
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyChord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ShortcutSettingsPaneTest {

    @TempDir
    lateinit var tempDir: Path

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `rebinding to a used key warns and replaces, reset restores defaults`() = runComposeUiTest {
        val store = DesktopKeyBindingStore(DesktopPreferenceStore(tempDir.resolve("preferences.properties")))
        setContent { ShortcutSettingsPane(store) }

        onNodeWithTag("shortcut-add-reader-TOGGLE_BOOKMARK").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("shortcut-capture-area").performKeyInput { pressKey(Key.N) }
        waitForIdle()
        onNodeWithTag("shortcut-conflict").assertIsDisplayed()
        onNodeWithTag("shortcut-keep-both").assertDoesNotExist()
        onNodeWithTag("shortcut-replace").performClick()
        waitForIdle()

        val saved = store.load()
        saved.reader.match(KeyChord(Key.N)) shouldBe ReaderShortcutAction.TOGGLE_BOOKMARK
        saved.reader.chordsFor(ReaderShortcutAction.NEXT_CHAPTER) shouldBe listOf(KeyChord(Key.RightBracket))

        onNodeWithTag("shortcut-reset-all").performScrollTo().performClick()
        waitForIdle()
        store.load() shouldBe DesktopKeyBindings.Default
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `removing a chord persists immediately`() = runComposeUiTest {
        val store = DesktopKeyBindingStore(DesktopPreferenceStore(tempDir.resolve("preferences.properties")))
        setContent { ShortcutSettingsPane(store) }

        onNodeWithTag("shortcut-chip-reader-FULLSCREEN-F11").performScrollTo().performClick()
        waitForIdle()
        store.load().reader.chordsFor(ReaderShortcutAction.FULLSCREEN) shouldBe listOf(KeyChord(Key.F))
        onNodeWithTag("shortcut-reset-reader-FULLSCREEN").performScrollTo().performClick()
        waitForIdle()
        store.load() shouldBe DesktopKeyBindings.Default
    }
}
