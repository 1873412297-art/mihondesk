package mihon.sync.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.server.SyncServer.Companion.configureServerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class SyncServerAbuseTest {

    @Test
    fun `rate limit is enforced per paired identity with retry header`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        application {
            configureServerApplication(
                token = "token-a",
                additionalTokens = setOf("token-b"),
                store = store,
                maxRequestsPerMinute = 2,
            )
        }

        repeat(2) {
            assertEquals(
                HttpStatusCode.OK,
                client.get("/v1/head") {
                    header("Authorization", "Bearer token-a")
                }.status,
            )
        }
        val limited = client.get("/v1/head") { header("Authorization", "Bearer token-a") }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        val retryAfter = limited.headers["Retry-After"]?.toLongOrNull()
        assertEquals(true, retryAfter != null && retryAfter in 1L..60L)
        assertEquals(
            HttpStatusCode.OK,
            client.get("/v1/head") {
                header("Authorization", "Bearer token-b")
            }.status,
        )
    }

    private fun sampleChangeset(
        deviceId: String,
        cursor: Long,
        producedAt: Long = 1000L + cursor,
    ): Changeset {
        return Changeset(
            deviceId = deviceId,
            baseSchema = 1,
            cursor = cursor,
            producedAt = producedAt,
            upserts = EntityDelta(),
            tombstones = emptyList(),
        )
    }

    @Test
    fun `spoofing deviceId with different token is rejected with 409 Conflict`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        val tokenA = "token-device-a"
        val tokenB = "token-device-b"

        application {
            configureServerApplication(
                token = tokenA,
                additionalTokens = setOf(tokenB),
                store = store,
            )
        }

        val cs1 = sampleChangeset("devA", 1)
        val bytes1 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs1)

        // Device A writes using tokenA
        val r1 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $tokenA")
            setBody(bytes1)
        }
        assertEquals(HttpStatusCode.NoContent, r1.status)

        // Attacker holding valid tokenB attempts to push for devA
        val cs2 = sampleChangeset("devA", 2)
        val bytes2 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs2)
        val r2 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $tokenB")
            setBody(bytes2)
        }
        assertEquals(HttpStatusCode.Conflict, r2.status)

        // Device A with tokenA can push cursor 2 normally
        val r3 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $tokenA")
            setBody(bytes2)
        }
        assertEquals(HttpStatusCode.NoContent, r3.status)
    }

    @Test
    fun `oversized payload is rejected with 413 PayloadTooLarge`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        val token = "token-123"
        val maxBytes = 256L

        application {
            configureServerApplication(
                token = token,
                store = store,
                maxRequestBodyBytes = maxBytes,
                maxChangesetPayloadBytes = maxBytes,
            )
        }

        // Body exceeding maxBytes
        val oversized = ByteArray(512) { 0x42 }
        val response = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(oversized)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)

        // Store directly rejects oversized payloads
        assertThrows(IllegalArgumentException::class.java) {
            store.insertOrReplace(
                ChangesetRecord(
                    deviceId = "devA",
                    cursor = 1,
                    producedAt = 1000L,
                    payload = ByteArray(SqliteChangesetStore.MAX_PAYLOAD_BYTES + 1),
                    receivedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    @Test
    fun `non-monotonic cursor is rejected with 409 Conflict`() = testApplication {
        val store = SqliteChangesetStore.inMemory()
        val token = "token-monotony"

        application {
            configureServerApplication(
                token = token,
                store = store,
            )
        }

        // First valid push: cursor 2
        val cs2 = sampleChangeset("devA", 2)
        val bytes2 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs2)
        val r1 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes2)
        }
        assertEquals(HttpStatusCode.NoContent, r1.status)

        // Regressing cursor: cursor 1 < head 2 -> 409 Conflict
        val cs1 = sampleChangeset("devA", 1)
        val bytes1 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs1)
        val r2 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes1)
        }
        assertEquals(HttpStatusCode.Conflict, r2.status)

        // Duplicate cursor with different content: cursor 2, different producedAt -> 409 Conflict
        val cs2Different = sampleChangeset("devA", 2, producedAt = 9999L)
        val bytes2Diff = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs2Different)
        val r3 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes2Diff)
        }
        assertEquals(HttpStatusCode.Conflict, r3.status)

        // Cursor gap: cursor 4 when expected 3 -> 409 Conflict
        val cs4 = sampleChangeset("devA", 4)
        val bytes4 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs4)
        val r4 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes4)
        }
        assertEquals(HttpStatusCode.Conflict, r4.status)

        // Consecutive monotonic cursor: cursor 3 -> 204 NoContent
        val cs3 = sampleChangeset("devA", 3)
        val bytes3 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs3)
        val r5 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes3)
        }
        assertEquals(HttpStatusCode.NoContent, r5.status)

        // Non-positive cursor -> 400 Bad Request
        val cs0 = sampleChangeset("devB", 0)
        val bytes0 = ProtoBuf.encodeToByteArray(Changeset.serializer(), cs0)
        val r6 = client.post("/v1/changesets") {
            header("Authorization", "Bearer $token")
            setBody(bytes0)
        }
        assertEquals(HttpStatusCode.BadRequest, r6.status)
    }
}
