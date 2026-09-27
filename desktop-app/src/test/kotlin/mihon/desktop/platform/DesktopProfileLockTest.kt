package mihon.desktop.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopProfileLockTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `acquiring lock creates lock file with current process id`() {
        val root = tempDir.resolve("profile")
        val lock = DesktopProfileLock.acquire(root)
        val lockFile = root.resolve(".mihon-profile.lock")
        Files.exists(lockFile) shouldBe true
        lock.close()
        val content = Files.readString(lockFile)
        content shouldBe ProcessHandle.current().pid().toString()
    }

    @Test
    fun `second acquire on same profile throws ProfileInUseException`() {
        val root = tempDir.resolve("profile-conflict")
        DesktopProfileLock.acquire(root).use { _ ->
            val exception = shouldThrow<ProfileInUseException> {
                DesktopProfileLock.acquire(root)
            }
            exception.message shouldContain "Profile is already open"
        }
    }

    @Test
    fun `releasing lock allows subsequent acquisition`() {
        val root = tempDir.resolve("profile-release")
        val firstLock = DesktopProfileLock.acquire(root)
        firstLock.close()

        val secondLock = DesktopProfileLock.acquire(root)
        secondLock.close()
    }
}
