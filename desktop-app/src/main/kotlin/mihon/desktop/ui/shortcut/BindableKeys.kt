package mihon.desktop.ui.shortcut
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.input.ReaderShortcutAction

object BindableKeys {
    private data class Entry(val name: String, val display: String, val key: Key)

    private val entries: List<Entry> = buildList {
        listOf(
            Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
            Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
        ).forEachIndexed { index, key ->
            val letter = ('A' + index).toString()
            add(Entry(letter, letter, key))
        }
        listOf(
            Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
            Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
        ).forEachIndexed { index, key -> add(Entry("$index", "$index", key)) }
        listOf(
            Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
            Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
        ).forEachIndexed { index, key -> add(Entry("NumPad$index", "Num $index", key)) }
        FUNCTION_KEYS.forEachIndexed { index, key -> add(Entry("F${index + 1}", "F${index + 1}", key)) }
        add(Entry("Left", "←", Key.DirectionLeft))
        add(Entry("Right", "→", Key.DirectionRight))
        add(Entry("Up", "↑", Key.DirectionUp))
        add(Entry("Down", "↓", Key.DirectionDown))
        add(Entry("PageUp", "PageUp", Key.PageUp))
        add(Entry("PageDown", "PageDown", Key.PageDown))
        add(Entry("Home", "Home", Key.MoveHome))
        add(Entry("End", "End", Key.MoveEnd))
        add(Entry("Space", "Space", Key.Spacebar))
        add(Entry("Escape", "Esc", Key.Escape))
        add(Entry("Backspace", "Backspace", Key.Backspace))
        add(Entry("Enter", "Enter", Key.Enter))
        add(Entry("Tab", "Tab", Key.Tab))
        add(Entry("Delete", "Delete", Key.Delete))
        add(Entry("Insert", "Insert", Key.Insert))
        add(Entry("Plus", "+", Key.Plus))
        add(Entry("Equals", "=", Key.Equals))
        add(Entry("Minus", "-", Key.Minus))
        add(Entry("NumPadAdd", "Num +", Key.NumPadAdd))
        add(Entry("NumPadSubtract", "Num -", Key.NumPadSubtract))
        add(Entry("NumPadMultiply", "Num *", Key.NumPadMultiply))
        add(Entry("NumPadDivide", "Num /", Key.NumPadDivide))
        add(Entry("LeftBracket", "[", Key.LeftBracket))
        add(Entry("RightBracket", "]", Key.RightBracket))
        add(Entry("Slash", "/", Key.Slash))
        add(Entry("Backslash", "\\", Key.Backslash))
        add(Entry("Comma", ",", Key.Comma))
        add(Entry("Period", ".", Key.Period))
        add(Entry("Semicolon", ";", Key.Semicolon))
        add(Entry("Apostrophe", "'", Key.Apostrophe))
        add(Entry("Grave", "`", Key.Grave))
        add(Entry("Help", "Help", Key.Help))
    }

    val functionKeys: Set<Key> get() = FUNCTION_KEYS.toSet()

    /** Keys that only modify another key; pressing one alone never makes a chord. */
    val modifierKeys: Set<Key> = setOf(
        Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.ShiftRight, Key.AltLeft, Key.AltRight,
        Key.MetaLeft, Key.MetaRight, Key.CapsLock, Key.NumLock, Key.ScrollLock, Key.Function, Key.Unknown,
    )

    fun nameOf(key: Key): String = entries.firstOrNull { it.key == key }?.name ?: "$RAW_PREFIX${key.keyCode}"

    fun displayOf(key: Key): String = entries.firstOrNull { it.key == key }?.display
        ?: key.toString().removePrefix("Key: ").ifBlank { nameOf(key) }

    fun keyOf(name: String): Key? {
        entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.key }
        if (name.startsWith(RAW_PREFIX, ignoreCase = true)) {
            return name.substring(RAW_PREFIX.length).toLongOrNull()?.let(::Key)
        }
        return null
    }

    private const val RAW_PREFIX = "Code"
}
