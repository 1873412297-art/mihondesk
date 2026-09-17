package mihon.desktop.updates

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Opens/closes only the real EXE copied into this test's uniquely owned evidence directory. */
@EnabledIfEnvironmentVariable(named = "MIHON_HANDOFF_IMAGE", matches = ".+")
class PortableUpdateHandoffIntegrationTest {
    @Test
    fun `real packaged caller closes cleanly and the replacement restarts with its profile`(): Unit = runBlocking {
        val image = Path.of(System.getenv("MIHON_HANDOFF_IMAGE")).toAbsolutePath()
        val archive = Path.of(System.getenv("MIHON_HANDOFF_ZIP")).toAbsolutePath()
        val root = Files.createDirectories(
            Path.of(System.getenv("MIHON_HANDOFF_EVIDENCE")).resolve(UUID.randomUUID().toString()),
        )
        val target = root.resolve("portable 中文's app")
        Files.walk(image).use { paths ->
            paths.forEach { source ->
                val destination = target.resolve(image.relativize(source))
                if (Files.isDirectory(source)) Files.createDirectories(destination) else Files.copy(source, destination)
            }
        }
        Files.writeString(target.resolve(".portable"), "")
        Files.writeString(target.resolve("old-program-sentinel"), "old program")
        val profile = Files.createDirectories(target.resolve("data"))
        Files.writeString(profile.resolve("profile-sentinel"), "preserved private profile")
        val exe = target.resolve("mihondesk.exe")
        val caller = ProcessBuilder(
            exe.toString(),
        ).redirectErrorStream(true).redirectOutput(root.resolve("caller.log").toFile()).start()
        var prepared: PreparedAppUpdate? = null
        try {
            val windowOwner = withTimeout(30_000) {
                var owner: ProcessHandle? = null
                while (owner == null) {
                    check(caller.isAlive)
                    owner = ownedProcesses(exe).firstOrNull { windowFor(it.pid()) != null }
                    if (owner == null) delay(100)
                }
                owner
            }
            val handoff = PortableUpdateHandoff(target, profile, windowOwner)
            handoff.available shouldBe true
            PortableUpdateHandoff(target, root.resolve("external-profile"), windowOwner).available shouldBe false
            val hash = Files.newInputStream(archive).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            val corrupted = Files.writeString(root.resolve("changed.zip"), "changed after download")
            var refused = false
            try {
                handoff.prepare(corrupted, hash)
            } catch (_: IllegalStateException) {
                refused = true
            }
            refused shouldBe true
            caller.isAlive shouldBe true
            Files.exists(target.resolve("old-program-sentinel")) shouldBe true
            prepared = handoff.prepare(archive, hash)
            caller.isAlive shouldBe true
            prepared.commit()
            closeWindow(windowOwner, exe)
            withTimeout(30_000) { while (caller.isAlive) delay(100) }
            caller.exitValue() shouldBe 0
            withTimeout(120_000) { while (handoff.lastOutcome()?.succeeded != true) delay(200) }
            val replacement = withTimeout(30_000) {
                var found: ProcessHandle? = null
                while (found ==
                    null
                ) {
                    found = ownedProcesses(exe).firstOrNull { windowFor(it.pid()) != null }
                    if (found ==
                        null
                    ) {
                        delay(100)
                    }
                }
                found
            }
            Files.exists(target.resolve("old-program-sentinel")) shouldBe false
            Files.readString(profile.resolve("profile-sentinel")) shouldBe "preserved private profile"
            closeWindow(replacement, exe)
            withTimeout(30_000) { while (replacement.isAlive) delay(100) }
            val backups = Files.list(root).use { paths ->
                paths.filter { it.fileName.toString().startsWith(".mihon-rollback-") }.toList()
            }
            backups.size shouldBe 1
            Files.readString(backups.single().resolve("old-program-sentinel")) shouldBe "old program"
            Files.readString(backups.single().resolve("data/profile-sentinel")) shouldBe "preserved private profile"
            Files.writeString(
                root.resolve("result.txt"),
                "PASS\ncaller=${caller.pid()} exit=0\nreplacement=${replacement.pid()}\nlog=${handoff.lastOutcome()?.logFile}\n",
            )
            println("REAL_PORTABLE_HANDOFF $root")
        } finally {
            runCatching { prepared?.abort() }
            val owned = ownedProcesses(exe)
            owned.forEach { process -> runCatching { closeWindow(process, exe) } }
            kotlinx.coroutines.withTimeoutOrNull(10_000) { while (owned.any { it.isAlive }) delay(100) }
            owned.filter { it.isAlive && it.info().command().orElse(null)?.let(Path::of) == exe }.forEach {
                it.destroy()
            }
        }
    }

    private fun ownedProcesses(exe: Path): List<ProcessHandle> = ProcessHandle.allProcesses().use { handles ->
        handles.filter { it.info().command().orElse(null)?.let(Path::of) == exe }.toList()
    }

    private fun closeWindow(process: ProcessHandle, exe: Path) {
        check(process.info().command().orElse(null)?.let(Path::of) == exe)
        val window = requireNotNull(windowFor(process.pid()))
        User32.INSTANCE.PostMessage(window, 0x0010, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }

    private fun windowFor(pid: Long): WinDef.HWND? {
        var result: WinDef.HWND? = null
        User32.INSTANCE.EnumWindows({ window, _ ->
            val owner = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(window, owner)
            if (owner.value.toLong() == pid) {
                val title = CharArray(256)
                val length = User32.INSTANCE.GetWindowText(window, title, title.size)
                if (String(title, 0, length) == "mihondesk") result = window
            }
            true
        }, null)
        return result
    }
}
