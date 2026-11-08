package mihon.desktop.extension

import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** True when the AppContainer system package can already read probe files in %TEMP%. */
internal fun isTempAccessibleByAllPackages(): Boolean {
    return try {
        val probe = Files.createTempDirectory("appcontainer-probe").toFile()
        val log = Files.createTempFile("appcontainer-probe-", ".log")
        try {
            // Redirect to a file first: reading the pipe blocks until the child exits, which would
            // bypass the timeout if icacls hangs.
            val process = ProcessBuilder("icacls", probe.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
            try {
                if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return false
                }
                Files.readString(log).contains("ALL APPLICATION PACKAGES", ignoreCase = true)
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            log.toFile().delete()
            probe.delete()
        }
    } catch (_: Throwable) {
        false
    }
}

private const val PROBE_TIMEOUT_SECONDS = 5L
