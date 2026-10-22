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
    ToggleNavigationRail,
    OpenCommandPalette,
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

    /** Keys a focused element may legitimately consume (typing, deleting, Esc): no Ctrl/Alt, not an F-key. */
    fun isPlainKey(event: KeyEvent): Boolean =
        isPlainKey(event.key, event.isCtrlPressed || event.isMetaPressed, event.isAltPressed)

    fun isPlainKey(key: Key, ctrl: Boolean, alt: Boolean): Boolean =
        !ctrl && !alt && key !in BindableKeys.functionKeys

    /**
     * Window preview pass: only modifier/function-key chords, which must work everywhere. Plain keys are
     * deferred to [matchAfterDispatch] so whatever has focus (a text field deleting with Backspace) sees them first.
     */
    fun matchPreview(
        event: KeyEvent,
        isTextInputFocused: Boolean,
        bindings: KeyBindingMap<ShellShortcutAction> = DesktopKeyBindings.defaultGlobal,
    ): ShellShortcutAction? = if (isPlainKey(event)) null else matchAction(event, isTextInputFocused, bindings)

    /** Window post-dispatch pass: plain keys nobody consumed, and never while a text field has focus. */
    fun matchAfterDispatch(
        event: KeyEvent,
        isTextInputFocused: Boolean,
        bindings: KeyBindingMap<ShellShortcutAction> = DesktopKeyBindings.defaultGlobal,
    ): ShellShortcutAction? = if (!isPlainKey(event)) null else matchAction(event, isTextInputFocused, bindings)

    /**
     * True when a text field is focused: either one opted in via trackTextInputFocus, or Compose has an
     * active IME session on the focused AWT component (every Compose text field opens one while focused).
     */
    fun isTextInputActive(trackedFocus: Boolean): Boolean = trackedFocus || awtTextInputActive()

    private fun awtTextInputActive(): Boolean = runCatching {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.inputMethodRequests != null
    }.getOrDefault(false)

    fun destinationForIndex(index: Int): DesktopDestination? =
        DesktopDestination.entries.getOrNull(index)
}
