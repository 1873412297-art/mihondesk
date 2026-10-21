package mihon.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.flow.Flow
import mihon.desktop.DesktopRuntime
import mihon.desktop.StartupTrace
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.platform.DesktopTitleBarPolicy
import mihon.desktop.platform.JbrTitleBar
import mihon.desktop.platform.MihonTitleBar
import mihon.desktop.platform.SingleInstanceRequest
import mihon.desktop.platform.WindowsReducedMotion
import mihon.desktop.platform.WindowsTitleBar
import mihon.desktop.platform.toColorRef
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.splash.StartupHandoff
import mihon.desktop.ui.splash.StartupSplash
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Toolkit
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

/**
 * The single main window shared by start-up and the app. The window is created before the runtime
 * exists; once [MihonDesktopApp] composes inside it, the app takes over its title, icon, close and
 * key handling through this host instead of opening a window of its own.
 */
class MainWindowHost internal constructor(
    val windowState: WindowState,
    val frameScope: FrameWindowScope,
    val customTitleBar: WindowDecorations.CustomTitleBar?,
    val customTitleBarEnabled: Boolean,
) {
    var title by mutableStateOf("mihondesk")
    var icon by mutableStateOf<Painter?>(null)
    var onCloseRequest: () -> Unit = {}
    var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    var onKeyEvent: (KeyEvent) -> Boolean = { false }
}

/**
 * Loading -> Ready in one window: splash v3 fills the content while [runtime] is null (or the
 * animation is still playing); then the app UI composes in the same window under a splash layer
 * that resumes on the same frame and crossfades away in place.
 */
@Composable
fun ApplicationScope.MainAppWindow(
    preferences: DesktopPreferences,
    playSplash: Boolean,
    runtime: DesktopRuntime?,
    instanceRequests: Flow<SingleInstanceRequest>,
    onCloseDuringStartup: () -> Unit,
) {
    val placement = remember {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false))
            .sanitize(ScreenBounds(0, 0, screenSize.width, screenSize.height))
    }
    val windowState = rememberWindowState(
        placement = if (placement.maximized) ComposeWindowPlacement.Maximized else ComposeWindowPlacement.Floating,
        position = WindowPosition(placement.x.dp, placement.y.dp),
        width = placement.width.dp,
        height = placement.height.dp,
    )
    val bootIcon = remember {
        runCatching {
            MainWindowHost::class.java.getResourceAsStream("/icon.png")?.use {
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    org.jetbrains.skia.Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap(),
                )
            }
        }.getOrNull()
    }
    val customTitleBarEnabled = remember {
        DesktopTitleBarPolicy.useCustomTitleBar(
            jbrSupported = JbrTitleBar.isSupported,
            userPrefersSystemTitleBar = preferences.useSystemTitleBar,
            highContrast = JbrTitleBar.isHighContrast,
        )
    }
    var host by remember { mutableStateOf<MainWindowHost?>(null) }
    // null while the boot splash animation plays; then whether the user skipped it.
    val bootSplash = remember { mutableStateOf<Boolean?>(null) }
    val activeHost = host
    Window(
        onCloseRequest = { host?.onCloseRequest?.invoke() ?: onCloseDuringStartup() },
        state = windowState,
        title = activeHost?.title ?: "mihondesk",
        icon = activeHost?.icon ?: bootIcon,
        onPreviewKeyEvent = { event -> host?.onPreviewKeyEvent?.invoke(event) ?: false },
        onKeyEvent = { event -> host?.onKeyEvent?.invoke(event) ?: false },
    ) {
        SideEffect { window.minimumSize = java.awt.Dimension(960, 640) }
        val dark = resolveDarkTheme(preferences.themeMode)
        val customTitleBar = remember(window) {
            if (customTitleBarEnabled) JbrTitleBar.install(window, dark) else null
        }
        val windowHost = remember(window) {
            MainWindowHost(windowState, this, customTitleBar, customTitleBarEnabled).also {
                it.onCloseRequest = onCloseDuringStartup
            }
        }
        SideEffect { host = windowHost }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            StartupTrace.mark("first window frame")
        }
        Box(Modifier.fillMaxSize()) {
            // Compose the app as soon as the runtime exists, underneath the boot splash, so its
            // first frame (library grid, covers, tray) is built while the splash animation plays.
            if (runtime != null) {
                val handoff = remember { StartupHandoff(bootSplash) { StartupTrace.mark("app UI first frame") } }
                MihonDesktopApp(
                    runtime,
                    instanceRequests,
                    startupHandoff = handoff,
                    host = windowHost,
                )
            }
            if (bootSplash.value == null) {
                LaunchedEffect(window, dark) {
                    WindowsTitleBar.apply(window, dark)
                    customTitleBar?.let { JbrTitleBar.setDark(window, it, dark) }
                }
                val strings = remember(preferences.language) { DesktopStrings.resolve(preferences.language) }
                MihonDesktopTheme(
                    themeMode = preferences.themeMode,
                    appTheme = preferences.appTheme,
                    isAmoled = preferences.themeDarkAmoled,
                    topBar = {
                        val borderColor = MaterialTheme.colorScheme.outlineVariant
                        LaunchedEffect(window, borderColor, customTitleBar) {
                            WindowsTitleBar.applyBorderColor(
                                window,
                                if (customTitleBar != null) borderColor.toColorRef() else null,
                            )
                        }
                        if (customTitleBar != null) {
                            MihonTitleBar(
                                frame = window,
                                titleBar = customTitleBar,
                                title = "mihondesk",
                                icon = bootIcon,
                                iconDescription = strings.titleBarSystemMenu,
                            )
                        }
                    },
                ) {
                    StartupSplash(
                        dark = dark,
                        reducedMotion = WindowsReducedMotion.prefersReducedMotion,
                        ready = runtime != null,
                        animate = playSplash,
                        fadeOut = false,
                        onFinished = { bootSplash.value = it },
                    )
                }
            }
        }
    }
}
