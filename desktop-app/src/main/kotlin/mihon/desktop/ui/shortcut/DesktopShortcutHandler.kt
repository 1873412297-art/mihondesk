package mihon.desktop.ui.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import mihon.desktop.navigation.DesktopDestination

enum class ShellShortcutAction {
    NavigateDestination1,
    NavigateDestination2,
    NavigateDestination3,
    NavigateDestination4,
    NavigateDestination5,
    NavigateDestination6,
    NavigateDestination7,
    NavigateDestination8,
    FocusSearch,
    RefreshCurrentPage,
    NavigateBack,
    ShowShortcutsHelp,
}

object DesktopShortcutMatcher {
    fun matchAction(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        isCtrlPressed: Boolean = false,
        isAltPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        isMetaPressed: Boolean = false,
        isTextInputFocused: Boolean = false,
    ): ShellShortcutAction? {
        if (type != KeyEventType.KeyDown) return null
        val ctrl = isCtrlPressed || isMetaPressed
        val alt = isAltPressed
        val shift = isShiftPressed

        // Ctrl + 1..8
        if (ctrl && !alt && !shift) {
            when (key) {
                Key.One, Key.NumPad1 -> return ShellShortcutAction.NavigateDestination1
                Key.Two, Key.NumPad2 -> return ShellShortcutAction.NavigateDestination2
                Key.Three, Key.NumPad3 -> return ShellShortcutAction.NavigateDestination3
                Key.Four, Key.NumPad4 -> return ShellShortcutAction.NavigateDestination4
                Key.Five, Key.NumPad5 -> return ShellShortcutAction.NavigateDestination5
                Key.Six, Key.NumPad6 -> return ShellShortcutAction.NavigateDestination6
                Key.Seven, Key.NumPad7 -> return ShellShortcutAction.NavigateDestination7
                Key.Eight, Key.NumPad8 -> return ShellShortcutAction.NavigateDestination8
            }
        }

        // Ctrl + F
        if (ctrl && !alt && !shift && key == Key.F) {
            return ShellShortcutAction.FocusSearch
        }

        // F5 or Ctrl + R
        if ((key == Key.F5 && !ctrl && !alt) || (ctrl && !alt && !shift && key == Key.R)) {
            return ShellShortcutAction.RefreshCurrentPage
        }

        // Alt + Left
        if (alt && !ctrl && key == Key.DirectionLeft) {
            return ShellShortcutAction.NavigateBack
        }

        // Backspace (only if text input NOT focused)
        if (!ctrl && !alt && !shift && key == Key.Backspace && !isTextInputFocused) {
            return ShellShortcutAction.NavigateBack
        }

        // Ctrl + /
        if (ctrl && !alt && key == Key.Slash) {
            return ShellShortcutAction.ShowShortcutsHelp
        }

        // ? (Shift + /), only if text input NOT focused
        if (shift && !ctrl && !alt && key == Key.Slash && !isTextInputFocused) {
            return ShellShortcutAction.ShowShortcutsHelp
        }

        // Escape (when text input NOT focused)
        if (!ctrl && !alt && !shift && key == Key.Escape && !isTextInputFocused) {
            return ShellShortcutAction.NavigateBack
        }

        return null
    }

    fun matchAction(
        event: KeyEvent,
        isTextInputFocused: Boolean = false,
    ): ShellShortcutAction? {
        if (event.type != KeyEventType.KeyDown) return null
        return matchAction(
            key = event.key,
            type = event.type,
            isCtrlPressed = event.isCtrlPressed,
            isAltPressed = event.isAltPressed,
            isShiftPressed = event.isShiftPressed,
            isMetaPressed = event.isMetaPressed,
            isTextInputFocused = isTextInputFocused,
        )
    }

    fun destinationForIndex(index: Int): DesktopDestination? =
        DesktopDestination.entries.getOrNull(index)
}
