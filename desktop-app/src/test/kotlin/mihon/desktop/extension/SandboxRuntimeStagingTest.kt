package mihon.desktop.extension

import com.sun.jna.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class SandboxRuntimeStagingTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `a source is staged once and released with the last lease`() {
        val staging = staging(tempDir.resolve("stages"))
        val source = runtimeSource("runtime-a")
        val first = staging.acquire(source)
        val second = staging.acquire(source)
        assertEquals(first.absolutePath, second.absolutePath, "one source must keep one staged copy")

        staging.release(source.absolutePath)
        assertTrue(first.isDirectory, "the copy must survive while another lease is live")

        staging.release(source.absolutePath)
        assertTrue(!first.exists(), "the copy must be removed with the last lease")
    }

    @Test
    fun `each staged source is released on its own`() {
        val staging = staging(tempDir.resolve("stages"))
        val javaRuntime = runtimeSource("runtime-a")
        val packagedRuntime = runtimeSource("runtime-b")
        val first = staging.acquire(javaRuntime)
        val second = staging.acquire(packagedRuntime)
        assertNotEquals(first.absolutePath, second.absolutePath)

        staging.release(javaRuntime.absolutePath)
        assertTrue(!first.exists(), "releasing one source must not keep its copy")
        assertTrue(second.isDirectory, "releasing one source must not release another")

        staging.release(packagedRuntime.absolutePath)
        assertTrue(!second.exists())
    }

    @Test
    fun `close releases every runtime the launcher staged`() {
        // Two stages from one launcher: the second lease must not shadow the first one.
        val javaRuntime = runtimeSource("launcher-runtime-a")
        val packagedRuntime = runtimeSource("launcher-runtime-b")
        val first = SandboxRuntimeStaging.shared.acquire(javaRuntime)
        val second = SandboxRuntimeStaging.shared.acquire(packagedRuntime)
        val launcher = WindowsAppContainerLauncher(tempDir.resolve("host").toFile(), 256L * 1024L * 1024L)

        @Suppress("UNCHECKED_CAST")
        val leases = WindowsAppContainerLauncher::class.java.getDeclaredField("runtimeLeases")
            .apply { isAccessible = true }
            .get(launcher) as MutableList<String>
        leases.add(javaRuntime.absolutePath)
        leases.add(packagedRuntime.absolutePath)

        launcher.close()
        assertTrue(!first.exists(), "close must release every staged runtime, not only the last one")
        assertTrue(!second.exists())

        launcher.close()
    }

    @Test
    fun `release drops the lease even when the copy cannot be deleted`() {
        assumeTrue(Platform.isWindows(), "a read-only file only blocks deletion on Windows")
        val staging = staging(tempDir.resolve("stages"))
        val source = runtimeSource("runtime-a")
        val staged = staging.acquire(source)
        val locked = staged.toPath().resolve("locked.txt")
        Files.writeString(locked, "held")
        Files.setAttribute(locked, "dos:readonly", true)

        // A copy that cannot be deleted must neither fail close nor keep its lease forever.
        try {
            staging.release(source.absolutePath)
            assertTrue(staged.isDirectory, "the undeletable copy stays behind for the stale sweep")

            staging.release(source.absolutePath)
            val restaged = staging.acquire(source)
            assertNotEquals(staged.absolutePath, restaged.absolutePath, "a stranded copy must not be reused")
        } finally {
            runCatching { Files.setAttribute(locked, "dos:readonly", false) }
        }
    }

    @Test
    fun `stale sweep removes only runtimes older than the threshold`() {
        val root = Files.createDirectories(tempDir.resolve("stages"))
        val stale = stagedRuntime(root, "mihonw-sandbox-runtime-stale", HOURS_30)
        val fresh = stagedRuntime(root, "mihonw-sandbox-runtime-fresh", MINUTES_5)
        val unrelated = Files.createDirectories(root.resolve("other-staging"))
        val staging = staging(root)

        staging.acquire(runtimeSource("runtime-a"))
        assertTrue(!stale.toFile().exists(), "a copy older than the threshold must be swept")
        assertTrue(fresh.toFile().isDirectory, "a recent copy must survive the sweep")
        assertTrue(unrelated.toFile().isDirectory, "entries outside the staged prefix must be untouched")

        // Lazy and once per process: a leftover that appears later waits for the next process.
        val late = stagedRuntime(root, "mihonw-sandbox-runtime-late", HOURS_30)
        staging.acquire(runtimeSource("runtime-b"))
        assertTrue(late.toFile().isDirectory, "the sweep must not run once per staging")
    }

    @Test
    fun `sweep keeps a stale runtime that a running host uses`() {
        val root = Files.createDirectories(tempDir.resolve("stages"))
        val stale = stagedRuntime(root, "mihonw-sandbox-runtime-running", HOURS_30)
        val image = Files.createDirectories(stale.resolve("bin")).resolve("java.exe")
        Files.writeString(image, "image")
        Files.setLastModifiedTime(stale, age(HOURS_30))
        val staging = SandboxRuntimeStaging(root, ONE_DAY_MILLIS) { setOf(image.toAbsolutePath().toString()) }

        staging.acquire(runtimeSource("runtime-a"))
        assertTrue(stale.toFile().isDirectory, "a copy an active host process runs from must survive")
    }

    private fun staging(root: Path): SandboxRuntimeStaging =
        SandboxRuntimeStaging(Files.createDirectories(root), ONE_DAY_MILLIS) { emptySet() }

    private fun runtimeSource(name: String): File = tempDir.resolve(name).toFile().also { source ->
        File(source, "bin").mkdirs()
        File(source, "bin/java.exe").writeText("staged runtime")
    }

    private fun stagedRuntime(root: Path, name: String, ageMillis: Long): Path =
        Files.createDirectories(root.resolve(name)).also { directory ->
            Files.writeString(directory.resolve("marker.txt"), "staged")
            Files.setLastModifiedTime(directory, age(ageMillis))
        }

    private fun age(ageMillis: Long): FileTime = FileTime.fromMillis(System.currentTimeMillis() - ageMillis)

    private companion object {
        const val MINUTES_5 = 5L * 60L * 1000L
        const val HOURS_30 = 30L * 60L * 60L * 1000L
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
