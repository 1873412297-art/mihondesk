package mihon.desktop.updates

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class InstallerUpdateHandoffTest {
    @TempDir
    lateinit var root: Path

    private val self: ProcessHandle = ProcessHandle.current()
    private val selfCommand: Path = Path.of(self.info().command().orElseThrow())

    /** A handoff for an application directory this process does not run from. */
    private fun handoff(started: MutableList<List<String>>, applicationDirectory: Path = root.resolve("app")) =
        InstallerUpdateHandoff(
            applicationDirectory = applicationDirectory,
            stateDirectory = root.resolve("state"),
            caller = self,
            windowsDirectory = root.resolve("Windows"),
            startHelper = { started += it },
        )

    /** A handoff whose launcher is the JVM running this test, so it counts as packaged. */
    private fun packaged(started: MutableList<List<String>>) = InstallerUpdateHandoff(
        applicationDirectory = selfCommand.parent,
        stateDirectory = root.resolve("state"),
        caller = self,
        windowsDirectory = root.resolve("Windows"),
        launcherName = selfCommand.fileName.toString(),
        startHelper = { started += it },
    )

    private fun setup(content: String = "setup program"): Pair<Path, String> {
        val file = Files.writeString(root.resolve("mihondesk-9.9.0.exe"), content)
        return file to fileSha256(file)
    }

    private fun decode(command: List<String>): String =
        String(Base64.getDecoder().decode(command.last()), Charsets.UTF_16LE)

    @Test
    fun `only the packaged launcher can hand over to the installer`() {
        handoff(mutableListOf()).available shouldBe false
        packaged(mutableListOf()).available shouldBe true
        InstallerUpdateHandoff(
            selfCommand.parent,
            root,
            caller = self,
            windowsDirectory = null,
            launcherName = selfCommand.fileName.toString(),
        ).available shouldBe false
    }

    @Test
    fun `the helper waits for the app, runs the verified setup program and reopens the app`() {
        val handoff = handoff(mutableListOf())
        val installer = root.resolve("it's here").resolve("mihondesk-9.9.0.exe")
        val command = handoff.helperCommand(installer, callerPid = 4242)

        command.first() shouldBe root.resolve("Windows/System32/WindowsPowerShell/v1.0/powershell.exe").toString()
        command[command.size - 2] shouldBe "-EncodedCommand"
        val script = decode(command)
        script shouldContain "Wait-Process -Id 4242"
        // Single quotes in a path are doubled inside a PowerShell literal, never left to expand.
        script shouldContain "Start-Process -FilePath '${installer.toString().replace("'", "''")}' -PassThru -Wait"
        script shouldContain "if (\$setup.ExitCode -in 0, 3010) { Start-Process -FilePath '"
        script shouldContain root.resolve("app").resolve("mihondesk.exe").toString()
    }

    @Test
    fun `the setup program is verified again before handing over`(): Unit = runBlocking {
        val started = mutableListOf<List<String>>()
        val handoff = packaged(started)
        val (file, sha) = setup()
        Files.writeString(file, "replaced after download")
        shouldThrow<IllegalStateException> { handoff.prepare(file, sha) }
        shouldThrow<IllegalArgumentException> { handoff.prepare(root.resolve("setup.ps1"), sha) }

        val (good, goodSha) = setup()
        val prepared = handoff.prepare(good, goodSha)
        // Nothing starts until the update is committed, right before the app exits.
        started shouldBe emptyList()
        prepared.commit()
        started.size shouldBe 1
        decode(started.single()) shouldContain "Wait-Process -Id ${self.pid()}"
    }

    @Test
    fun `the last outcome reflects the setup program's exit code`() {
        val handoff = handoff(mutableListOf())
        handoff.lastOutcome() shouldBe null
        val log = Files.createDirectories(root.resolve("state")).resolve("installer-update.log")
        Files.writeString(log, "exit=0\r\n")
        handoff.lastOutcome() shouldBe AppUpdateOutcome(succeeded = true, logFile = log)
        Files.writeString(log, "exit=3010")
        handoff.lastOutcome()?.succeeded shouldBe true
        Files.writeString(log, "exit=1602")
        handoff.lastOutcome()?.succeeded shouldBe false
    }

    @Test
    fun `prepare refuses anything but a verified setup program`(): Unit = runBlocking {
        val handoff = handoff(mutableListOf())
        val (file, sha) = setup()
        // Not the packaged launcher here, so nothing is handed over at all.
        shouldThrow<IllegalStateException> { handoff.prepare(file, sha) }
    }
}
