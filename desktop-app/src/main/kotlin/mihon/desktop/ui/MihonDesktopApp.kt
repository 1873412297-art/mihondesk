package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.platform.SingleInstanceRequest
import mihon.desktop.ui.splash.StartupHandoff

/** Assembles the main window; feature behaviour lives in the session and route components. */
@Composable
fun ApplicationScope.MihonDesktopApp(
    runtime: DesktopRuntime,
    instanceRequests: Flow<SingleInstanceRequest> = emptyFlow(),
    startupHandoff: StartupHandoff? = null,
    host: MainWindowHost? = null,
) {
    val app = rememberAppSession(runtime, startupHandoff)
    val window = rememberAppWindowSetup(app, runtime, host, ::exitApplication)
    AppSessionEffects(app, runtime, window.controller)
    ForwardedInstanceRequests(app, runtime, instanceRequests, window.controller)
    ShellActivityEffects(app, runtime, window.controller)
    val live = rememberShellLiveState(app, runtime)
    val ctx = appShellContext(app, runtime)
    val title = mainWindowTitle(
        app.navigator.current,
        live.mangaDetailState,
        DesktopStrings.resolve(app.state.preferences.language),
    )
    val icon = remember { loadAppIcon() }
    val onKey: (androidx.compose.ui.input.key.KeyEvent, Boolean) -> Boolean = { event, after ->
        handleShellKey(ctx, event, after, app.textInputFocused.get(), app.searchFocus)
    }
    val content: @Composable androidx.compose.ui.window.FrameWindowScope.() -> Unit = {
        AppWindowContent(app, ctx, live, runtime, window.controller, window.updates, title, icon, host)
    }
    if (host != null) {
        SideEffect {
            host.title = title
            host.icon = icon
            host.onCloseRequest = { window.close(false) }
            host.onPreviewKeyEvent = { onKey(it, false) }
            host.onKeyEvent = { onKey(it, true) }
        }
        host.frameScope.content()
    } else {
        Window(
            onCloseRequest = { window.close(false) },
            state = window.controller.windowState,
            title = title,
            icon = icon,
            onPreviewKeyEvent = { onKey(it, false) },
            onKeyEvent = { onKey(it, true) },
            content = content,
        )
    }
}
