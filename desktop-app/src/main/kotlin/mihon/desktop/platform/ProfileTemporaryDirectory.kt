package mihon.desktop.platform

import mihon.desktop.logging.DesktopLogger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps temporary files inside the profile for portable launches, so a portable copy run from a USB
 * stick leaves nothing in `%TEMP%` and every child process stays inside the same folder.
 *
 * The JVM cannot change its own environment, so the redirect has two halves: `java.io.tmpdir` for
 * this process (read by `Files.createTempFile` and by the native-library extractors Skiko and JNA use),
 * and [childEnvironment] for every process we start.
 */
object ProfileTemporaryDirectory {
    private const val DIRECTORY = "temp"

    /**
     * Points `java.io.tmpdir` at `<profile>/cache/temp` and returns that directory. Installed launches
     * keep the system temp directory, which Windows cleans for them.
     */
    fun apply(directories: AppDirectories, portable: Boolean): Path? {
        if (!portable) return null
        val temporary = directories.cache.resolve(DIRECTORY).toAbsolutePath().normalize()
        return try {
            Files.createDirectories(temporary)
            System.setProperty("java.io.tmpdir", temporary.toString())
            // Skiko and JNA extract their native libraries before any of our code would notice.
            System.setProperty("skiko.data.path", directories.cache.resolve("skiko").toString())
            System.setProperty("jna.tmpdir", temporary.toString())
            temporary
        } catch (error: IOException) {
            // Startup still works from the system temp directory; the user only loses the guarantee.
            DesktopLogger.warn("ProfileTemporaryDirectory", "Cannot create portable temp directory $temporary", error)
            null
        }
    }

    /** `TEMP`, `TMP` and `-Djava.io.tmpdir` for a child process when a profile temp root is active. */
    fun childEnvironment(): Map<String, String> {
        val temporary = activeDirectory() ?: return emptyMap()
        return mapOf("TEMP" to temporary, "TMP" to temporary)
    }

    /** The JVM argument a Java child needs; environment variables alone do not move its tmpdir. */
    fun childJvmArguments(): List<String> = activeDirectory()?.let { listOf("-Djava.io.tmpdir=$it") }.orEmpty()

    private fun activeDirectory(): String? = System.getProperty("java.io.tmpdir")
        ?.takeIf { it.replace('\\', '/').trimEnd('/').endsWith("/cache/$DIRECTORY") }
}
