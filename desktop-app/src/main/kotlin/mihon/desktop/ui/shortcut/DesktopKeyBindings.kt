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

/**
 * One key plus modifiers. Meta (Windows/Cmd) is folded into [ctrl] so both behave the same, matching
 * how the shell shortcuts have always treated them.
 */
data class KeyChord(
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    val isFunctionKey: Boolean get() = key in BindableKeys.functionKeys

    /** Stable text form stored in preferences, e.g. `Ctrl+Shift+Slash`. */
    fun serialize(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        append(BindableKeys.nameOf(key))
    }

    /** Human-readable label for dialogs and settings, e.g. `Ctrl + Shift + /`. */
    fun displayLabel(): String = listOfNotNull(
        "Ctrl".takeIf { ctrl },
        "Alt".takeIf { alt },
        "Shift".takeIf { shift },
        BindableKeys.displayOf(key),
    ).joinToString(" + ")

    companion object {
        fun parse(text: String): KeyChord? {
            val parts = text.trim().split('+').map { it.trim() }
            if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
            var ctrl = false
            var alt = false
            var shift = false
            parts.dropLast(1).forEach { modifier ->
                when (modifier.lowercase()) {
                    "ctrl", "control", "meta", "cmd" -> ctrl = true
                    "alt" -> alt = true
                    "shift" -> shift = true
                    else -> return null
                }
            }
            val key = BindableKeys.keyOf(parts.last()) ?: return null
            return KeyChord(key, ctrl = ctrl, alt = alt, shift = shift)
        }

        fun from(event: KeyEvent): KeyChord = KeyChord(
            key = event.key,
            ctrl = event.isCtrlPressed || event.isMetaPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
        )
    }
}

/** Names for the keys the key map can store and show. Unknown keys fall back to their raw code. */
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

private val FUNCTION_KEYS = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
    Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
)

