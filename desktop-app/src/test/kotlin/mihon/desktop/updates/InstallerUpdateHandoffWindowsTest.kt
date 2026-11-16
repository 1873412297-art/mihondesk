package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs the real PowerShell helper against stand-in setup programs from System32. */
@EnabledOnOs(OS.WINDOWS)
class InstallerUpdateHandoffWindowsTest {
    @TempDir
    lateinit var root: Path

    @ParameterizedTest
    @CsvSource("whoami.exe, exit=0", "where.exe, exit=2")
    fun `the helper waits for the caller, runs the setup program and records its exit code`(
        standIn: String,
        expected: String,
    ) {
        val windows = Path.of(System.getenv("WINDIR"))
        // A quote and non-ASCII characters in the path must survive the hand-over unexpanded.
        val folder = Files.createDirectories(root.resolve("it's 中文 \$env:TEMP"))
        val setup = Files.copy(windows.resolve("System32").resolve(standIn), folder.resolve("mihondesk-9.9.0.exe"))
        val app = Files.createDirectories(folder.resolve("app"))
        Files.copy(windows.resolve("System32/whoami.exe"), app.resolve("mihondesk.exe"))
        val state = folder.resolve("state")
        val handoff = InstallerUpdateHandoff(app, state, windowsDirectory = windows)
        val caller = ProcessBuilder(windows.resolve("System32/whoami.exe").toString()).start()
        caller.waitFor(30, TimeUnit.SECONDS) shouldBe true

        val helper = ProcessBuilder(handoff.helperCommand(setup, caller.pid())).redirectErrorStream(true).start()
        helper.waitFor(120, TimeUnit.SECONDS) shouldBe true

        Files.readString(state.resolve("installer-update.log")).trim() shouldBe expected
        handoff.lastOutcome()?.succeeded shouldBe (expected == "exit=0")
    }
}
