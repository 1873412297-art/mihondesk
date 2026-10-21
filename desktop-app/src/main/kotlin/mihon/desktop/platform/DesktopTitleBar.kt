@file:Suppress("FunctionName")

package mihon.desktop.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import mihon.desktop.logging.DesktopLogger
import java.awt.Frame
import java.awt.Toolkit

/** Height of the custom caption, in dp (== AWT logical pixels on desktop). */
const val DESKTOP_TITLE_BAR_HEIGHT_DP = 32

/** Native caption buttons are 46 logical px wide on Windows 10/11. */
private const val FALLBACK_CONTROLS_WIDTH_DP = 138

/**
 * Decides whether the main window uses the JetBrains Runtime custom title bar. Pure so it can be
 * unit tested; the caller supplies the environment facts.
 */
object DesktopTitleBarPolicy {
    fun useCustomTitleBar(
        jbrSupported: Boolean,
        userPrefersSystemTitleBar: Boolean,
        highContrast: Boolean,
    ): Boolean = jbrSupported && !userPrefersSystemTitleBar && !highContrast

    /** The Compose caption strip is hidden in borderless/fullscreen reading; native buttons stay. */
    fun showCaptionStrip(customTitleBar: Boolean, borderlessOrFullscreen: Boolean): Boolean =
        customTitleBar && !borderlessOrFullscreen
}

private interface User32Ext : StdCallLibrary {
    fun PostMessageW(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Boolean
}

/** JetBrains Runtime `CustomTitleBar` integration; every call degrades to a no-op off JBR. */
object JbrTitleBar {
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_KEYMENU = 0xF100L
    private const val VK_SPACE = 0x20L

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    val isSupported: Boolean by lazy {
        isWindows && runCatching { JBR.isAvailable() && JBR.isWindowDecorationsSupported() }
            .onFailure { DesktopLogger.warn("TitleBar", "JBR decorations unavailable: ${it.message}") }
            .getOrDefault(false)
    }

    val isHighContrast: Boolean
        get() = runCatching {
            Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") as? Boolean
        }.getOrNull() ?: false

    private val user32: User32Ext? by lazy {
        if (!isWindows) null else runCatching { Native.load("user32", User32Ext::class.java) }.getOrNull()
    }

    /** Installs a 32dp custom title bar on [frame]; returns null when unsupported. */
    fun install(frame: Frame, dark: Boolean): WindowDecorations.CustomTitleBar? {
        if (!isSupported) return null
        return runCatching {
            val decorations = JBR.getWindowDecorations()
            val titleBar = decorations.createCustomTitleBar()
            titleBar.height = DESKTOP_TITLE_BAR_HEIGHT_DP.toFloat()
            titleBar.putProperty("controls.dark", dark)
            decorations.setCustomTitleBar(frame, titleBar)
            titleBar
        }.onFailure { DesktopLogger.warn("TitleBar", "CustomTitleBar install failed: ${it.message}") }
            .getOrNull()
    }

    fun setDark(frame: Frame, titleBar: WindowDecorations.CustomTitleBar, dark: Boolean) {
        runCatching {
            titleBar.putProperty("controls.dark", dark)
            // Re-applying makes JBR repaint the native caption buttons with the new palette.
            JBR.getWindowDecorations().setCustomTitleBar(frame, titleBar)
        }
    }

    /** Opens the native system menu (Restore/Move/Size/Minimize/Maximize/Close) like Alt+Space. */
    fun showSystemMenu(frame: Frame) {
        val api = user32 ?: return
        runCatching {
            val hwnd = Native.getWindowPointer(frame) ?: return
            api.PostMessageW(hwnd, WM_SYSCOMMAND, SC_KEYMENU, VK_SPACE)
        }.onFailure { DesktopLogger.warn("TitleBar", "System menu failed: ${it.message}") }
    }
}

/**
 * Lets clicks on interactive caption content (the app icon) reach Compose, while every other
 * point stays native caption so Snap, Snap Layouts, drag and double-click-maximize keep working.
 */
private fun Modifier.captionHitTest(
    titleBar: WindowDecorations.CustomTitleBar,
    interactiveBounds: () -> androidx.compose.ui.geometry.Rect = { androidx.compose.ui.geometry.Rect.Zero },
): Modifier =
    pointerInput(titleBar) {
        val context = currentCoroutineContext()
        awaitPointerEventScope {
            var inUserControl = false
            while (context.isActive) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { change ->
                    // clickable() consumes only on release, so hover/press over title-bar actions must be
                    // claimed by position or the native caption would swallow the click as a drag.
                    val overAction = interactiveBounds().contains(change.position)
                    if (!change.isConsumed && !inUserControl && !overAction) {
                        titleBar.forceHitTest(false)
                    } else {
                        if (event.type == PointerEventType.Press) inUserControl = true
                        if (event.type == PointerEventType.Release) inUserControl = false
                        titleBar.forceHitTest(true)
                    }
                }
            }
        }
    }

@Composable
fun MihonTitleBar(
    frame: Frame,
    titleBar: WindowDecorations.CustomTitleBar,
    title: String,
    icon: Painter?,
    iconDescription: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var actionsBounds by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    }
    val controlsWidth = titleBar.rightInset.takeIf { it > 0f }?.dp ?: FALLBACK_CONTROLS_WIDTH_DP.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DESKTOP_TITLE_BAR_HEIGHT_DP.dp)
            .background(colors.surface)
            .captionHitTest(titleBar) { actionsBounds }
            .testTag("mihon-title-bar"),
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClickLabel = iconDescription) {
                        JbrTitleBar.showSystemMenu(frame)
                    }
                    .testTag("mihon-title-bar-icon"),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(icon, contentDescription = iconDescription, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onGloballyPositioned { actionsBounds = it.boundsInParent() },
            ) { actions() }
            Spacer(Modifier.width(controlsWidth))
        }
        // Bottom separator shares the outlineVariant token with the Windows 11 1px window border.
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(colors.outlineVariant),
        )
    }
}

/** Windows COLORREF (0x00BBGGRR) for a Compose colour. */
fun Color.toColorRef(): Int {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (b shl 16) or (g shl 8) or r
}
