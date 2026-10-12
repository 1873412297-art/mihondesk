package mihon.desktop.ui.shortcut

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.input.ReaderShortcutAction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopKeyBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `default reader map reproduces the built-in reader keys`() {
        val reader = DesktopKeyBindings.defaultReader
        reader.match(KeyChord(Key.DirectionLeft)) shouldBe ReaderShortcutAction.PAGE_LEFT
        reader.match(KeyChord(Key.D)) shouldBe ReaderShortcutAction.PAGE_RIGHT
        reader.match(KeyChord(Key.Spacebar)) shouldBe ReaderShortcutAction.NEXT_PAGE
        reader.match(KeyChord(Key.Spacebar, shift = true)) shouldBe ReaderShortcutAction.PREVIOUS_PAGE
        reader.match(KeyChord(Key.RightBracket)) shouldBe ReaderShortcutAction.NEXT_CHAPTER
        reader.match(KeyChord(Key.F11)) shouldBe ReaderShortcutAction.FULLSCREEN
        reader.match(KeyChord(Key.F1)) shouldBe ReaderShortcutAction.SHOW_HELP
        reader.match(KeyChord(Key.Slash, shift = true)) shouldBe ReaderShortcutAction.SHOW_HELP
    }

    @Test
    fun `shift falls back to the unshifted chord but ctrl does not`() {
        val reader = DesktopKeyBindings.defaultReader
        reader.match(KeyChord(Key.DirectionRight, shift = true)) shouldBe ReaderShortcutAction.PAGE_RIGHT
        reader.match(KeyChord(Key.DirectionRight, ctrl = true)).shouldBeNull()
        reader.match(KeyChord(Key.F, alt = true)).shouldBeNull()
    }

    @Test
    fun `defaults have no conflicts in either scope`() {
        DesktopKeyBindings.defaultGlobal.conflicts().isEmpty() shouldBe true
        DesktopKeyBindings.defaultReader.conflicts().isEmpty() shouldBe true
    }

    @Test
    fun `chords round trip through their stored text`() {
        listOf(
            KeyChord(Key.Slash, ctrl = true, shift = true),
            KeyChord(Key.DirectionLeft, alt = true),
            KeyChord(Key.NumPad3, ctrl = true),
            KeyChord(Key.F5),
            KeyChord(Key.Comma),
            KeyChord(Key(987654L)),
        ).forEach { chord -> KeyChord.parse(chord.serialize()) shouldBe chord }
        KeyChord.parse("Hyper+A").shouldBeNull()
        KeyChord.parse("Ctrl+").shouldBeNull()
        KeyChord(Key.Slash, ctrl = true).displayLabel() shouldBe "Ctrl + /"
    }

    @Test
    fun `store persists only customised actions and resets to defaults`() {
        val file = tempDir.resolve("preferences.properties")
        val preferences = DesktopPreferenceStore(file)
        val store = DesktopKeyBindingStore(preferences)
        store.load() shouldBe DesktopKeyBindings.Default

        val customised = DesktopKeyBindings.Default.let { bindings ->
            bindings.copy(
                reader = bindings.reader.withChords(ReaderShortcutAction.NEXT_PAGE, listOf(KeyChord(Key.J))),
                global = bindings.global.withChords(ShellShortcutAction.FocusSearch, emptyList()),
            )
        }
        store.save(customised)

        val text = Files.readString(file)
        text.contains("shortcuts.reader.NEXT_PAGE=J") shouldBe true
        text.contains("shortcuts.global.FocusSearch=") shouldBe true
        text.contains("shortcuts.reader.PAGE_LEFT") shouldBe false

        val reloaded = DesktopKeyBindingStore(DesktopPreferenceStore(file)).load()
        reloaded shouldBe customised
        reloaded.reader.match(KeyChord(Key.J)) shouldBe ReaderShortcutAction.NEXT_PAGE
        reloaded.reader.match(KeyChord(Key.PageDown)).shouldBeNull()
        reloaded.global.chordsFor(ShellShortcutAction.FocusSearch).shouldBeEmpty()

        store.resetToDefaults()
        store.load() shouldBe DesktopKeyBindings.Default
        Files.readString(file).contains("shortcuts.") shouldBe false
    }

    @Test
    fun `saving other preferences keeps the key map`() {
        val file = tempDir.resolve("preferences.properties")
        val preferences = DesktopPreferenceStore(file)
        val store = DesktopKeyBindingStore(preferences)
        val customised = DesktopKeyBindings.Default.let {
            it.copy(reader = it.reader.withChords(ReaderShortcutAction.EXIT, listOf(KeyChord(Key.Q))))
        }
        store.save(customised)
        preferences.updatePreferences { it.copy(incognitoMode = true) }
        DesktopKeyBindingStore(DesktopPreferenceStore(file)).load() shouldBe customised
    }

    @Test
    fun `same-scope conflict must replace while cross-scope may keep both`() {
        val bindings = DesktopKeyBindings.Default

        val same = bindings.planAssignment(ShortcutTarget.Reader(ReaderShortcutAction.TOGGLE_BOOKMARK), KeyChord(Key.N))
        same.hasConflict shouldBe true
        same.sameScopeConflicts shouldContainExactly listOf(ShortcutTarget.Reader(ReaderShortcutAction.NEXT_CHAPTER))
        same.keepBoth.shouldBeNull()
        same.replaced.reader.match(KeyChord(Key.N)) shouldBe ReaderShortcutAction.TOGGLE_BOOKMARK
        same.replaced.reader.chordsFor(ReaderShortcutAction.NEXT_CHAPTER) shouldContainExactly
            listOf(KeyChord(Key.RightBracket))

        val cross = bindings.planAssignment(
            ShortcutTarget.Reader(ReaderShortcutAction.PAGE_ACTIONS),
            KeyChord(Key.Backspace),
        )
        cross.sameScopeConflicts.shouldBeEmpty()
        cross.otherScopeConflicts shouldContainExactly listOf(ShortcutTarget.Global(ShellShortcutAction.NavigateBack))
        val keepBoth = cross.keepBoth.shouldNotBeNull()
        keepBoth.global.match(KeyChord(Key.Backspace)) shouldBe ShellShortcutAction.NavigateBack
        keepBoth.reader.match(KeyChord(Key.Backspace)) shouldBe ReaderShortcutAction.PAGE_ACTIONS
        cross.replaced.global.match(KeyChord(Key.Backspace)).shouldBeNull()

        val free = bindings.planAssignment(
            ShortcutTarget.Global(ShellShortcutAction.FocusSearch),
            KeyChord(Key.K, ctrl = true),
        )
        free.hasConflict shouldBe false
        free.replaced.global.match(KeyChord(Key.K, ctrl = true)) shouldBe ShellShortcutAction.FocusSearch
        free.replaced.global.match(KeyChord(Key.F, ctrl = true)) shouldBe ShellShortcutAction.FocusSearch
    }

    @Test
    fun `reset restores a single action`() {
        val changed = DesktopKeyBindings.Default.withoutChord(
            ShortcutTarget.Global(ShellShortcutAction.RefreshCurrentPage),
            KeyChord(Key.F5),
        )
        changed.global.chordsFor(ShellShortcutAction.RefreshCurrentPage) shouldContainExactly
            listOf(KeyChord(Key.R, ctrl = true))
        changed.resetToDefault(ShortcutTarget.Global(ShellShortcutAction.RefreshCurrentPage)) shouldBe
            DesktopKeyBindings.Default
    }

    @Test
    fun `shell matcher follows a customised map`() {
        val custom = DesktopKeyBindings.defaultGlobal
            .withChords(ShellShortcutAction.FocusSearch, listOf(KeyChord(Key.K, ctrl = true)))
            .withChords(ShellShortcutAction.ShowShortcutsHelp, listOf(KeyChord(Key.F1)))
        DesktopShortcutMatcher.matchAction(Key.K, isCtrlPressed = true, bindings = custom) shouldBe
            ShellShortcutAction.FocusSearch
        DesktopShortcutMatcher.matchAction(Key.F, isCtrlPressed = true, bindings = custom).shouldBeNull()
        DesktopShortcutMatcher.matchAction(Key.K, isMetaPressed = true, bindings = custom) shouldBe
            ShellShortcutAction.FocusSearch
        // Function keys still work while typing; plain keys do not.
        DesktopShortcutMatcher.matchAction(Key.F1, isTextInputFocused = true, bindings = custom) shouldBe
            ShellShortcutAction.ShowShortcutsHelp
        DesktopShortcutMatcher.matchAction(Key.Backspace, isTextInputFocused = true, bindings = custom)
            .shouldBeNull()
    }
}
