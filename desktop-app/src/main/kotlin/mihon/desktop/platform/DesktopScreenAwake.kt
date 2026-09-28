@file:Suppress("FunctionName")

package mihon.desktop.platform

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import mihon.desktop.logging.DesktopLogger

internal interface AwakeKernel32 : StdCallLibrary {
    fun SetThreadExecutionState(flags: Int): Int
}

/** Windows `SetThreadExecutionState` binding; a no-op on every other OS. */
object DesktopScreenAwake {
    // ES_CONTINUOUS (0x80000000) must stay set so the state applies continuously rather than
    // resetting once after a single interval.
    const val ES_CONTINUOUS: Int = Int.MIN_VALUE
    const val ES_DISPLAY_REQUIRED: Int = 0x00000002

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty()
        .startsWith("Windows", ignoreCase = true)

    private val kernel: AwakeKernel32? by lazy {
        if (!isWindows) {
            null
        } else {
            runCatching { Native.load("kernel32", AwakeKernel32::class.java) }
                .onFailure {
                    DesktopLogger.error("ScreenAwake", "Failed to load kernel32 for SetThreadExecutionState", it)
                }
                .getOrNull()
        }
    }

    /** Applies an execution-state flag combination. No-op (returns true) off Windows. */
    fun setExecutionState(flags: Int): Boolean {
        val k = kernel ?: return true
        return runCatching { k.SetThreadExecutionState(flags) != 0 }
            .onFailure { DesktopLogger.error("ScreenAwake", "SetThreadExecutionState($flags) failed", it) }
            .getOrDefault(false)
    }
}

/**
 * Reference-counted guard that keeps the display awake while at least one holder is active.
 * Transitions are pushed through [setExecutionState] so the state machine is testable without
 * touching the native API.
 */
class ScreenAwakeController(
    private val setExecutionState: (Int) -> Boolean = DesktopScreenAwake::setExecutionState,
) {
    private var displayHolders = 0

    val isDisplayKeptOn: Boolean
        get() = displayHolders > 0

    @Synchronized
    fun acquireDisplay() {
        if (displayHolders++ == 0) {
            setExecutionState(DesktopScreenAwake.ES_CONTINUOUS or DesktopScreenAwake.ES_DISPLAY_REQUIRED)
        }
    }

    @Synchronized
    fun releaseDisplay() {
        if (displayHolders <= 0) return
        if (--displayHolders == 0) {
            setExecutionState(DesktopScreenAwake.ES_CONTINUOUS)
        }
    }
}
