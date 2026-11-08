package mihon.desktop.extension

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.IntByReference

/** Whether this process, or an ancestor, already runs inside an MSIX/AppX package. */
internal object WindowsPackageIdentity {
    fun isInsidePackage(): Boolean {
        try {
            val length = IntByReference(0)
            // APPMODEL_ERROR_NO_PACKAGE = 15700
            val rc = WindowsSandboxNative.kernel.GetCurrentPackageFamilyName(length, null)
            if (rc != 15700) return true
        } catch (error: Throwable) {
            // Missing on old Windows builds (a JNA linkage error); fall through to the other checks.
            mihon.desktop.logging.DesktopLogger.warn("Sandbox", "Unable to query package identity", error)
        }
        try {
            var handle: ProcessHandle? = ProcessHandle.current()
            while (handle != null) {
                val command = handle.info().command().orElse("")
                if (command.contains("WindowsApps", ignoreCase = true)) {
                    return true
                }
                val hProcess = Kernel32.INSTANCE.OpenProcess(0x1000, false, handle.pid().toInt())
                if (hProcess != null && hProcess != WinBase.INVALID_HANDLE_VALUE) {
                    try {
                        val length = IntByReference(0)
                        val rc = WindowsSandboxNative.kernel.GetPackageFamilyName(hProcess, length, null)
                        if (rc != 15700) return true
                    } finally {
                        Kernel32.INSTANCE.CloseHandle(hProcess)
                    }
                }
                handle = handle.parent().orElse(null)
            }
        } catch (error: Throwable) {
            // Ancestor inspection is a heuristic; the PATH check below still runs.
            mihon.desktop.logging.DesktopLogger.warn("Sandbox", "Unable to inspect parent processes", error)
        }
        val path = System.getenv("PATH").orEmpty()
        if (path.contains("WindowsApps", ignoreCase = true)) {
            return true
        }
        return false
    }
}
