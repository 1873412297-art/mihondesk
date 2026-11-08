package mihon.sync.transport.gdrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Drive REST v3 store for one file in the hidden appDataFolder (scope drive.appdata only). */
class DriveRestSnapshotStore(
    private val client: OkHttpClient,
    private val accessToken: suspend () -> String,
    private val baseUrl: String = "https://www.googleapis.com",
    private val fileName: String = FILE_NAME,
) : DriveSnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedId: String? = null

    override suspend fun read(): ByteArray? {
        val id = findFileId() ?: return null
        val url = "$baseUrl/drive/v3/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        return call(Request.Builder().url(url).get()) { it.body.bytes() }
    }

    override suspend fun write(data: ByteArray) {
        val octet = "application/octet-stream".toMediaType()
        val id = findFileId()
        if (id == null) {
            val meta = """{"name":"$fileName","parents":["appDataFolder"]}"""
            val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(data.toRequestBody(octet))
                .build()
            val url = "$baseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart").addQueryParameter("fields", "id").build()
            cachedId = call(Request.Builder().url(url).post(body)) { idOf(it.body.string()) }
        } else {
            val url = "$baseUrl/upload/drive/v3/files/$id".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "media").build()
            call(Request.Builder().url(url).patch(data.toRequestBody(octet))) { }
        }
    }

    /** Each device owns one document, so concurrent pushes can never overwrite another device's. */
    override fun deviceStore(deviceId: String): DriveSnapshotStore = DriveRestSnapshotStore(
        client = client,
        accessToken = accessToken,
        baseUrl = baseUrl,
        fileName = DEVICE_FILE_PREFIX + deviceId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".pb",
    )

    override suspend fun readAll(): List<ByteArray> {
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name contains '$DEVICE_FILE_PREFIX' and trashed = false")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "1000")
            .build()
        val ids = call(Request.Builder().url(url).get()) { resp ->
            json.parseToJsonElement(resp.body.string()).jsonObject["files"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                .orEmpty()
        }
        return ids.mapNotNull { id ->
            runCatching {
                val media = "$baseUrl/drive/v3/files/$id".toHttpUrl().newBuilder()
                    .addQueryParameter("alt", "media").build()
                call(Request.Builder().url(media).get()) { it.body.bytes() }
            }.onFailure { error ->
                // A document that disappears between listing and download must not fail the pull.
                System.err.println("Skipping unreadable Drive sync document $id: ${error.message}")
            }.getOrNull()
        }
    }

    private suspend fun findFileId(): String? {
        cachedId?.let { return it }
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$fileName' and trashed = false")
            .addQueryParameter("fields", "files(id,modifiedTime)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .build()
        val id = call(Request.Builder().url(url).get()) { resp ->
            json.parseToJsonElement(resp.body.string()).jsonObject["files"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        }
        cachedId = id
        return id
    }

    private fun idOf(body: String) = json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content

    private suspend fun <T> call(builder: Request.Builder, block: (Response) -> T): T {
        val token = accessToken()
        return withContext(Dispatchers.IO) {
            client.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { resp ->
                when {
                    resp.code == 401 -> throw DriveAuthException("Drive rejected the access token (401)")
                    resp.code == 404 -> {
                        cachedId = null
                        throw DriveException("Drive file not found (404)")
                    }
                    resp.code == 403 -> throw DriveException(
                        "Drive denied access (403): ${resp.body.string().take(200)}",
                    )
                    !resp.isSuccessful -> throw DriveException("Drive HTTP ${resp.code}")
                    else -> block(resp)
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "mihon-sync-snapshot.pb"

        /** Prefix of the per-device documents written by newer clients; the legacy file still matches. */
        const val DEVICE_FILE_PREFIX = "mihon-sync-"
    }
}
