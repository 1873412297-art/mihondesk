package mihon.sync.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.server.SyncServer.Companion.configureServerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

@OptIn(ExperimentalSerializationApi::class)
class SyncServerTest {

    private val testToken = "test-secret-token-12345"

    private fun sampleChangeset(deviceId: String, cursor: Long): Changeset {
        return Changeset(
            deviceId = deviceId,
            baseSchema = 1,
            cursor = cursor,
            producedAt = 1000L + cursor,
            upserts = mihon.sync.core.model.EntityDelta(),
            tombstones = emptyList(),
        )
    }

    @Test
    fun `health endpoint returns 200 ok without token`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        val response = client.get("/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `push requires valid authorization token`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        val cs = sampleChangeset("devA", 1)
        val bytes = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs)

        // No token
        val r1 = client.post("/v1/changesets") { setBody(bytes) }
        assertEquals(HttpStatusCode.Unauthorized, r1.status)

        // Wrong token
        val r2 = client.post("/v1/changesets") {
            header("Authorization", "Bearer wrong-token")
            setBody(bytes)
        }
        assertEquals(HttpStatusCode.Unauthorized, r2.status)

        // Correct token
        val r3 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $testToken")
            setBody(bytes)
        }
        assertEquals(HttpStatusCode.NoContent, r3.status)
        assertEquals("1", r3.headers["X-Cursor"])
    }

    @Test
    fun `push is idempotent when repeated`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        val cs1 = sampleChangeset("devA", 1)
        val bytes1 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs1)

        val r1 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $testToken")
            setBody(bytes1)
        }
        assertEquals(HttpStatusCode.NoContent, r1.status)

        // Repeat push same (devA, 1)
        val r2 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $testToken")
            setBody(bytes1)
        }
        assertEquals(HttpStatusCode.NoContent, r2.status)

        // Only one record in store
        val stored = store.getChangesets(emptyMap(), "")
        assertEquals(1, stored.size)
    }

    @Test
    fun `push rejects malformed body`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        val r1 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $testToken")
            setBody(byteArrayOf(1, 2, 3, 4, 5))
        }
        assertEquals(HttpStatusCode.BadRequest, r1.status)

        val r2 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $testToken")
            setBody(ByteArray(0))
        }
        assertEquals(HttpStatusCode.BadRequest, r2.status)
    }

    @Test
    fun `pull filters by excludeDeviceId and sinceCursors`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        // Push devA:1, devA:2, devB:1
        val csA1 = sampleChangeset("devA", 1)
        val csA2 = sampleChangeset("devA", 2)
        val csB1 = sampleChangeset("devB", 1)

        for (cs in listOf(csA1, csA2, csB1)) {
            val bytes = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs)
            client.post("/v1/changesets") {
                header("Authorization", "Bearer $testToken")
                setBody(bytes)
            }
        }

        // Pull excluding devA -> only devB:1
        val r1 = client.get("/v1/changesets?exclude=devA") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, r1.status)
        val lines1 = r1.bodyAsText().lines().filter { it.isNotBlank() }
        assertEquals(1, lines1.size)
        val decoded1 = ProtoBuf.decodeFromByteArray(Changeset.serializer(), Base64.getDecoder().decode(lines1[0]))
        assertEquals("devB", decoded1.deviceId)
        assertEquals(1L, decoded1.cursor)

        // Pull excluding devB, with since=devA:1 -> only devA:2
        val r2 = client.get("/v1/changesets?exclude=devB&since=devA:1") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        val lines2 = r2.bodyAsText().lines().filter { it.isNotBlank() }
        assertEquals(1, lines2.size)
        val decoded2 = ProtoBuf.decodeFromByteArray(Changeset.serializer(), Base64.getDecoder().decode(lines2[0]))
        assertEquals("devA", decoded2.deviceId)
        assertEquals(2L, decoded2.cursor)
    }

    @Test
    fun `head returns max cursor excluding local device`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(token = testToken, store = store, retentionDays = 30, maxChangesets = 5000)
        }

        val csA1 = sampleChangeset("devA", 5)
        val csB1 = sampleChangeset("devB", 3)

        for (cs in listOf(csA1, csB1)) {
            val bytes = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs)
            client.post("/v1/changesets") {
                header("Authorization", "Bearer $testToken")
                setBody(bytes)
            }
        }

        // Head excluding devA -> max is devB's 3
        val r1 = client.get("/v1/head?exclude=devA") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, r1.status)
        assertEquals("3", r1.bodyAsText())

        // Head excluding devB -> max is devA's 5
        val r2 = client.get("/v1/head?exclude=devB") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        assertEquals("5", r2.bodyAsText())
    }

    @Test
    fun `cleanup deletes expired changesets and trims excess count`() {
        val store = SqliteChangesetStore.inMemory()

        val now = System.currentTimeMillis()
        val oldTime = now - 35L * 86_400_000L // 35 days ago

        // Insert 1 expired record
        store.insertOrReplace(
            ChangesetRecord("devA", 1, 100, byteArrayOf(1), oldTime),
        )
        // Insert 5 fresh records
        for (i in 2..6) {
            store.insertOrReplace(
                ChangesetRecord("devA", i.toLong(), 100L + i, byteArrayOf(i.toByte()), now + i),
            )
        }

        assertEquals(6, store.getChangesets(emptyMap(), "").size)

        // Cleanup with retentionDays = 30 -> deletes oldTime (1 record)
        val deletedTime = store.cleanup(retentionDays = 30, maxChangesets = 100)
        assertEquals(1, deletedTime)
        assertEquals(5, store.getChangesets(emptyMap(), "").size)

        // Cleanup with maxChangesets = 3 -> deletes 2 oldest among remaining 5
        val deletedCount = store.cleanup(retentionDays = 30, maxChangesets = 3)
        assertEquals(2, deletedCount)
        val remaining = store.getChangesets(emptyMap(), "")
        assertEquals(3, remaining.size)
        assertEquals(listOf(4L, 5L, 6L), remaining.map { it.cursor })
    }
}
