package mihon.desktop

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class TestRuntimeRootsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `create tracks every root and release deletes just that root`() {
        val roots = TestRuntimeRoots(tempDir)

        val first = roots.create()
        val second = roots.create()

        roots.trackedRoots() shouldContainExactly listOf(first, second)
        Files.isDirectory(first) shouldBe true

        roots.release(first)

        roots.trackedRoots() shouldContainExactly listOf(second)
        Files.exists(first) shouldBe false
        Files.isDirectory(second) shouldBe true
    }

    @Test
    fun `releaseAll removes every tracked root`() {
        val roots = TestRuntimeRoots(tempDir)
        val created = listOf(roots.create(), roots.create(), roots.create())

        roots.releaseAll()

        roots.trackedRoots() shouldBe emptyList()
        created.forEach { Files.exists(it) shouldBe false }
    }

    @Test
    fun `stale roots are swept lazily, once per process, before the first root`() {
        val stale = Files.createDirectory(tempDir.resolve("mihon-runtime-test-old"))
        Files.writeString(stale.resolve("preferences.properties"), "left by a killed JVM")
        Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() - 60_000))

        val roots = TestRuntimeRoots(tempDir, staleAgeMillis = 1_000L)
        Files.isDirectory(stale) shouldBe true // constructing the tracker alone sweeps nothing

        roots.create()
        Files.exists(stale) shouldBe false

        val late = Files.createDirectory(tempDir.resolve("mihon-runtime-test-late"))
        Files.setLastModifiedTime(late, FileTime.fromMillis(System.currentTimeMillis() - 60_000))
        roots.create()
        Files.isDirectory(late) shouldBe true

        roots.releaseAll()
    }

    @Test
    fun `sweep removes only roots past the age threshold and leaves other entries alone`() {
        val stale = Files.createDirectory(tempDir.resolve("mihon-runtime-test-stale"))
        Files.writeString(Files.createDirectories(stale.resolve("database")).resolve("library.db"), "bytes")
        Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() - 180_000))
        val fresh = Files.createDirectory(tempDir.resolve("mihon-runtime-test-fresh"))
        val unrelated = Files.createDirectory(tempDir.resolve("mihon-other"))

        val roots = TestRuntimeRoots(tempDir, staleAgeMillis = 60_000L)
        roots.sweepStaleRoots()

        Files.exists(stale) shouldBe false
        Files.isDirectory(fresh) shouldBe true
        Files.isDirectory(unrelated) shouldBe true
    }

    @Test
    fun `sweep keeps a root this process still tracks`() {
        val roots = TestRuntimeRoots(tempDir, staleAgeMillis = 1_000L)
        val tracked = roots.create()
        Files.setLastModifiedTime(tracked, FileTime.fromMillis(System.currentTimeMillis() - 60_000))

        roots.sweepStaleRoots()

        Files.isDirectory(tracked) shouldBe true
        roots.trackedRoots() shouldContainExactly listOf(tracked)
    }

    @Test
    fun `sweep tolerates a temp root that does not exist`() {
        TestRuntimeRoots(tempDir.resolve("missing"), staleAgeMillis = 1_000L).sweepStaleRoots()
    }

    @Test
    fun `forTesting registers its data root and shutdown leaves it on disk`() {
        val runtime = DesktopRuntime.forTesting()
        val root = runtime.directories.root
        try {
            TestRuntimeRoots.shared.trackedRoots() shouldContain root
            Files.isRegularFile(root.resolve("database").resolve("library.db")) shouldBe true
        } finally {
            runBlocking { runtime.shutdown() }
        }

        // Tests read their profile after shutdown(), so roots only go away when the JVM exits.
        Files.isRegularFile(root.resolve("database").resolve("library.db")) shouldBe true
        TestRuntimeRoots.shared.trackedRoots() shouldContain root

        TestRuntimeRoots.shared.release(root)
        Files.exists(root) shouldBe false
    }
}
