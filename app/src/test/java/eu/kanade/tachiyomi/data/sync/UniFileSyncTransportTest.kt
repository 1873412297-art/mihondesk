package eu.kanade.tachiyomi.data.sync

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class UniFileSyncTransportTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var syncDir: UniFile
    private lateinit var transport: UniFileSyncTransport

    @BeforeEach
    fun setup() {
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val cs = firstArg<CharSequence?>()
            cs == null || cs.isEmpty()
        }
        val dir = File(tempDir.toFile(), "sync")
        dir.mkdirs()
        syncDir = UniFile.fromFile(dir)!!
        transport = UniFileSyncTransport(syncDir, localDeviceId = "local-device")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `push writes changeset file and pull retrieves it for remote devices`() = runBlocking {
        val cs1 = Changeset(
            deviceId = "remote-device-1",
            baseSchema = 1,
            cursor = 10L,
            producedAt = 1000L,
            upserts = EntityDelta(
                mangas = listOf(
                    AndroidBackupManga(source = 1L, url = "/test", title = "Remote Manga"),
                ),
            ),
        )

        val cs2 = Changeset(
            deviceId = "local-device",
            baseSchema = 1,
            cursor = 15L,
            producedAt = 1500L,
            upserts = EntityDelta(),
        )

        transport.push(cs1)
        transport.push(cs2)

        // Pull excluding localDeviceId -> only cs1 should be returned
        val pulled = transport.pull(sinceCursor = 0L, excludeDeviceId = "local-device")
        assertEquals(1, pulled.size)
        assertEquals("remote-device-1", pulled.first().deviceId)
        assertEquals(10L, pulled.first().cursor)
        assertEquals("Remote Manga", pulled.first().upserts.mangas.first().title)

        // Pull since cursor 12 -> cs1 should be excluded
        val pulledLater = transport.pull(sinceCursor = 12L, excludeDeviceId = "local-device")
        assertTrue(pulledLater.isEmpty())

        // headCursor excluding localDeviceId
        val head = transport.headCursor(excludeDeviceId = "local-device")
        assertEquals(10L, head)
    }

    @Test
    fun `pull with per-peer sinceCursors map filters correctly`() = runBlocking {
        val csA1 = Changeset(
            deviceId = "device-A",
            baseSchema = 1,
            cursor = 1L,
            producedAt = 100L,
            upserts = EntityDelta(),
        )
        val csA2 = Changeset(
            deviceId = "device-A",
            baseSchema = 1,
            cursor = 2L,
            producedAt = 200L,
            upserts = EntityDelta(),
        )
        val csB1 = Changeset(
            deviceId = "device-B",
            baseSchema = 1,
            cursor = 5L,
            producedAt = 150L,
            upserts = EntityDelta(),
        )

        transport.push(csA1)
        transport.push(csA2)
        transport.push(csB1)

        // Device A has seen up to cursor 1, Device B seen up to cursor 10 -> only A2 should be pulled
        val pulled = transport.pull(
            sinceCursors = mapOf("device-A" to 1L, "device-B" to 10L),
            excludeDeviceId = "local-device",
        )
        assertEquals(1, pulled.size)
        assertEquals("device-A", pulled.first().deviceId)
        assertEquals(2L, pulled.first().cursor)
    }
}
