package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PortableLayoutTest {
    @TempDir
    lateinit var tempDir: Path

    private val exe: Path get() = tempDir.resolve("MihonW")
    private val noArgs = emptyArray<String>()
    private var savedTmp: String? = null

    @BeforeEach
    fun createExecutableDirectory() {
        Files.createDirectories(exe)
        savedTmp = System.getProperty("java.io.tmpdir")
    }

    @AfterEach
    fun restoreTemporaryDirectory() {
        savedTmp?.let { System.setProperty("java.io.tmpdir", it) }
        System.clearProperty("skiko.data.path")
        System.clearProperty("jna.tmpdir")
    }

    @Test
    fun `nothing beside the executable means an installed launch`() {
        PortableLayout.detect(noArgs, exe) shouldBe PortableLayout(false, null)
    }

    @Test
    fun `the shipped dot portable marker keeps the data folder`() {
        Files.createFile(exe.resolve(".portable"))
        PortableLayout.detect(noArgs, exe) shouldBe PortableLayout(true, exe.resolve("data"))
    }

    @Test
    fun `a visible portable marker file behaves like the dot marker`() {
        Files.createFile(exe.resolve("portable"))
        PortableLayout.detect(noArgs, exe) shouldBe PortableLayout(true, exe.resolve("data"))
    }

    @Test
    fun `a directory named portable is not a marker`() {
        Files.createDirectories(exe.resolve("portable"))
        PortableLayout.detect(noArgs, exe).portable shouldBe false
    }

    @Test
    fun `a portable-data directory is itself the profile and wins over the markers`() {
        Files.createFile(exe.resolve(".portable"))
        Files.createDirectories(exe.resolve("portable-data"))
        PortableLayout.detect(noArgs, exe) shouldBe PortableLayout(true, exe.resolve("portable-data"))
    }

    @Test
    fun `the command line flag still selects portable mode`() {
        PortableLayout.detect(arrayOf("--portable"), exe) shouldBe PortableLayout(true, exe.resolve("data"))
    }

    @Test
    fun `profile directories resolve every store under portable-data`() {
        Files.createDirectories(exe.resolve("portable-data"))
        val profile = DesktopProfileDirectories.resolve(
            noArgs,
            mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            exe,
        )
        val root = exe.resolve("portable-data").toAbsolutePath().normalize()
        profile.root shouldBe root
        listOf(profile.database, profile.extensions, profile.covers, profile.cache, profile.logs)
            .all { it.startsWith(root) } shouldBe true
    }

    @Test
    fun `an explicit data dir overrides the detected portable root`() {
        Files.createDirectories(exe.resolve("portable-data"))
        val chosen = tempDir.resolve("chosen")
        val profile = DesktopProfileDirectories.resolve(arrayOf("--data-dir=$chosen"), emptyMap(), exe)
        profile.root shouldBe chosen.toAbsolutePath().normalize()
    }

    @Test
    fun `portable launches move the temp directory into the profile and hand it to children`() {
        val profile = AppDirectories(exe.resolve("portable-data"))
        val temporary = ProfileTemporaryDirectory.apply(profile, portable = true)!!

        temporary.startsWith(profile.root) shouldBe true
        Files.isDirectory(temporary) shouldBe true
        // Not asserting Files.createTempFile here: the JDK caches java.io.tmpdir on first use, and
        // this test JVM has used it already. That is why main() applies the redirect before
        // anything else can create a temp file.
        Path.of(System.getProperty("java.io.tmpdir")) shouldBe temporary
        System.getProperty("jna.tmpdir") shouldBe temporary.toString()
        Path.of(System.getProperty("skiko.data.path")).startsWith(profile.root) shouldBe true
        ProfileTemporaryDirectory.childEnvironment() shouldBe mapOf("TEMP" to "$temporary", "TMP" to "$temporary")
        ProfileTemporaryDirectory.childJvmArguments() shouldBe listOf("-Djava.io.tmpdir=$temporary")
    }

    @Test
    fun `installed launches keep the system temp directory`() {
        val before = System.getProperty("java.io.tmpdir")
        ProfileTemporaryDirectory.apply(AppDirectories(tempDir.resolve("Roaming")), portable = false) shouldBe null
        System.getProperty("java.io.tmpdir") shouldBe before
        ProfileTemporaryDirectory.childEnvironment() shouldBe emptyMap()
    }
}
