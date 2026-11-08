package mihon.desktop.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mihon.desktop.platform.BackgroundProcessResult
import mihon.desktop.platform.BackgroundProcessRunner
import mihon.desktop.platform.WindowsBackgroundScheduler
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BackgroundScheduleCoordinatorTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `failed schedule change restores the previous interval`() = runBlocking {
        val coordinator = coordinator(rejectingRunner())
        val restored = mutableListOf<Pair<Int, Int>>()
        val failures = mutableListOf<String>()

        coordinator.observe(schedule(6), restore = { restored += it }) { failures += it }.join()

        assertEquals(listOf(0 to 0), restored)
        assertTrue(failures.single().contains("denied"))
    }

    @Test
    fun `successful schedule change is kept`() = runBlocking {
        val coordinator = coordinator(acceptingRunner())
        val restored = mutableListOf<Pair<Int, Int>>()

        coordinator.observe(schedule(12), restore = { restored += it }) { error("unexpected: $it") }.join()

        assertTrue(restored.isEmpty())
    }

    @Test
    fun `a newer interval supersedes a schedule the system rejects`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val applied = AtomicInteger()
        val coordinator = coordinator { arguments ->
            if (arguments.first() == "/Query") {
                BackgroundProcessResult(1, "The system cannot find the file specified.")
            } else {
                val xml = readSchedulerXml(Path.of(arguments[arguments.indexOf("/XML") + 1]))
                if (xml.contains("<Interval>PT6H</Interval>")) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "timed out waiting to release the old schedule" }
                    BackgroundProcessResult(1, "denied")
                } else {
                    applied.incrementAndGet()
                    BackgroundProcessResult(0, "ok")
                }
            }
        }
        val restored = mutableListOf<Pair<Int, Int>>()
        val first = coordinator.observe(schedule(6), restore = { restored += it }) { }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        val second = coordinator.observe(schedule(12), restore = { restored += it }) { error("unexpected: $it") }
        release.countDown()
        first.join()
        second.join()

        assertTrue(restored.isEmpty())
        assertEquals(1, applied.get())
    }

    @Test
    fun `enabling through the switch registers once and remembers the applied schedule`() = runBlocking {
        val creates = AtomicInteger()
        val coordinator = coordinator { arguments ->
            if (arguments.first() == "/Query") {
                BackgroundProcessResult(1, "The system cannot find the file specified.")
            } else {
                if (arguments.first() == "/Create") creates.incrementAndGet()
                BackgroundProcessResult(0, "ok")
            }
        }

        assertEquals(null, coordinator.setEnabled(true, 12 to 0))
        coordinator.observe(schedule(12), restore = { error("unexpected restore") }) { error("unexpected: $it") }.join()

        assertEquals(1, creates.get())
    }

    @Test
    fun `disabling through the switch removes both scheduled tasks`() = runBlocking {
        val deletes = AtomicInteger()
        val coordinator = coordinator { arguments ->
            if (arguments.first() == "/Query") {
                BackgroundProcessResult(0, "<Task />")
            } else {
                if (arguments.first() == "/Delete") deletes.incrementAndGet()
                BackgroundProcessResult(0, "ok")
            }
        }

        assertEquals(null, coordinator.setEnabled(false, 12 to 0))

        assertEquals(2, deletes.get())
    }

    @Test
    fun `a rejected switch change restores the previously applied task state`() = runBlocking {
        val createAttempts = AtomicInteger()
        val coordinator = coordinator { arguments ->
            if (arguments.first() == "/Query") {
                BackgroundProcessResult(1, "The system cannot find the file specified.")
            } else if (arguments.first() == "/Create" && createAttempts.getAndIncrement() == 0) {
                BackgroundProcessResult(1, "denied")
            } else {
                BackgroundProcessResult(0, "ok")
            }
        }

        val failure = coordinator.setEnabled(true, 12 to 0)

        assertTrue(failure != null && failure.contains("denied"))
        // The failed registration is not retried; the rollback removes the previously absent tasks.
        assertEquals(1, createAttempts.get())
    }

    private fun CoroutineScope.coordinator(runner: BackgroundProcessRunner) = BackgroundScheduleCoordinator(
        WindowsBackgroundScheduler(Files.createFile(directory.resolve("MihonW.exe")), directory, runner),
        this,
        0 to 0,
        initiallyEnabled = true,
    )

    private fun schedule(updateHours: Int): DesktopPreferences =
        DesktopPreferenceStore(directory.resolve("prefs.properties")).load().copy(
            backgroundTasksEnabled = true,
            libraryUpdateIntervalHours = updateHours,
        )

    private fun rejectingRunner(): BackgroundProcessRunner = BackgroundProcessRunner { arguments ->
        if (arguments.first() == "/Query") {
            BackgroundProcessResult(1, "The system cannot find the file specified.")
        } else {
            BackgroundProcessResult(1, "denied")
        }
    }

    private fun readSchedulerXml(path: Path): String {
        val bytes = Files.readAllBytes(path)
        val utf16 = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        return String(bytes, if (utf16) Charsets.UTF_16LE else Charsets.UTF_8)
    }

    private fun acceptingRunner(): BackgroundProcessRunner = BackgroundProcessRunner { arguments ->
        if (arguments.first() == "/Query") {
            BackgroundProcessResult(1, "The system cannot find the file specified.")
        } else {
            BackgroundProcessResult(0, "ok")
        }
    }
}
