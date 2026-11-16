package mihon.desktop.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Base64

/**
 * One-click update for installed builds: once MihonDesk has exited, a hidden PowerShell helper starts
 * the verified setup program (through the shell, so Windows can ask for elevation), waits for it and
 * reopens MihonDesk when it succeeds. The setup wizard stays interactive: the EXE installer has no
 * supported silent mode. The helper's result is written to [stateDirectory] for the next start.
 */
class InstallerUpdateHandoff(
    applicationDirectory: Path,
    stateDirectory: Path,
    private val caller: ProcessHandle = ProcessHandle.current(),
    private val windowsDirectory: Path? = System.getenv("WINDIR")?.let(Path::of),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    launcherName: String = "mihondesk.exe",
    private val startHelper: (List<String>) -> Unit = { command ->
        ProcessBuilder(command)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    },
) : AppUpdateInstaller {
    private val launcher = applicationDirectory.toAbsolutePath().normalize().resolve(launcherName)
    private val result = stateDirectory.toAbsolutePath().normalize().resolve("installer-update.log")

    override val wizard: Boolean get() = true

    override val available: Boolean
        get() = windowsDirectory != null && commandOf(caller) == launcher

    override fun lastOutcome(): AppUpdateOutcome? = runCatching {
        if (!Files.isRegularFile(result, NOFOLLOW_LINKS) || Files.size(result) > 4096) return null
        // The jpackage setup program returns msiexec's code: 3010 is success with a reboot pending.
        val code = Files.readString(result).trim()
        AppUpdateOutcome(succeeded = code == "exit=0" || code == "exit=3010", logFile = result)
    }.getOrNull()

    override suspend fun prepare(archive: Path, expectedSha256: String): PreparedAppUpdate {
        check(available) { "Installer updates require the packaged MihonDesk launcher" }
        require(Regex("[a-fA-F0-9]{64}").matches(expectedSha256)) { "A verified checksum is required" }
        val installer = archive.toAbsolutePath().normalize()
        require(installer.fileName.toString().lowercase().endsWith(".exe")) { "Not a setup program: $installer" }
        withContext(ioDispatcher) {
            // Checked again right before handing over: the file sat in a user folder since the download.
            check(hasVerifiedFile(installer, size = 0, sha256 = expectedSha256)) {
                "Setup program changed since download"
            }
            Files.createDirectories(result.parent)
            Files.deleteIfExists(result)
        }
        val command = helperCommand(installer, updateCaller().pid())
        return object : PreparedAppUpdate {
            override fun commit() = startHelper(command)

            override fun abort() = Unit
        }
    }

    /** The outermost process running this launcher (jpackage keeps a launcher above the JVM). */
    private fun updateCaller(): ProcessHandle {
        var owner = caller
        while (true) {
            val parent = owner.parent().orElse(null) ?: break
            if (commandOf(parent) != launcher) break
            owner = parent
        }
        return owner
    }

    internal fun helperCommand(installer: Path, callerPid: Long): List<String> {
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}log = ${quote(result)}
            try {
                Wait-Process -Id $callerPid -Timeout 120 -ErrorAction SilentlyContinue
                ${'$'}setup = Start-Process -FilePath ${quote(installer)} -PassThru -Wait
                Set-Content -LiteralPath ${'$'}log -Value "exit=${'$'}(${'$'}setup.ExitCode)" -Encoding ascii
                if (${'$'}setup.ExitCode -in 0, 3010) { Start-Process -FilePath ${quote(launcher)} }
            } catch {
                Set-Content -LiteralPath ${'$'}log -Value "error=${'$'}(${'$'}_.Exception.Message)" -Encoding ascii
            }
        """.trimIndent()
        return listOf(
            requireNotNull(windowsDirectory).resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),
            "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
            // Encoded, so no path ever goes through command-line quoting.
            "-EncodedCommand", Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)),
        )
    }

    private companion object {
        fun commandOf(process: ProcessHandle): Path? =
            process.info().command().orElse(null)?.let(Path::of)?.toAbsolutePath()?.normalize()

        /** A PowerShell single-quoted literal: nothing inside is expanded, `'` is doubled. */
        fun quote(path: Path): String = "'" + path.toString().replace("'", "''") + "'"

        fun nullDevice(): java.io.File =
            java.io.File(if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null")
    }
}
