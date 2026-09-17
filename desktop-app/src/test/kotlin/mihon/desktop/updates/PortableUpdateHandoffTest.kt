package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PortableUpdateHandoffTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `source launch and external profile cannot request automatic replacement`(): Unit = runBlocking {
        val target = Files.createDirectory(directory.resolve("application"))
        Files.writeString(target.resolve(".portable"), "")
        for (profile in listOf(target.resolve("data"), directory.resolve("custom-data"))) {
            val handoff = PortableUpdateHandoff(target, profile)
            handoff.available shouldBe false
            var refused = false
            try {
                handoff.prepare(directory.resolve("missing.zip"), "a".repeat(64))
            } catch (
                _: IllegalStateException,
            ) {
                refused = true
            }
            refused shouldBe true
        }
        Files.list(directory).use { it.count() } shouldBe 1L
    }

    @Test
    fun `receipt resolves only owned sibling directory and distinguishes incomplete result`() {
        val target = Files.createDirectory(directory.resolve("application"))
        val profile = Files.createDirectory(target.resolve("data"))
        val handoff = PortableUpdateHandoff(target, profile)
        val pointer = profile.resolve(".mihon-update-receipt")
        handoff.lastOutcome() shouldBe null
        Files.writeString(pointer, "../invalid/receipt")
        handoff.lastOutcome() shouldBe null
        val token = "a".repeat(32)
        Files.writeString(pointer, token)
        handoff.lastOutcome()?.succeeded shouldBe false
        val update = Files.createDirectory(directory.resolve(".mihon-handoff-$token"))
        Files.writeString(update.resolve("result"), "updated")
        handoff.lastOutcome() shouldBe AppUpdateOutcome(true, update.resolve("update.log"))
        Files.writeString(update.resolve("result"), "failed")
        handoff.lastOutcome()?.succeeded shouldBe false
    }

    @Test
    fun `trusted updater resource supports the readiness protocol`() {
        val script = requireNotNull(javaClass.getResourceAsStream("/mihondesk-updater.ps1")).bufferedReader().use {
            it.readText()
        }
        script.contains("HandoffToken") shouldBe true
        script.contains("CallerStartMillis") shouldBe true
    }
}
