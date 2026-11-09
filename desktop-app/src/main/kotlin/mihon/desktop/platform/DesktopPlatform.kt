package mihon.desktop.platform

/** Host-platform facts shared by UI code that used to hardcode Windows conventions. */
internal object DesktopPlatform {
    private val osName = System.getProperty("os.name").orEmpty()
    private val osArch = System.getProperty("os.arch").orEmpty()

    val isWindows: Boolean = osName.startsWith("Windows", ignoreCase = true)
    val isMac: Boolean = osName.startsWith("Mac", ignoreCase = true)

    /** Modifier shown for shortcuts stored as Ctrl in preferences. */
    val primaryModifierLabel: String = if (isMac) "⌘" else "Ctrl"

    val displayName: String = buildString {
        append(
            when {
                isWindows -> "Windows"
                isMac -> "macOS"
                else -> osName.ifBlank { "Desktop" }
            },
        )
        append(' ')
        append(
            when (osArch.lowercase()) {
                "aarch64", "arm64" -> "arm64"
                "x86_64", "amd64" -> "x64"
                else -> osArch
            },
        )
    }
}
