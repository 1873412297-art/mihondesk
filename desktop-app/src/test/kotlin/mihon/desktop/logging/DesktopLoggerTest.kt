package mihon.desktop.logging

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopLoggerTest {

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun tearDown() {
        DesktopLogger.close()
    }

    @Test
    fun `logs formatted message to active log file`() {
        val logger = RollingFileLogger(tempDir)
        logger.log(LogLevel.INFO, "TestTag", "Hello world from test")

        val logFile = tempDir.resolve("mihon.log")
        Files.exists(logFile) shouldBe true
        val content = Files.readString(logFile)
        content shouldContain "[INFO]"
        content shouldContain "[TestTag]"
        content shouldContain "Hello world from test"
    }

    @Test
    fun `logs throwable with stack trace`() {
        val logger = RollingFileLogger(tempDir)
        val exception = RuntimeException("Boom!")
        logger.log(LogLevel.ERROR, "CrashTag", "Something crashed", exception)

        val logFile = tempDir.resolve("mihon.log")
        val content = Files.readString(logFile)
        content shouldContain "[ERROR]"
        content shouldContain "Something crashed"
        content shouldContain "Boom!"
        content shouldContain "DesktopLoggerTest"
    }

    @Test
    fun `rotates files when size limit exceeded and retains max backups`() {
        // Limit each file to 200 bytes, max 3 backups
        val logger = RollingFileLogger(tempDir, maxSizeBytes = 200L, maxBackups = 3)

        // Write enough data to trigger multiple rotations
        for (i in 1..20) {
            logger.log(LogLevel.INFO, "Tag", "Message line number $i with extra padding bytes to fill up space")
        }

        val files = Files.list(tempDir).map { it.fileName.toString() }.toList()
        files shouldContain "mihon.log"
        files shouldContain "mihon.1.log"
        files shouldContain "mihon.2.log"
        files shouldContain "mihon.3.log"
        // Should not have mihon.4.log
        (files.contains("mihon.4.log")) shouldBe false
    }

    @Test
    fun `DesktopLogger singleton delegates and handles lifecycle`() {
        DesktopLogger.isInitialized() shouldBe false
        DesktopLogger.init(tempDir)
        DesktopLogger.isInitialized() shouldBe true

        DesktopLogger.info("App", "App initialized")
        DesktopLogger.warn("App", "A warning occurred")
        DesktopLogger.error("App", "An error occurred")

        val logFile = tempDir.resolve("mihon.log")
        val content = Files.readString(logFile)
        content shouldContain "App initialized"
        content shouldContain "A warning occurred"
        content shouldContain "An error occurred"

        DesktopLogger.close()
        DesktopLogger.isInitialized() shouldBe false
    }
}
