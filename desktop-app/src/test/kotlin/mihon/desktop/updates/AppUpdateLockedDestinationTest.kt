package mihon.desktop.updates

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@EnabledOnOs(OS.WINDOWS)
class AppUpdateLockedDestinationTest {
    @TempDir lateinit var root: Path

    @ParameterizedTest
    @ValueSource(strings = ["released", "held", "cancelled"])
    fun `Windows destination lock retries briefly and preserves original on persistent failure`(
        mode: String,
    ): Unit = runBlocking {
        val data = "verified payload".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val target = Files.writeString(root.resolve("update.exe"), "original")
        // Permit reading/writing, but prevent replacement until this real Windows handle closes.
        val handle = Kernel32.INSTANCE.CreateFile(
            target.toString(),
            WinNT.GENERIC_READ,
            WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE,
            null,
            WinNT.OPEN_EXISTING,
            0,
            null,
        )
        check(handle != WinBase.INVALID_HANDLE_VALUE)
        var closed = false
        try {
            val publishing = CompletableDeferred<Unit>()
            val service = DesktopAppUpdateService(downloadStream = { ByteArrayInputStream(data) })
            val download = async {
                service.downloadAsset(
                    AppReleaseAsset(
                        "update.exe",
                        "unused",
                        data.size.toLong(),
                    ),
                    target,
                    hash,
                    onPublishing = {
                        publishing.complete(Unit)
                    },
                )
            }
            publishing.await()
            delay(150)
            Files.readString(target) shouldBe "original"
            download.isCompleted shouldBe false
            if (mode == "released") {
                Kernel32.INSTANCE.CloseHandle(handle)
                closed = true
            }
            if (mode == "cancelled") {
                download.cancelAndJoin()
                download.isCancelled shouldBe true
            } else {
                download.await() shouldBe (mode == "released")
            }
            Files.readString(target) shouldBe if (mode == "released") "verified payload" else "original"
            Files.list(root).use { it.count() } shouldBe 1L
        } finally {
            if (!closed) Kernel32.INSTANCE.CloseHandle(handle)
        }
    }
}
