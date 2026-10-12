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
    /**
     * Resolves a shell shortcut through the (possibly user-customised) [bindings]. Plain keys without
     * Ctrl/Alt (Backspace, Esc, ?) are ignored while a text field has focus so typing stays typing;
     * function keys such as F5 still work there.
     */
    fun matchAction(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        isCtrlPressed: Boolean = false,
        isAltPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        isMetaPressed: Boolean = false,
        isTextInputFocused: Boolean = false,
        bindings: KeyBindingMap<ShellShortcutAction> = DesktopKeyBindings.defaultGlobal,
    ): ShellShortcutAction? {
        if (type != KeyEventType.KeyDown) return null
        val chord = KeyChord(
            key = key,
            ctrl = isCtrlPressed || isMetaPressed,
            alt = isAltPressed,
            shift = isShiftPressed,
        )
        if (isTextInputFocused && !chord.ctrl && !chord.alt && !chord.isFunctionKey) return null
        return bindings.match(chord)
    }

    fun matchAction(
        event: KeyEvent,
        isTextInputFocused: Boolean = false,
        bindings: KeyBindingMap<ShellShortcutAction> = DesktopKeyBindings.defaultGlobal,
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
            bindings = bindings,
        )
    }

    fun destinationForIndex(index: Int): DesktopDestination? =
        DesktopDestination.entries.getOrNull(index)
}