private fun chord(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
    KeyChord(key, ctrl = ctrl, alt = alt, shift = shift)

/**
 * An action → key chords table for one scope. A chord pressed with Shift that is not bound on its own
 * falls back to the same chord without Shift, so e.g. Shift+→ still turns the page as it always did.
 */
class KeyBindingMap<A : Enum<A>>(
    private val actions: List<A>,
    bindings: Map<A, List<KeyChord>>,
) {
    val bindings: Map<A, List<KeyChord>> = actions.associateWith { action ->
        bindings[action].orEmpty().distinct()
    }

    private val index: Map<KeyChord, A> = buildMap {
        actions.forEach { action -> this@KeyBindingMap.bindings.getValue(action).forEach { putIfAbsent(it, action) } }
    }

    fun chordsFor(action: A): List<KeyChord> = bindings.getValue(action)

    fun match(chord: KeyChord): A? = index[chord] ?: if (chord.shift) index[chord.copy(shift = false)] else null

    /** Actions (other than [except]) that this exact chord is already bound to. */
    fun actionsBoundTo(chord: KeyChord, except: A? = null): List<A> =
        actions.filter { it != except && chord in bindings.getValue(it) }

    /** Chords bound to more than one action. Never non-empty for maps edited through the settings UI. */
    fun conflicts(): Map<KeyChord, List<A>> = actions
        .flatMap { action -> bindings.getValue(action).map { it to action } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size > 1 }

    fun withChords(action: A, chords: List<KeyChord>): KeyBindingMap<A> =
        KeyBindingMap(actions, bindings + (action to chords))

    fun withoutChord(chord: KeyChord): KeyBindingMap<A> =
        KeyBindingMap(actions, bindings.mapValues { (_, chords) -> chords - chord })

    /** Adds [chord] to [action]; when [replace] is true the chord is first removed from every other action. */
    fun assign(action: A, chord: KeyChord, replace: Boolean): KeyBindingMap<A> {
        val base = if (replace) withoutChord(chord) else this
        val existing = base.chordsFor(action)
        return if (chord in existing) base else base.withChords(action, existing + chord)
    }

    fun isDefault(action: A, defaults: KeyBindingMap<A>): Boolean = chordsFor(action) == defaults.chordsFor(action)

    override fun equals(other: Any?): Boolean = other is KeyBindingMap<*> && other.bindings == bindings

    override fun hashCode(): Int = bindings.hashCode()

    override fun toString(): String = "KeyBindingMap($bindings)"
}

/** The full, persisted keyboard map: shell-wide shortcuts and reader shortcuts. */
data class DesktopKeyBindings(
    val global: KeyBindingMap<ShellShortcutAction> = defaultGlobal,
    val reader: KeyBindingMap<ReaderShortcutAction> = defaultReader,
) {
    /** A chord used in both scopes; the shell handler sees keys first, so the global action wins. */
    data class CrossScopeOverlap(
        val chord: KeyChord,
        val global: ShellShortcutAction,
        val reader: ReaderShortcutAction,
    )

    fun crossScopeOverlaps(): List<CrossScopeOverlap> = ReaderShortcutAction.entries.flatMap { readerAction ->
        reader.chordsFor(readerAction).flatMap { chord ->
            global.actionsBoundTo(chord).map { CrossScopeOverlap(chord, it, readerAction) }
        }
    }

    companion object {
        val defaultGlobal: KeyBindingMap<ShellShortcutAction> = KeyBindingMap(
            ShellShortcutAction.entries,
            ShellShortcutAction.entries.associateWith { it.defaultChords() },
        )
        val defaultReader: KeyBindingMap<ReaderShortcutAction> = KeyBindingMap(
            ReaderShortcutAction.entries,
            ReaderShortcutAction.entries.associateWith { it.defaultChords() },
        )
        val Default = DesktopKeyBindings()
    }
}

private val DESTINATION_KEYS = listOf(
    Key.One to Key.NumPad1,
    Key.Two to Key.NumPad2,
    Key.Three to Key.NumPad3,
    Key.Four to Key.NumPad4,
    Key.Five to Key.NumPad5,
)

fun ShellShortcutAction.defaultChords(): List<KeyChord> = when (this) {
    ShellShortcutAction.NavigateDestination1 -> destinationChords(0)
    ShellShortcutAction.NavigateDestination2 -> destinationChords(1)
    ShellShortcutAction.NavigateDestination3 -> destinationChords(2)
    ShellShortcutAction.NavigateDestination4 -> destinationChords(3)
    ShellShortcutAction.NavigateDestination5 -> destinationChords(4)
    // 0.3.0: Ctrl+1~5 follow the rail order; Settings moved to the Windows-conventional Ctrl+, and
    // Stats/About (now a History segment and a Settings section) have no default chord. Users who
    // customised these actions keep their stored chords: only non-default bindings are persisted.
    ShellShortcutAction.NavigateDestination6 -> emptyList()
    ShellShortcutAction.NavigateDestination7 -> listOf(chord(Key.Comma, ctrl = true))
    ShellShortcutAction.NavigateDestination8 -> emptyList()
    ShellShortcutAction.FocusSearch -> listOf(chord(Key.F, ctrl = true))
    ShellShortcutAction.RefreshCurrentPage -> listOf(chord(Key.F5), chord(Key.R, ctrl = true))
    ShellShortcutAction.NavigateBack -> listOf(
        chord(Key.DirectionLeft, alt = true),
        chord(Key.Backspace),
        chord(Key.Escape),
    )
    ShellShortcutAction.ShowShortcutsHelp -> listOf(chord(Key.Slash, ctrl = true), chord(Key.Slash, shift = true))
    ShellShortcutAction.ToggleNavigationRail -> listOf(chord(Key.B, ctrl = true))
}

private fun destinationChords(index: Int): List<KeyChord> =
    DESTINATION_KEYS[index].let { (row, pad) -> listOf(chord(row, ctrl = true), chord(pad, ctrl = true)) }

fun ReaderShortcutAction.defaultChords(): List<KeyChord> = when (this) {
    ReaderShortcutAction.PAGE_LEFT -> listOf(chord(Key.DirectionLeft), chord(Key.A))
    ReaderShortcutAction.PAGE_RIGHT -> listOf(chord(Key.DirectionRight), chord(Key.D))
    ReaderShortcutAction.PREVIOUS_PAGE -> listOf(
        chord(Key.PageUp),
        chord(Key.DirectionUp),
        chord(Key.Spacebar, shift = true),
    )
    ReaderShortcutAction.NEXT_PAGE -> listOf(chord(Key.PageDown), chord(Key.DirectionDown), chord(Key.Spacebar))
    ReaderShortcutAction.FIRST_PAGE -> listOf(chord(Key.MoveHome))
    ReaderShortcutAction.LAST_PAGE -> listOf(chord(Key.MoveEnd))
    ReaderShortcutAction.ZOOM_IN -> listOf(chord(Key.Plus), chord(Key.Equals), chord(Key.NumPadAdd))
    ReaderShortcutAction.ZOOM_OUT -> listOf(chord(Key.Minus), chord(Key.NumPadSubtract))
    ReaderShortcutAction.RESET_ZOOM -> listOf(chord(Key.Zero))
    ReaderShortcutAction.FULLSCREEN -> listOf(chord(Key.F), chord(Key.F11))
    // 0.3.0: F11/F is the one immersive toggle; borderless stays bindable but has no default key.
    ReaderShortcutAction.BORDERLESS -> emptyList()
    ReaderShortcutAction.EXIT -> listOf(chord(Key.Escape))
    ReaderShortcutAction.PAGE_ACTIONS -> listOf(chord(Key.X))
    ReaderShortcutAction.PREVIOUS_CHAPTER -> listOf(chord(Key.P), chord(Key.LeftBracket))
    ReaderShortcutAction.NEXT_CHAPTER -> listOf(chord(Key.N), chord(Key.RightBracket))
    ReaderShortcutAction.TOGGLE_BOOKMARK -> listOf(chord(Key.M))
    ReaderShortcutAction.CYCLE_READING_MODE -> listOf(chord(Key.V))
    ReaderShortcutAction.TOGGLE_AUTO_SCROLL -> listOf(chord(Key.S))
    ReaderShortcutAction.AUTO_SCROLL_FASTER -> listOf(chord(Key.DirectionDown, shift = true))
    ReaderShortcutAction.AUTO_SCROLL_SLOWER -> listOf(chord(Key.DirectionUp, shift = true))
    ReaderShortcutAction.SHOW_HELP -> listOf(
        chord(Key.F1),
        chord(Key.Slash),
        chord(Key.Slash, shift = true),
        chord(Key.Help),
    )
}

/**
 * Persists the key map in the shared preferences file under `shortcuts.global.*` and
 * `shortcuts.reader.*`. Only actions that differ from the defaults are written, so new default
 * shortcuts in later versions reach users who never customised that action.
 */
class DesktopKeyBindingStore(private val preferences: DesktopPreferenceStore) {

    @Volatile
    private var cache: Pair<Map<String, String>, DesktopKeyBindings>? = null

    /** Cheap enough to call per key press: one file stat, and a re-parse only after a change. */
    fun load(): DesktopKeyBindings {
        val raw = preferences.propertiesWithPrefix(PREFIX)
        cache?.let { (cachedRaw, cachedBindings) -> if (cachedRaw == raw) return cachedBindings }
        val parsed = parse(raw)
        cache = raw to parsed
        return parsed
    }

    fun save(bindings: DesktopKeyBindings) {
        val encoded = encode(bindings)
        preferences.update {
            stringPropertyNames().filter { it.startsWith(PREFIX) }.forEach { remove(it) }
            encoded.forEach { (key, value) -> setProperty(key, value) }
        }
    }

    fun resetToDefaults() = save(DesktopKeyBindings.Default)

    companion object {
        const val PREFIX = "shortcuts."
        private const val GLOBAL = "${PREFIX}global."
        private const val READER = "${PREFIX}reader."

        fun parse(raw: Map<String, String>): DesktopKeyBindings {
            fun <A : Enum<A>> parseScope(prefix: String, defaults: KeyBindingMap<A>, entries: List<A>) =
                KeyBindingMap(
                    entries,
                    entries.associateWith { action ->
                        raw[prefix + action.name]?.let(::parseChordList) ?: defaults.chordsFor(action)
                    },
                )
            return DesktopKeyBindings(
                global = parseScope(GLOBAL, DesktopKeyBindings.defaultGlobal, ShellShortcutAction.entries),
                reader = parseScope(READER, DesktopKeyBindings.defaultReader, ReaderShortcutAction.entries),
            )
        }

        fun encode(bindings: DesktopKeyBindings): Map<String, String> = buildMap {
            ShellShortcutAction.entries.forEach { action ->
                if (!bindings.global.isDefault(action, DesktopKeyBindings.defaultGlobal)) {
                    put(GLOBAL + action.name, bindings.global.chordsFor(action).joinToString(",") { it.serialize() })
                }
            }
            ReaderShortcutAction.entries.forEach { action ->
                if (!bindings.reader.isDefault(action, DesktopKeyBindings.defaultReader)) {
                    put(READER + action.name, bindings.reader.chordsFor(action).joinToString(",") { it.serialize() })
                }
            }
        }

        private fun parseChordList(value: String): List<KeyChord> =
            value.split(',').filter { it.isNotBlank() }.mapNotNull(KeyChord::parse)
    }
}

/** Which action a chord is being bound to in the settings editor. */
sealed interface ShortcutTarget {
    data class Global(val action: ShellShortcutAction) : ShortcutTarget

    data class Reader(val action: ReaderShortcutAction) : ShortcutTarget
}

/**
 * The outcome of binding [chord] to a target. [replaced] moves the chord to the target, removing it
 * from every action that used it. [keepBoth] adds it without touching others and is only offered when
 * the clash is with the other scope (same-scope duplicates would make one action unreachable).
 */
data class ShortcutAssignmentPlan(
    val chord: KeyChord,
    val sameScopeConflicts: List<ShortcutTarget>,
    val otherScopeConflicts: List<ShortcutTarget>,
    val replaced: DesktopKeyBindings,
    val keepBoth: DesktopKeyBindings?,
) {
    val hasConflict: Boolean get() = sameScopeConflicts.isNotEmpty() || otherScopeConflicts.isNotEmpty()
}

fun DesktopKeyBindings.planAssignment(target: ShortcutTarget, chord: KeyChord): ShortcutAssignmentPlan {
    val globalExcept = (target as? ShortcutTarget.Global)?.action
    val readerExcept = (target as? ShortcutTarget.Reader)?.action
    val globalClashes = global.actionsBoundTo(chord, except = globalExcept).map { ShortcutTarget.Global(it) }
    val readerClashes = reader.actionsBoundTo(chord, except = readerExcept).map { ShortcutTarget.Reader(it) }
    val (same, other) = when (target) {
        is ShortcutTarget.Global -> globalClashes to readerClashes
        is ShortcutTarget.Reader -> readerClashes to globalClashes
    }
    val cleared = copy(global = global.withoutChord(chord), reader = reader.withoutChord(chord))
    return ShortcutAssignmentPlan(
        chord = chord,
        sameScopeConflicts = same,
        otherScopeConflicts = other,
        replaced = cleared.withAdded(target, chord),
        keepBoth = if (same.isEmpty()) withAdded(target, chord) else null,
    )
}

fun DesktopKeyBindings.chordsFor(target: ShortcutTarget): List<KeyChord> = when (target) {
    is ShortcutTarget.Global -> global.chordsFor(target.action)
    is ShortcutTarget.Reader -> reader.chordsFor(target.action)
}

fun DesktopKeyBindings.withoutChord(target: ShortcutTarget, chord: KeyChord): DesktopKeyBindings = when (target) {
    is ShortcutTarget.Global -> copy(global = global.withChords(target.action, global.chordsFor(target.action) - chord))
    is ShortcutTarget.Reader -> copy(reader = reader.withChords(target.action, reader.chordsFor(target.action) - chord))
}

fun DesktopKeyBindings.resetToDefault(target: ShortcutTarget): DesktopKeyBindings = when (target) {
    is ShortcutTarget.Global -> copy(global = global.withChords(target.action, target.action.defaultChords()))
    is ShortcutTarget.Reader -> copy(reader = reader.withChords(target.action, target.action.defaultChords()))
}

private fun DesktopKeyBindings.withAdded(target: ShortcutTarget, chord: KeyChord): DesktopKeyBindings = when (target) {
    is ShortcutTarget.Global -> copy(global = global.assign(target.action, chord, replace = false))
    is ShortcutTarget.Reader -> copy(reader = reader.assign(target.action, chord, replace = false))
}

/** Set while the settings editor is recording a chord so the shell does not act on it. */
object ShortcutCaptureState {
    @Volatile
    var active: Boolean = false
}
