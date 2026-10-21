package mihon.desktop.ui.splash

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.platform.DesktopTitleBarPolicy
import mihon.desktop.platform.JbrTitleBar
import mihon.desktop.platform.MihonTitleBar
import mihon.desktop.platform.WindowsReducedMotion
import mihon.desktop.platform.WindowsTitleBar
import mihon.desktop.platform.toColorRef
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.ui.resolveDarkTheme
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Toolkit
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

/** Milliseconds since this process started, or -1 when the OS does not report it. */
fun processUptimeMillis(): Long = ProcessHandle.current().info().startInstant()
    .map { Duration.between(it, Instant.now()).toMillis() }
    .orElse(-1L)

/**
 * The first window of an interactive launch: the main window frame (saved size/position/maximised
 * state, custom caption with window buttons) with splash v3 filling its content, shown before the
 * slow runtime initialisation. [onHandOff] runs once the animation finished (or was skipped) and
 * [ready] is true; the main window then opens over it on the same frame and calls back to close it.
 */
@Composable
fun ApplicationScope.StartupWindow(
    preferences: DesktopPreferences,
    playSplash: Boolean,
    ready: Boolean,
    onHandOff: (skipped: Boolean) -> Unit,
    onCloseRequest: () -> Unit,
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
    val appIcon = remember {
        runCatching {
            StartupSplashIcon::class.java.getResourceAsStream("/icon.png")?.use {
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    org.jetbrains.skia.Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap(),
                )
            }
        }.getOrNull()
    }
    val strings = remember(preferences.language) { DesktopStrings.resolve(preferences.language) }
    val customTitleBarEnabled = remember {
        DesktopTitleBarPolicy.useCustomTitleBar(
            jbrSupported = JbrTitleBar.isSupported,
            userPrefersSystemTitleBar = preferences.useSystemTitleBar,
            highContrast = JbrTitleBar.isHighContrast,
        )
    }
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "mihondesk",
        icon = appIcon,
    ) {
        SideEffect { window.minimumSize = java.awt.Dimension(960, 640) }
        val dark = resolveDarkTheme(preferences.themeMode)
        val customTitleBar = remember(window) {
            if (customTitleBarEnabled) JbrTitleBar.install(window, dark) else null
        }
        LaunchedEffect(window, dark) {
            WindowsTitleBar.apply(window, dark)
            customTitleBar?.let { JbrTitleBar.setDark(window, it, dark) }
        }
        LaunchedEffect(Unit) {
            withFrameNanos {}
            DesktopLogger.info("Startup", "First window frame at ${processUptimeMillis()} ms since process start")
        }
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
                        icon = appIcon,
                        iconDescription = strings.titleBarSystemMenu,
                    )
                }
            },
        ) {
            StartupSplash(
                dark = dark,
                reducedMotion = WindowsReducedMotion.prefersReducedMotion,
                ready = ready,
                animate = playSplash,
                fadeOut = false,
                onFinished = onHandOff,
            )
        }
    }
}

private object StartupSplashIcon
