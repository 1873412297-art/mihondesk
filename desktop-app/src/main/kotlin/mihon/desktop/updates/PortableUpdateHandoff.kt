package mihon.desktop.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class AppUpdateOutcome(val succeeded: Boolean, val logFile: Path)

interface PreparedAppUpdate {
    fun commit()
    fun abort()
}

interface AppUpdateInstaller {
    val available: Boolean
    fun lastOutcome(): AppUpdateOutcome?
    suspend fun prepare(archive: Path, expectedSha256: String): PreparedAppUpdate
}

/** Uses a trusted embedded helper; a downloaded archive is never executed as a script. */
class PortableUpdateHandoff(
    private val applicationDirectory: Path,
    private val profileDirectory: Path,
    private val caller: ProcessHandle = ProcessHandle.current(),
    private val windowsDirectory: Path? = System.getenv("WINDIR")?.let(Path::of),
) : AppUpdateInstaller {
    private val target = applicationDirectory.toAbsolutePath().normalize()
    private val profile = profileDirectory.toAbsolutePath().normalize()
    private val pointer = profile.resolve(".mihon-update-receipt")

    private fun updateCaller(): ProcessHandle {
        // jpackage may keep a same-executable launcher alive above the JVM child.
        var owner = caller
        while (true) {
            val parent = owner.parent().orElse(null) ?: break
            val command = parent.info().command().orElse(null)?.let(Path::of)?.toAbsolutePath()?.normalize()
            if (command != target.resolve("mihondesk.exe")) break
            owner = parent
        }
        return owner
    }

    override val available: Boolean
        get() = windowsDirectory != null &&
            profile == target.resolve("data") &&
            Files.isRegularFile(target.resolve(".portable"), NOFOLLOW_LINKS) &&
            caller.info().command().orElse(null)?.let(Path::of)?.toAbsolutePath()?.normalize() ==
            target.resolve("mihondesk.exe") &&
            caller.info().startInstant().isPresent

    override fun lastOutcome(): AppUpdateOutcome? = runCatching {
        if (!Files.isRegularFile(pointer, NOFOLLOW_LINKS) || Files.size(pointer) != 32L) return null
        val token = Files.readString(pointer)
        if (!Regex("[a-f0-9]{32}").matches(token)) return null
        val handoff = target.parent.resolve(".mihon-handoff-$token")
        val result = handoff.resolve("result")
        val succeeded =
            Files.isRegularFile(result, NOFOLLOW_LINKS) && Files.size(result) <= 32 &&
                Files.readString(result) == "updated"
        AppUpdateOutcome(succeeded, handoff.resolve("update.log"))
    }.getOrNull()

    override suspend fun prepare(archive: Path, expectedSha256: String): PreparedAppUpdate {
        check(available) { "Automatic updates require the default packaged portable profile" }
        require(Regex("[a-fA-F0-9]{64}").matches(expectedSha256)) { "A verified checksum is required" }
        val token = UUID.randomUUID().toString().replace("-", "")
        val handoff = target.parent.resolve(".mihon-handoff-$token")
        var created = false
        try {
            return withContext(Dispatchers.IO) {
                Files.createDirectory(handoff)
                created = true
                val script = handoff.resolve("updater.ps1")
                requireNotNull(javaClass.getResourceAsStream("/mihondesk-updater.ps1")).use { Files.copy(it, script) }
                val receipt = Files.createTempFile(profile, ".update-receipt-", ".tmp")
                try {
                    Files.writeString(receipt, token)
                    Files.move(receipt, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    Files.deleteIfExists(receipt)
                }
                val updateCaller = updateCaller()
                val command = listOf(
                    requireNotNull(
                        windowsDirectory,
                    ).resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),
                    "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                    "-ZipPath", archive.toAbsolutePath().toString(), "-TargetDir", target.toString(),
                    "-ExpectedSha256", expectedSha256, "-CallerPid", updateCaller.pid().toString(),
                    "-CallerStartMillis", updateCaller.info().startInstant().orElseThrow().toEpochMilli().toString(),
                    "-HandoffToken", token,
                )
                val helper = ProcessBuilder(
                    command,
                ).redirectErrorStream(true).redirectOutput(handoff.resolve("update.log").toFile()).start()
                withTimeout(180_000) {
                    val ready = handoff.resolve("ready")
                    while (true) {
                        check(helper.isAlive) { "Updater preparation failed; see ${handoff.resolve("update.log")}" }
                        if (Files.isRegularFile(ready, NOFOLLOW_LINKS) && Files.size(ready) == 32L &&
                            Files.readString(ready) == token
                        ) {
                            break
                        }
                        delay(100)
                    }
                }
                object : PreparedAppUpdate {
                    override fun commit() {
                        check(helper.isAlive) { "Prepared updater has exited" }
                        Files.writeString(handoff.resolve("commit"), token)
                    }
                    override fun abort() {
                        Files.writeString(handoff.resolve("abort"), token)
                    }
                }
            }
        } catch (error: Exception) {
            if (created) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { Files.writeString(handoff.resolve("abort"), token) }
                }
            }
            throw error
        }
    }
}
