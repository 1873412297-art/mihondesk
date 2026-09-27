package mihon.desktop.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

class RollingFileLogger(
    val logDirectory: Path,
    private val maxSizeBytes: Long = 5L * 1024L * 1024L,
    private val maxBackups: Int = 5,
    private val logFileName: String = "mihon.log",
) : AutoCloseable {

    private val lock = Any()
    val activeLogFile: Path = logDirectory.resolve(logFileName)

    init {
        Files.createDirectories(logDirectory)
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val threadName = Thread.currentThread().name
        val formatted = buildString {
            append(timestamp)
            append(" [")
            append(level.name)
            append("] [")
            append(threadName)
            append("] [")
            append(tag)
            append("]: ")
            append(message)
            if (throwable != null) {
                append(System.lineSeparator())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString().trimEnd())
            }
            append(System.lineSeparator())
        }

        synchronized(lock) {
            try {
                rotateIfNeeded(formatted.length.toLong())
                Files.writeString(
                    activeLogFile,
                    formatted,
                    UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                )
            } catch (_: Throwable) {
                // Logging must never throw or disrupt application flow
            }
        }
    }

    private fun rotateIfNeeded(pendingBytes: Long) {
        if (!Files.exists(activeLogFile)) return
        val currentSize = try {
            Files.size(activeLogFile)
        } catch (_: Throwable) {
            0L
        }
        if (currentSize + pendingBytes <= maxSizeBytes) return

        // Rotate existing backups: mihon.4.log -> mihon.5.log, ..., mihon.1.log -> mihon.2.log
        for (i in maxBackups downTo 1) {
            val src = if (i == 1) activeLogFile else logDirectory.resolve("mihon.${i - 1}.log")
            val dst = logDirectory.resolve("mihon.$i.log")
            if (Files.exists(src)) {
                if (i == maxBackups && Files.exists(dst)) {
                    runCatching { Files.deleteIfExists(dst) }
                }
                runCatching {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    override fun close() {
        // No open persistent file handles to close since writes are direct and flushed
    }
}

object DesktopLogger {
    @Volatile
    private var logger: RollingFileLogger? = null

    fun init(
        logDirectory: Path,
        maxSizeBytes: Long = 5L * 1024L * 1024L,
        maxBackups: Int = 5,
    ): RollingFileLogger {
        val instance = RollingFileLogger(logDirectory, maxSizeBytes, maxBackups)
        logger = instance
        return instance
    }

    fun close() {
        logger?.close()
        logger = null
    }

    fun isInitialized(): Boolean = logger != null

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val active = logger
        if (active != null) {
            active.log(level, tag, message, throwable)
        } else {
            // Uninitialized fallback: print warnings and errors to stderr
            if (level == LogLevel.WARN || level == LogLevel.ERROR) {
                System.err.println("[$level] [$tag]: $message")
                throwable?.printStackTrace(System.err)
            }
        }
    }

    fun debug(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.DEBUG, tag, message, throwable)

    fun info(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.INFO, tag, message, throwable)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)
}
