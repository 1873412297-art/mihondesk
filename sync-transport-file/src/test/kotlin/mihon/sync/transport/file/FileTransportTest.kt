package mihon.sync.transport.file

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FileTransportTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `push writes file and pull retrieves remote changesets in cursor order`(): Unit = runBlocking {
        val transportA = FileTransport(tempDir, "device-A")
        val transportB = FileTransport(tempDir, "device-B")

        val cs1 = Changeset(
            deviceId = "device-A",
            cursor = 1L,
            producedAt = 1000L,
            upserts = EntityDelta(
                mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/1")),
            ),
        )
        val cs2 = Changeset(
            deviceId = "device-A",
            cursor = 2L,
            producedAt = 2000L,
            upserts = EntityDelta(
                mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/2")),
            ),
        )

        transportA.push(cs2) // pushed out of order
        transportA.push(cs1)

        // Device A pulling should get nothing (skips self)
        transportA.pull(sinceCursor = 0L) shouldBe emptyList()

        // Device B should see both in ascending cursor order
        val pulledByB = transportB.pull(sinceCursor = 0L)
        pulledByB.size shouldBe 2
        pulledByB[0].cursor shouldBe 1L
        pulledByB[1].cursor shouldBe 2L
        pulledByB[0].upserts.mangas.first().url shouldBe "/manga/1"
        pulledByB[1].upserts.mangas.first().url shouldBe "/manga/2"

        // Device B pulling since cursor 1 should get only cursor 2
        val since1 = transportB.pull(sinceCursor = 1L)
        since1.size shouldBe 1
        since1[0].cursor shouldBe 2L

        // Head cursor
        transportB.headCursor() shouldBe 2L
    }
}
