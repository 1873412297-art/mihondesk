@file:Suppress("ktlint:standard:property-naming", "ktlint:standard:function-naming")

package mihon.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import mihon.desktop.logging.DesktopLogger

/** Layout of the Win32 SYSTEM_POWER_STATUS structure; only [ACLineStatus] is consumed. */
internal class SystemPowerStatus : Structure() {
    @JvmField var ACLineStatus: Byte = 0

    @JvmField var BatteryFlag: Byte = 0

    @JvmField var BatteryLifePercent: Byte = 0

    @JvmField var SystemStatusFlag: Byte = 0

    @JvmField var BatteryLifeTime: Int = 0

    @JvmField var BatteryFullLifeTime: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "ACLineStatus",
        "BatteryFlag",
        "BatteryLifePercent",
        "SystemStatusFlag",
        "BatteryLifeTime",
        "BatteryFullLifeTime",
    )
}

internal interface PowerKernel32 : StdCallLibrary {
    fun GetSystemPowerStatus(status: SystemPowerStatus): Boolean
}

/** Desktop power-line state; desktops without a battery report AC. */
object DesktopPowerStatus {
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty()
        .startsWith("Windows", ignoreCase = true)

    private val kernel: PowerKernel32? by lazy {
        if (!isWindows) {
            null
        } else {
            runCatching { Native.load("kernel32", PowerKernel32::class.java) }
                .onFailure {
                    DesktopLogger.error("PowerStatus", "Failed to load kernel32 for GetSystemPowerStatus", it)
                }
                .getOrNull()
        }
    }

    /**
     * True when the machine is running on AC power. Non-Windows platforms and any failure to
     * query the OS report `true` so the "only on AC power" library-update gate never blocks
     * desktops, which effectively always count as being on AC.
     */
    fun isOnAcPower(): Boolean {
        val k = kernel ?: return true
        return runCatching {
            val status = SystemPowerStatus()
            if (!k.GetSystemPowerStatus(status)) return true
            // ACLineStatus: 0 = offline (battery), 1 = online (AC), 255 = unknown status.
            status.ACLineStatus.toInt() != 0
        }.onFailure { DesktopLogger.warn("PowerStatus", "GetSystemPowerStatus failed; assuming AC", it) }
            .getOrDefault(true)
    }
}
