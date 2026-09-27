package mihon.sync.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class SyncServer(
    val host: String = "0.0.0.0",
    val port: Int = 45831,
    val token: String,
    val store: ChangesetStore,
    val retentionDays: Int = 30,
    val maxChangesets: Int = 5000,
    val maxRequestBodyBytes: Long = 16L * 1024 * 1024,
    val maxChangesetPayloadBytes: Long = 16L * 1024 * 1024,
    val additionalTokens: Set<String> = emptySet(),
) {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    val isRunning: Boolean
        @Synchronized get() = engine != null

    @Synchronized
    fun start(wait: Boolean = false) {
        if (engine != null) return
        val server = embeddedServer(CIO, port = port, host = host) {
            configureServerApplication(
                token = token,
                store = store,
                retentionDays = retentionDays,
                maxChangesets = maxChangesets,
                maxRequestBodyBytes = maxRequestBodyBytes,
                maxChangesetPayloadBytes = maxChangesetPayloadBytes,
                additionalTokens = additionalTokens,
            )
        }
        server.start(wait = wait)
        engine = server
    }

    @Synchronized
    fun stop(gracePeriodMillis: Long = 500, timeoutMillis: Long = 1000) {
        engine?.stop(gracePeriodMillis, timeoutMillis)
        engine = null
    }

    companion object {
        fun Application.configureServerApplication(
            token: String,
            store: ChangesetStore,
            retentionDays: Int = 30,
            maxChangesets: Int = 5000,
            maxRequestBodyBytes: Long = 16L * 1024 * 1024,
            maxChangesetPayloadBytes: Long = 16L * 1024 * 1024,
            additionalTokens: Set<String> = emptySet(),
        ) {
            routing {
                syncServerRoutes(
                    token = token,
                    store = store,
                    retentionDays = retentionDays,
                    maxChangesets = maxChangesets,
                    maxRequestBodyBytes = maxRequestBodyBytes,
                    maxChangesetPayloadBytes = maxChangesetPayloadBytes,
                    additionalTokens = additionalTokens,
                )
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun Route.syncServerRoutes(
            token: String,
            store: ChangesetStore,
            retentionDays: Int = 30,
            maxChangesets: Int = 5000,
            maxRequestBodyBytes: Long = 16L * 1024 * 1024,
            maxChangesetPayloadBytes: Long = 16L * 1024 * 1024,
            additionalTokens: Set<String> = emptySet(),
        ) {
            val allTokens = (setOf(token) + additionalTokens).filter { it.isNotBlank() }.toSet()

            fun isAuthorized(authHeader: String?): String? {
                if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
                val clientToken = authHeader.substring("Bearer ".length).trim()
                val clientBytes = clientToken.toByteArray(StandardCharsets.UTF_8)
                var matched: String? = null
                for (expected in allTokens) {
                    val expectedBytes = expected.toByteArray(StandardCharsets.UTF_8)
                    if (MessageDigest.isEqual(clientBytes, expectedBytes)) {
                        matched = expected
                    }
                }
                return matched
            }

            fun tokenHash(rawToken: String): String {
                val md = MessageDigest.getInstance("SHA-256")
                return md.digest(rawToken.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
            }

            get("/v1/health") {
                call.respondText("ok")
            }

            post("/v1/changesets") {
                val matchedToken = isAuthorized(call.request.header(HttpHeaders.Authorization))
                if (matchedToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                if (contentLength != null && contentLength > maxRequestBodyBytes) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        "Request body exceeds maximum size of $maxRequestBodyBytes bytes",
                    )
                    return@post
                }

                val payload = try {
                    val channel = call.request.receiveChannel()
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        total += read
                        if (total > maxRequestBodyBytes) {
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                "Request body exceeds maximum size of $maxRequestBodyBytes bytes",
                            )
                            return@post
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, "Failed to read request body")
                    return@post
                }

                if (payload.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Empty request body")
                    return@post
                }

                if (payload.size > maxChangesetPayloadBytes) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        "Changeset payload exceeds limit of $maxChangesetPayloadBytes bytes",
                    )
                    return@post
                }

                val changeset = try {
                    ProtoBuf.decodeFromByteArray(Changeset.serializer(), payload)
                } catch (e: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid changeset protobuf: ${e.message}")
                    return@post
                }

                if (changeset.deviceId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Changeset deviceId cannot be blank")
                    return@post
                }

                // 0.2 Device ownership check
                val expectedOwnerHash = tokenHash(matchedToken)
                val boundOwnerHash = store.getDeviceOwner(changeset.deviceId)
                if (boundOwnerHash == null) {
                    store.bindDeviceOwner(changeset.deviceId, expectedOwnerHash)
                } else if (boundOwnerHash != expectedOwnerHash) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        "Device ${changeset.deviceId} is bound to another paired identity",
                    )
                    return@post
                }

                // 0.2 Monotonic cursor check
                val lastCursor = store.getLastCursor(changeset.deviceId)
                if (lastCursor != null && lastCursor > 0) {
                    if (changeset.cursor == lastCursor) {
                        val existing = store.getChangeset(changeset.deviceId, changeset.cursor)
                        if (existing != null && existing.payload.contentEquals(payload)) {
                            call.response.header("X-Cursor", changeset.cursor.toString())
                            call.respond(HttpStatusCode.NoContent)
                            return@post
                        } else {
                            call.respond(
                                HttpStatusCode.Conflict,
                                "Cursor ${changeset.cursor} already exists with different payload",
                            )
                            return@post
                        }
                    } else if (changeset.cursor < lastCursor) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            "Cursor ${changeset.cursor} is not monotonically increasing (current head: $lastCursor)",
                        )
                        return@post
                    } else if (changeset.cursor > lastCursor + 1) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            "Cursor gap detected: expected ${lastCursor + 1}, got ${changeset.cursor}",
                        )
                        return@post
                    }
                } else {
                    if (changeset.cursor <= 0) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Initial cursor must be positive, got ${changeset.cursor}",
                        )
                        return@post
                    }
                }

                val now = System.currentTimeMillis()
                store.insertOrReplace(
                    ChangesetRecord(
                        deviceId = changeset.deviceId,
                        cursor = changeset.cursor,
                        producedAt = changeset.producedAt,
                        payload = payload,
                        receivedAt = now,
                    ),
                )

                try {
                    store.cleanup(retentionDays = retentionDays, maxChangesets = maxChangesets)
                } catch (_: Throwable) {
                    // Ignore background cleanup failure
                }

                call.response.header("X-Cursor", changeset.cursor.toString())
                call.respond(HttpStatusCode.NoContent)
            }

            get("/v1/changesets") {
                if (isAuthorized(call.request.header(HttpHeaders.Authorization)) == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@get
                }

                val exclude = call.request.queryParameters["exclude"].orEmpty()
                val sinceParam = call.request.queryParameters["since"].orEmpty()
                val sinceMap = if (sinceParam.isNotBlank()) {
                    sinceParam.split(",").mapNotNull { part ->
                        val pieces = part.split(":", limit = 2)
                        if (pieces.size == 2) {
                            val peer = pieces[0].trim()
                            val cursor = pieces[1].trim().toLongOrNull()
                            if (peer.isNotBlank() && cursor != null) peer to cursor else null
                        } else {
                            null
                        }
                    }.toMap()
                } else {
                    emptyMap()
                }

                val records = store.getChangesets(sinceCursors = sinceMap, excludeDeviceId = exclude)
                if (records.isEmpty()) {
                    call.respondText("", status = HttpStatusCode.OK)
                    return@get
                }

                val encoder = Base64.getEncoder()
                val responseBody = buildString {
                    for (record in records) {
                        append(encoder.encodeToString(record.payload))
                        append("\n")
                    }
                }
                call.respondText(responseBody, status = HttpStatusCode.OK)
            }

            get("/v1/head") {
                if (isAuthorized(call.request.header(HttpHeaders.Authorization)) == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@get
                }

                val exclude = call.request.queryParameters["exclude"].orEmpty()
                val head = store.getHeadCursor(excludeDeviceId = exclude)
                call.respondText(head.toString(), status = HttpStatusCode.OK)
            }
        }
    }
}
