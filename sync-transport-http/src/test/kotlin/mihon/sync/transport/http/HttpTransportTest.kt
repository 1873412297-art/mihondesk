package mihon.sync.transport.http

import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ServerSocket

class HttpTransportTest {

    private val token = "secure-pairing-token-abc"
    private var serverPort = 0
    private var server: SyncServer? = null
    private var store: SqliteChangesetStore? = null

    @BeforeEach
    fun setUp() {
        serverPort = ServerSocket(0).use { it.localPort }
        val s = SqliteChangesetStore.inMemory()
        store = s
        val srv = SyncServer(
            host = "127.0.0.1",
            port = serverPort,
            token = token,
            store = s,
        )
        srv.start(wait = false)
        server = srv
    }

    @AfterEach
    fun tearDown() {
        server?.stop()
        store?.close()
    }

    private fun sampleChangeset(deviceId: String, cursor: Long): Changeset {
        return Changeset(
            deviceId = deviceId,
            baseSchema = 1,
            cursor = cursor,
            producedAt = 1000L + cursor,
            upserts = EntityDelta(),
            tombstones = emptyList(),
        )
    }

    @Test
    fun `health check returns true for valid server`() = runBlocking {
        val transport = HttpTransport("http://127.0.0.1:$serverPort", token)
        assertTrue(transport.checkHealth())

        val badTransport = HttpTransport("http://127.0.0.1:1", token)
        assertFalse(badTransport.checkHealth())
    }

    @Test
    fun `push and pull with per-peer filtering`() = runBlocking {
        val transportA = HttpTransport("http://127.0.0.1:$serverPort", token)
        val transportB = HttpTransport("http://127.0.0.1:$serverPort", token)

        val csA1 = sampleChangeset("devA", 1)
        val csA2 = sampleChangeset("devA", 2)
        val csB1 = sampleChangeset("devB", 1)

        transportA.push(csA1)
        transportA.push(csA2)
        transportB.push(csB1)

        // Pull by devB excluding devB: should see devA:1, devA:2
        val pulledByB = transportB.pull(sinceCursors = emptyMap(), excludeDeviceId = "devB")
        assertEquals(2, pulledByB.size)
        assertEquals(listOf(1L, 2L), pulledByB.map { it.cursor })

        // Pull by devB with since devA:1: should only see devA:2
        val pulledSince1 = transportB.pull(sinceCursors = mapOf("devA" to 1L), excludeDeviceId = "devB")
        assertEquals(1, pulledSince1.size)
        assertEquals(2L, pulledSince1[0].cursor)

        // Head cursor excluding devA: should be devB's 1
        assertEquals(1L, transportA.headCursor(excludeDeviceId = "devA"))

        // Head cursor excluding devB: should be devA's 2
        assertEquals(2L, transportB.headCursor(excludeDeviceId = "devB"))
    }

    @Test
    fun `wrong token throws SyncPairingException`() = runBlocking {
        val badTransport = HttpTransport("http://127.0.0.1:$serverPort", "wrong-token")
        val cs = sampleChangeset("devA", 1)

        assertThrows<SyncPairingException> {
            badTransport.push(cs)
        }

        assertThrows<SyncPairingException> {
            badTransport.pull(emptyMap(), "devA")
        }

        assertThrows<SyncPairingException> {
            badTransport.headCursor("devA")
        }
    }
}
