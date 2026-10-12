@file:Suppress("FunctionName")

package mihon.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import mihon.desktop.logging.DesktopLogger
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

internal interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

/** Matches the Windows caption (title bar) to the app theme; a no-op on other systems. */
object WindowsTitleBar {
    // Windows 10 20H1+ and Windows 11 use 20; Windows 10 1809-1909 used the undocumented 19.
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty()
        .startsWith("Windows", ignoreCase = true)

    private val dwm: DwmApi? by lazy {
        if (!isWindows) {
            null
        } else {
            runCatching { Native.load("dwmapi", DwmApi::class.java) }
                .onFailure { DesktopLogger.warn("TitleBar", "dwmapi is unavailable: ${it.message}") }
                .getOrNull()
        }
    }

    /** Applies [dark] now, or as soon as the native window exists. */
    fun apply(window: Window, dark: Boolean) {
        if (!isWindows) return
        if (window.isDisplayable) {
            setDark(window, dark)
            return
        }
        window.addWindowListener(
            object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    window.removeWindowListener(this)
                    setDark(window, dark)
                }
            },
        )
    }

    private fun setDark(window: Window, dark: Boolean) {
        val api = dwm ?: return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            val value = IntByReference(if (dark) 1 else 0)
            if (api.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4) != 0) {
                api.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, value, 4)
            }
        }.onFailure { DesktopLogger.warn("TitleBar", "DwmSetWindowAttribute failed: ${it.message}") }
    }
}
