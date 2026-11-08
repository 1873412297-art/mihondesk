package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.window.DisplayLayout
import mihon.desktop.window.WindowPlacement
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

internal class AppWindowSetup(
    val controller: MainWindowController,
    val screen: DisplayLayout,
    val tray: DesktopTray,
    val updates: mihon.desktop.updates.AppUpdatePresenter,
    val close: (Boolean) -> Unit,
)

@Composable
internal fun rememberAppWindowSetup(
    app: AppSession,
    runtime: DesktopRuntime,
    host: MainWindowHost?,
    exitApplication: () -> Unit,
): AppWindowSetup {
    val screen = remember { DisplayLayout.current() }
    val placement = remember(app.state.preferences.windowPlacement) {
        screen.sanitize(app.state.preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false))
    }
    val state = host?.windowState ?: appWindowState(placement)
    val controller = remember(state) { MainWindowController(state, screen, placement, runtime.preferences) }
    remember(placement) { controller.resetReaderWindow(placement) }
    lateinit var closeRef: (Boolean) -> Unit
    val tray = remember {
        mihon.desktop.StartupTrace.mark("tray created")
        DesktopTray(
            stringsProvider = { DesktopStrings.resolve(app.state.preferences.language) },
            onShow = { controller.showMainWindow() },
            onQuit = { closeRef(true) },
        ).also { runtime.onShutdown(it::dispose) }
    }
    fun close(force: Boolean) = closeApplication(app, runtime, controller, screen, tray, force, exitApplication)
    closeRef = ::close
    val updates = remember(runtime.appUpdateService) {
        mihon.desktop.updates.AppUpdatePresenter(runtime.appUpdateService, app.scope, runtime.appUpdateInstaller) {
            close(true)
        }.also { runtime.onShutdown(it::shutdown) }
    }
    return AppWindowSetup(controller, screen, tray, updates, ::close)
}

@Composable
private fun appWindowState(placement: WindowPlacement): WindowState = rememberWindowState(
    placement = if (placement.maximized) ComposeWindowPlacement.Maximized else ComposeWindowPlacement.Floating,
    position = WindowPosition(placement.x.dp, placement.y.dp),
    width = placement.width.dp,
    height = placement.height.dp,
)

private fun closeApplication(
    app: AppSession,
    runtime: DesktopRuntime,
    window: MainWindowController,
    screen: DisplayLayout,
    tray: DesktopTray,
    force: Boolean,
    exitApplication: () -> Unit,
) {
    val stay = DesktopTray.shouldStayInBackground(
        app.state.preferences.runInBackgroundOnClose,
        tray.isSupported,
        window.composeWindow != null,
        force,
    )
    savePlacement(app, runtime, window, screen)
    if (stay) {
        tray.ensureInstalled()
        window.composeWindow?.isVisible = false
        val strings = DesktopStrings.resolve(app.state.preferences.language)
        tray.showNotificationOnce(strings.trayHiddenTitle, strings.trayHiddenHint)
    } else {
        tray.dispose()
        exitApplication()
    }
}

private fun savePlacement(
    app: AppSession,
    runtime: DesktopRuntime,
    window: MainWindowController,
    screen: DisplayLayout,
) {
    if (window.composeWindow == null) return
    app.state.preferences = runtime.preferences.updatePreferences {
        it.copy(windowPlacement = screen.sanitize(window.currentWindowPlacement()))
    }
}
