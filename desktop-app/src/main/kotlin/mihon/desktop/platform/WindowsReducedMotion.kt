@file:Suppress("FunctionName")

package mihon.desktop.platform

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import mihon.desktop.logging.DesktopLogger

internal interface User32Spi : StdCallLibrary {
    fun SystemParametersInfoW(action: Int, param: Int, value: IntByReference, winIni: Int): Boolean
}

/** Reads Windows "Show animations in Windows" (SPI_GETCLIENTAREAANIMATION); false elsewhere or on error. */
object WindowsReducedMotion {
    private const val SPI_GETCLIENTAREAANIMATION = 0x1042

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty()
        .startsWith("Windows", ignoreCase = true)

    val prefersReducedMotion: Boolean by lazy {
        if (!isWindows) return@lazy false
        runCatching {
            val user32 = Native.load("user32", User32Spi::class.java)
            val enabled = IntByReference(1)
            user32.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, enabled, 0) && enabled.value == 0
        }.onFailure { DesktopLogger.warn("ReducedMotion", "SPI query failed: ${it.message}") }
            .getOrDefault(false)
    }
}
