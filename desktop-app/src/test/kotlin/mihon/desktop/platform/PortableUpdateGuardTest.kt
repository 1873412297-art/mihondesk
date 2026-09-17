package mihon.desktop.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.cli.DesktopCommand
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class PortableUpdateGuardTest {
    @TempDir lateinit var directory: Path
    private val token = "a".repeat(32)

    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_UPGRADE_APP")?.let { packaged ->
            listOf(PortableUpdateGuard::class.java, DesktopProfileLock::class.java).forEach { type ->
                val origin = Path.of(type.protectionDomain.codeSource.location.toURI()).toAbsolutePath()
                origin.startsWith(Path.of(packaged).toAbsolutePath()) shouldBe true
                println("PACKAGED_PORTABLE ${type.name}: $origin")
            }
        }
    }

    @Test
    fun `real PowerShell updater rejects the Java profile lock before accessing archive`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val repository = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("scripts/mihondesk-updater.ps1")) }
        Files.createFile(directory.resolve(".portable"))
        val data = Files.createDirectories(directory.resolve("data"))
        val sentinel = Files.writeString(data.resolve("keep.txt"), "untouched")
        val output = directory.resolve("updater-output.txt")
        DesktopProfileLock.acquire(data).use {
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                repository.resolve("scripts/mihondesk-updater.ps1").toString(),
                "-TargetDir", directory.toString(), "-ZipPath", directory.resolve("nonexistent.zip").toString(),
                "-ExpectedSha256", "0".repeat(64), "-NoRestart",
            ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                process.waitFor(15, TimeUnit.SECONDS) shouldBe true
                process.exitValue() shouldBe 1
                // Windows PowerShell may encode localized diagnostics with the OEM code page.
                Files.readAllBytes(output).toString(Charsets.UTF_8) shouldContain "profile is in use"
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
        }
        Files.readString(sentinel) shouldBe "untouched"
        Files.exists(directory.resolve(".mihon-update-in-progress")) shouldBe false
    }

    @Test
    fun `normal startup does not create update state`() {
        PortableUpdateGuard.requireAllowed(directory, DesktopCommand.LaunchUi, emptyMap())
        Files.list(directory).use { it.count() } shouldBe 0L
    }

    @Test
    fun `pending swap blocks UI and background commands even with the validation token`() {
        Files.writeString(directory.resolve(".mihon-update-in-progress"), token)
        listOf(DesktopCommand.LaunchUi, DesktopCommand.BackgroundUpdate, DesktopCommand.BackgroundBackup).forEach {
            shouldThrow<PortableUpdatePendingException> {
                PortableUpdateGuard.requireAllowed(directory, it, mapOf("MIHON_PORTABLE_UPDATE_TOKEN" to token))
            }.applicationDirectory shouldBe directory
        }
    }

    @Test
    fun `only version probe with matching operation token can start during replacement`() {
        Files.writeString(directory.resolve(".mihon-update-in-progress"), token)
        shouldThrow<PortableUpdatePendingException> {
            PortableUpdateGuard.requireAllowed(directory, DesktopCommand.Version, emptyMap())
        }
        shouldThrow<PortableUpdatePendingException> {
            PortableUpdateGuard.requireAllowed(
                directory,
                DesktopCommand.Version,
                mapOf("MIHON_PORTABLE_UPDATE_TOKEN" to "b".repeat(32)),
            )
        }
        PortableUpdateGuard.requireAllowed(
            directory,
            DesktopCommand.Version,
            mapOf("MIHON_PORTABLE_UPDATE_TOKEN" to token),
        )
    }

    @Test
    fun `invalid or unreadable update state fails closed`() {
        val guard = directory.resolve(".mihon-update-in-progress")
        Files.writeString(guard, "")
        shouldThrow<PortableUpdatePendingException> {
            PortableUpdateGuard.requireAllowed(
                directory,
                DesktopCommand.Version,
                mapOf("MIHON_PORTABLE_UPDATE_TOKEN" to ""),
            )
        }
        Files.delete(guard)
        Files.createDirectory(guard)
        shouldThrow<PortableUpdatePendingException> {
            PortableUpdateGuard.requireAllowed(directory, DesktopCommand.LaunchUi, emptyMap())
        }
    }
}
