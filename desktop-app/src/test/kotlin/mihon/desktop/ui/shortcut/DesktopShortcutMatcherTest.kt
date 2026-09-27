package mihon.desktop.ui.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopShortcutMatcherTest {

    @Test
    fun `ctrl 1 through 8 matches destinations 1 through 8`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.One,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.NavigateDestination1

        DesktopShortcutMatcher.matchAction(
            key = Key.Five,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.NavigateDestination5

        DesktopShortcutMatcher.matchAction(
            key = Key.Eight,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.NavigateDestination8
    }

    @Test
    fun `ctrl F matches focus search`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.F,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.FocusSearch
    }

    @Test
    fun `f5 and ctrl R match refresh current page`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.F5,
        ) shouldBe ShellShortcutAction.RefreshCurrentPage

        DesktopShortcutMatcher.matchAction(
            key = Key.R,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.RefreshCurrentPage
    }

    @Test
    fun `alt left matches back navigation`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.DirectionLeft,
            isAltPressed = true,
        ) shouldBe ShellShortcutAction.NavigateBack
    }

    @Test
    fun `backspace matches back navigation when not in text input`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.Backspace,
            isTextInputFocused = false,
        ) shouldBe ShellShortcutAction.NavigateBack

        DesktopShortcutMatcher.matchAction(
            key = Key.Backspace,
            isTextInputFocused = true,
        ) shouldBe null
    }

    @Test
    fun `question mark and ctrl slash open shortcuts cheatsheet`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.Slash,
            isCtrlPressed = true,
        ) shouldBe ShellShortcutAction.ShowShortcutsHelp

        DesktopShortcutMatcher.matchAction(
            key = Key.Slash,
            isShiftPressed = true,
            isTextInputFocused = false,
        ) shouldBe ShellShortcutAction.ShowShortcutsHelp

        // When text field is focused, typing '?' should not open cheatsheet
        DesktopShortcutMatcher.matchAction(
            key = Key.Slash,
            isShiftPressed = true,
            isTextInputFocused = true,
        ) shouldBe null
    }

    @Test
    fun `escape matches back navigation when text input not focused`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.Escape,
            isTextInputFocused = false,
        ) shouldBe ShellShortcutAction.NavigateBack

        DesktopShortcutMatcher.matchAction(
            key = Key.Escape,
            isTextInputFocused = true,
        ) shouldBe null
    }

    @Test
    fun `key up events are ignored`() {
        DesktopShortcutMatcher.matchAction(
            key = Key.One,
            type = KeyEventType.KeyUp,
            isCtrlPressed = true,
        ) shouldBe null
    }
}
