package mihon.desktop.reader

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.input.ReaderInputCommand
import mihon.desktop.reader.input.ReaderInputContext
import mihon.desktop.reader.input.ReaderInputMapper
import mihon.desktop.reader.input.ReaderShortcutAction
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyChord
import mihon.desktop.ui.shortcut.defaultChords
import mihon.reader.model.ReadingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AutoScrollTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scroll distance follows speed, frame time and density`() {
        val controller = AutoScrollController(speedDpPerSecond = 100)
        controller.deltaPixels(elapsedNanos = 50_000_000L, density = 2f) shouldBe (10f plusOrMinus 0.0001f)
        controller.deltaPixels(elapsedNanos = 16_000_000L, density = 1f) shouldBe (1.6f plusOrMinus 0.0001f)
    }

    @Test
    fun `a stalled frame never jumps further than the frame cap`() {
        val controller = AutoScrollController(speedDpPerSecond = 100)
        // A 3 s hitch (GC, window drag) must not teleport the page.
        controller.deltaPixels(elapsedNanos = 3_000_000_000L, density = 1f) shouldBe 10f
    }

    @Test
    fun `speed adjusts in steps within bounds`() {
        val controller = AutoScrollController(speedDpPerSecond = 100)
        controller.faster() shouldBe 100 + AutoScrollController.STEP_DP_PER_SECOND
        controller.slower()
        controller.slower() shouldBe 100 - AutoScrollController.STEP_DP_PER_SECOND
        AutoScrollController(speedDpPerSecond = AutoScrollController.MAX_DP_PER_SECOND).faster() shouldBe
            AutoScrollController.MAX_DP_PER_SECOND
        AutoScrollController(speedDpPerSecond = AutoScrollController.MIN_DP_PER_SECOND).slower() shouldBe
            AutoScrollController.MIN_DP_PER_SECOND
        AutoScrollController(speedDpPerSecond = 100_000).speedDpPerSecond shouldBe
            AutoScrollController.MAX_DP_PER_SECOND
    }

    @Test
    fun `auto-scroll shortcuts map to commands and are rebindable with defaults`() {
        val mapper = ReaderInputMapper()
        val context = ReaderInputContext(mode = ReadingMode.WEBTOON)
        mapper.mapAction(ReaderShortcutAction.TOGGLE_AUTO_SCROLL, context).action shouldBe
            ReaderInputCommand.ToggleAutoScroll
        mapper.mapAction(ReaderShortcutAction.AUTO_SCROLL_FASTER, context).action shouldBe
            ReaderInputCommand.AdjustAutoScroll(faster = true)
        mapper.mapAction(ReaderShortcutAction.AUTO_SCROLL_SLOWER, context).action shouldBe
            ReaderInputCommand.AdjustAutoScroll(faster = false)

        ReaderShortcutAction.TOGGLE_AUTO_SCROLL.defaultChords() shouldContain KeyChord(Key.S)
        DesktopKeyBindings.Default.reader.match(KeyChord(Key.S)) shouldBe ReaderShortcutAction.TOGGLE_AUTO_SCROLL
        DesktopKeyBindings.Default.reader.conflicts() shouldBe emptyMap()
    }

    @Test
    fun `auto-scroll speed is persisted with the reader settings`() {
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))
        store.load().autoScrollSpeed shouldBe AutoScrollController.DEFAULT_DP_PER_SECOND
        store.save(store.load().copy(autoScrollSpeed = 240))
        store.load().autoScrollSpeed shouldBe 240
    }
}
