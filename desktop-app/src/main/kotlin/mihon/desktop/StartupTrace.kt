package mihon.desktop

import mihon.desktop.logging.DesktopLogger
import java.time.Duration
import java.time.Instant

/**
 * Startup phase timestamps, logged as `[StartupTrace]` lines relative to process start (the JVM
 * launch), so before/after comparisons can be read straight from mihon.log.
 */
object StartupTrace {
    private val processStart: Instant? = ProcessHandle.current().info().startInstant().orElse(null)

    /** Milliseconds since the process started, or -1 when the OS does not report it. */
    fun uptimeMillis(): Long = processStart?.let { Duration.between(it, Instant.now()).toMillis() } ?: -1L

    fun mark(phase: String) {
        DesktopLogger.info("StartupTrace", "$phase at ${uptimeMillis()} ms")
    }

    inline fun <T> phase(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            DesktopLogger.info(
                "StartupTrace",
                "$name took ${(System.nanoTime() - start) / 1_000_000} ms (done at ${uptimeMillis()} ms)",
            )
        }
    }
}
