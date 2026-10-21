package mihon.sync.transport.http

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import mihon.sync.transport.api.SyncCursorConflictException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ServerSocket

class HttpCursorConflictTest {
    @Test
    fun `cursor conflicts carry the cursor the server expects next`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val accepted = mutableListOf<String>()
        val server = SyncServer(
            host = "127.0.0.1",
            port = port,
            token = "t",
            store = SqliteChangesetStore.inMemory(),
            onChangesetAccepted = { accepted += it },
        )
        server.start(wait = false)
        try {
            val transport = HttpTransport(baseUrl = "http://127.0.0.1:$port", token = "t", ownsClient = true)
            transport.push(Changeset(deviceId = "dev", cursor = 1, producedAt = 1, upserts = EntityDelta()))
            accepted shouldBe listOf("dev")

            val gap = assertThrows<SyncCursorConflictException> {
                runBlocking {
                    transport.push(Changeset(deviceId = "dev", cursor = 9, producedAt = 2, upserts = EntityDelta()))
                }
            }
            gap.expectedCursor shouldBe 2L

            val behind = assertThrows<SyncCursorConflictException> {
                runBlocking {
                    transport.push(Changeset(deviceId = "dev", cursor = 1, producedAt = 3, upserts = EntityDelta()))
                }
            }
            behind.expectedCursor shouldBe 2L
            runCatching { (transport as AutoCloseable).close() }
        } finally {
            server.stop()
        }
    }
}
