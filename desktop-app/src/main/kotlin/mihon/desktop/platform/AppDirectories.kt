package mihon.desktop.platform

import java.nio.file.Files
import java.nio.file.Path

enum class DistributionMode {
    Installed,
    Portable,
}

data class AppDirectories(
    val root: Path,
    val cache: Path = root.resolve("cache"),
    val logs: Path = root.resolve("logs"),
    val extensions: Path = root.resolve("extensions"),
    val database: Path = root.resolve("database"),
    val covers: Path = root.resolve("covers"),
) {
    fun create(): AppDirectories = apply {
        listOf(root, cache, logs, extensions, database, covers).forEach(Files::createDirectories)
    }
}

class AppDirectoryResolver(
    private val appDataDirectory: Path?,
    private val executableDirectory: Path,
) {
    fun resolve(
        mode: DistributionMode,
        explicitRoot: Path? = null,
    ): AppDirectories {
        val root = explicitRoot ?: when (mode) {
            DistributionMode.Installed -> resolveInstalledRoot()
            DistributionMode.Portable -> executableDirectory.resolve("data")
        }
        return AppDirectories(root.toAbsolutePath().normalize())
    }

    private fun resolveInstalledRoot(): Path =
        requireNotNull(appDataDirectory ?: macOSApplicationSupport()) {
            "APPDATA is unavailable; pass --data-dir=<path> to select a writable data directory"
        }.resolve("MihonW")

    /** Windows uses APPDATA; macOS keeps the same profile name under Application Support. */
    private fun macOSApplicationSupport(): Path? =
        if (System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)) {
            Path.of(System.getProperty("user.home"), "Library", "Application Support")
        } else {
            null
        }

    /** Resolves from a detected [PortableLayout], so `portable-data/` is honoured as the root. */
    fun resolve(layout: PortableLayout, explicitRoot: Path? = null): AppDirectories =
        resolve(layout.mode, explicitRoot ?: layout.root)
}
