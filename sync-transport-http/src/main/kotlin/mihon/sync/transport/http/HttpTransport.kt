package mihon.sync.transport.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncTransport
import java.io.Closeable
import java.util.Base64

class HttpTransport(
    baseUrl: String,
    val token: String,
    private val client: HttpClient = HttpClient(CIO),
    private val ownsClient: Boolean = false,
) : SyncTransport, Closeable {

    val baseUrl: String = baseUrl.trim().removeSuffix("/")

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun push(changeset: Changeset) {
        val payload = ProtoBuf.encodeToByteArray(Changeset.serializer(), changeset)
        try {
            val response = client.post("$baseUrl/v1/changesets") {
                header("Authorization", "Bearer $token")
                setBody(payload)
            }
            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> return
                HttpStatusCode.Unauthorized -> throw SyncPairingException("Invalid token or pairing expired (401)")
                HttpStatusCode.BadRequest -> throw SyncProtocolException(
                    "Bad request when pushing changeset (400): ${response.bodyAsText()}",
                )
                else -> throw SyncProtocolException(
                    "Unexpected server response (${response.status.value}): ${response.bodyAsText()}",
                )
            }
        } catch (e: SyncHttpException) {
            throw e
        } catch (e: Throwable) {
            throw SyncNetworkException("Network error while pushing changeset to $baseUrl", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun pull(
        sinceCursors: Map<String, Long>,
        excludeDeviceId: String?,
    ): List<Changeset> {
        try {
            val response = client.get("$baseUrl/v1/changesets") {
                header("Authorization", "Bearer $token")
                if (!excludeDeviceId.isNullOrBlank()) {
                    parameter("exclude", excludeDeviceId)
                }
                if (sinceCursors.isNotEmpty()) {
                    val sinceParam = sinceCursors.entries.joinToString(",") { "${it.key}:${it.value}" }
                    parameter("since", sinceParam)
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    if (body.isBlank()) return emptyList()

                    val decoder = Base64.getDecoder()
                    return body.lines()
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val bytes = decoder.decode(line.trim())
                            ProtoBuf.decodeFromByteArray(Changeset.serializer(), bytes)
                        }
                }
                HttpStatusCode.Unauthorized -> throw SyncPairingException("Invalid token or pairing expired (401)")
                else -> throw SyncProtocolException(
                    "Unexpected server response (${response.status.value}): ${response.bodyAsText()}",
                )
            }
        } catch (e: SyncHttpException) {
            throw e
        } catch (e: Throwable) {
            throw SyncNetworkException("Network error while pulling changesets from $baseUrl", e)
        }
    }

    override suspend fun headCursor(excludeDeviceId: String?): Long {
        try {
            val response = client.get("$baseUrl/v1/head") {
                header("Authorization", "Bearer $token")
                if (!excludeDeviceId.isNullOrBlank()) {
                    parameter("exclude", excludeDeviceId)
                }
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val text = response.bodyAsText().trim()
                    return text.toLongOrNull() ?: 0L
                }
                HttpStatusCode.Unauthorized -> throw SyncPairingException("Invalid token or pairing expired (401)")
                else -> throw SyncProtocolException(
                    "Unexpected server response (${response.status.value}): ${response.bodyAsText()}",
                )
            }
        } catch (e: SyncHttpException) {
            throw e
        } catch (e: Throwable) {
            throw SyncNetworkException("Network error while querying head cursor from $baseUrl", e)
        }
    }

    suspend fun checkHealth(): Boolean {
        return try {
            val response = client.get("$baseUrl/v1/health")
            response.status == HttpStatusCode.OK
        } catch (_: Throwable) {
            false
        }
    }

    override fun close() {
        if (ownsClient) {
            client.close()
        }
    }
}
