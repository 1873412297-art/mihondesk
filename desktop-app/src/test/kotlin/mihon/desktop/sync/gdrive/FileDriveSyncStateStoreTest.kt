package mihon.desktop.sync.gdrive

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileDriveSyncStateStoreTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `persists watermarks and cursors across instances`(): Unit = runBlocking {
        val f = dir.resolve("gdrive/sync-state.json")
        val a = FileDriveSyncStateStore(f) { "dev" }
        a.getDeviceId() shouldBe "dev"
        a.nextCursor() shouldBe 1L
        a.nextCursor() shouldBe 2L
        a.setLastPushCursor(2)
        a.setLastPushWatermark(500)
        a.setLastPullWatermark("phone", 7)
        a.recordSync(1234, "ok")
        val b = FileDriveSyncStateStore(f) { "dev" }
        b.getLastPushCursor() shouldBe 2L
        b.getLastPushWatermark() shouldBe 500L
        b.getAllPeerPullWatermarks() shouldBe mapOf("phone" to 7L)
        b.lastSyncAt() shouldBe 1234L
        b.nextCursor() shouldBe 3L
        b.reset()
        b.getLastPushWatermark() shouldBe 0L
    }

    @Test
    fun `corrupt state is quarantined instead of being overwritten with zeros`(): Unit = runBlocking {
        val f = dir.resolve("gdrive/sync-state.json")
        Files.createDirectories(f.parent)
        Files.writeString(f, "{not json")
        val store = FileDriveSyncStateStore(f) { "dev" }

        store.getLastPushWatermark() shouldBe 0L

        val quarantine = f.resolveSibling("sync-state.json.corrupt")
        Files.exists(quarantine) shouldBe true
        Files.readString(quarantine) shouldBe "{not json"
        Files.exists(f) shouldBe false

        store.setLastPushWatermark(42)
        store.getLastPushWatermark() shouldBe 42L
        Files.readString(quarantine) shouldBe "{not json"
    }
}
