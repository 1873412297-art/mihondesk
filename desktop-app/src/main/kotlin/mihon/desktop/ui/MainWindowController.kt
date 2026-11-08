package mihon.desktop.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.window.ReaderWindowController
import mihon.desktop.reader.window.ReaderWindowEscape
import mihon.desktop.window.DisplayLayout
import mihon.desktop.window.WindowPlacement
import java.awt.EventQueue
import java.awt.Frame
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

/** The main AWT window and the reader's fullscreen / borderless window modes. */
@Stable
internal class MainWindowController(
    val windowState: WindowState,
    val screen: DisplayLayout,
    savedPlacement: WindowPlacement,
    private val preferenceStore: DesktopPreferenceStore,
) {
    var composeWindow: ComposeWindow? by mutableStateOf(null)
    var readerWindowMode by mutableStateOf(ReaderWindowMode.NORMAL)
        private set
    var readerWindowController = ReaderWindowController(savedPlacement)
        private set

    fun resetReaderWindow(savedPlacement: WindowPlacement) {
        readerWindowController = ReaderWindowController(savedPlacement)
    }

    fun currentWindowPlacement(): WindowPlacement = composeWindow?.let { window ->
        WindowPlacement(
            x = window.x,
            y = window.y,
            width = window.width,
            height = window.height,
            maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
        )
    } ?: readerWindowController.normalBounds

    fun applyReaderWindowMode(mode: ReaderWindowMode) {
        readerWindowMode = mode
        when (mode) {
            ReaderWindowMode.FULLSCREEN -> windowState.placement = ComposeWindowPlacement.Fullscreen
            ReaderWindowMode.BORDERLESS -> windowState.placement = ComposeWindowPlacement.Floating
            ReaderWindowMode.NORMAL -> {
                val bounds = screen.sanitize(readerWindowController.normalBounds)
                windowState.placement = ComposeWindowPlacement.Floating
                windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
            }
        }
        val settingsStore = DesktopReaderSettingsStore(preferenceStore)
        settingsStore.save(settingsStore.load().copy(lastWindowMode = mode))
    }

    fun transitionReaderWindow(mode: ReaderWindowMode) {
        readerWindowController.transition(mode, currentWindowPlacement())
        applyReaderWindowMode(mode)
    }

    fun toggleReaderFullscreen() =
        applyReaderWindowMode(readerWindowController.toggleFullscreen(currentWindowPlacement()))

    fun toggleReaderBorderless() =
        applyReaderWindowMode(readerWindowController.toggleBorderless(currentWindowPlacement()))

    /** Restores the reader's last window mode when a reader opens in a normal window. */
    fun restoreReaderWindowMode() {
        if (readerWindowController.mode != ReaderWindowMode.NORMAL) return
        val savedReaderMode = DesktopReaderSettingsStore(preferenceStore).load().lastWindowMode
        if (savedReaderMode != ReaderWindowMode.NORMAL) {
            readerWindowController.restore(savedReaderMode, currentWindowPlacement())
            applyReaderWindowMode(savedReaderMode)
        }
    }

    /** Returns true when Escape should close the reader rather than leave a window mode. */
    fun handleReaderEscape(): Boolean = when (readerWindowController.onEscape(currentWindowPlacement())) {
        ReaderWindowEscape.ReturnedToNormal -> {
            applyReaderWindowMode(ReaderWindowMode.NORMAL)
            false
        }
        ReaderWindowEscape.CloseReader -> true
    }

    fun showMainWindow(fromAnotherProcess: Boolean = false) {
        val window = composeWindow ?: return
        EventQueue.invokeLater {
            if (!window.isVisible) {
                window.isVisible = true
            }
            if ((window.extendedState and Frame.ICONIFIED) != 0) {
                window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            }
            if (fromAnotherProcess && !window.isAlwaysOnTop) {
                // Windows may only flash the taskbar for toFront() from a background process.
                window.isAlwaysOnTop = true
                window.isAlwaysOnTop = false
            }
            window.toFront()
            window.requestFocus()
        }
    }
}
