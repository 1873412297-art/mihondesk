package mihon.sync.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import java.util.Base64

class SyncServer(
    val host: String = "0.0.0.0",
    val port: Int = 45831,
    val token: String,
    val store: ChangesetStore,
    val retentionDays: Int = 30,
    val maxChangesets: Int = 5000,
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
        ) {
            routing {
                syncServerRoutes(
                    token = token,
                    store = store,
                    retentionDays = retentionDays,
                    maxChangesets = maxChangesets,
                )
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun Route.syncServerRoutes(
            token: String,
            store: ChangesetStore,
            retentionDays: Int = 30,
            maxChangesets: Int = 5000,
        ) {
            get("/v1/health") {
                call.respondText("ok")
            }

            post("/v1/changesets") {
                val auth = call.request.header("Authorization")
                if (auth == null || !auth.startsWith("Bearer ") || auth.substring("Bearer ".length).trim() != token) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val payload = try {
                    call.receive<ByteArray>()
                } catch (_: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, "Failed to read request body")
                    return@post
                }

                if (payload.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Empty request body")
                    return@post
                }

                val changeset = try {
                    ProtoBuf.decodeFromByteArray(Changeset.serializer(), payload)
                } catch (e: Throwable) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid changeset protobuf: ${e.message}")
                    return@post
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
                val auth = call.request.header("Authorization")
                if (auth == null || !auth.startsWith("Bearer ") || auth.substring("Bearer ".length).trim() != token) {
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
                val auth = call.request.header("Authorization")
                if (auth == null || !auth.startsWith("Bearer ") || auth.substring("Bearer ".length).trim() != token) {
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
