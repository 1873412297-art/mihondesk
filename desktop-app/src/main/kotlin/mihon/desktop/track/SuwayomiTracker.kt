@file:Suppress("ktlint:standard:max-line-length")

package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

class SuwayomiTracker(
    id: Long = 9L,
    httpClient: OkHttpClient = defaultTrackerHttpClient(),
    private val requestTimeoutMillis: Long = INHERIT_CLIENT_TIMEOUT_MILLIS,
) : BaseDesktopTracker(id, "Suwayomi", TrackerAuthType.SERVER) {
    private val client = httpClient
    private var baseUrl: String? = null
    private var secret: String = ""
    override val persistenceToken get() = secret
    override val supportedStatuses = listOf(TrackStatus.PLAN_TO_READ, TrackStatus.READING, TrackStatus.COMPLETED)
    override val supportsScore = false

    override suspend fun login(credentials: Map<String, String>): Boolean {
        val url = (credentials["server_url"] ?: credentials["url"]).orEmpty().trim().trimEnd('/')
        if (url.isBlank()) return false
        val user = credentials["username"]?.trim().orEmpty()
        val credential = credentials["password"] ?: credentials["token"].orEmpty()
        return try {
            baseUrl = url
            secret = credential
            graphQl("query MihonDesktopProbe { __typename }", buildJsonObject { }, user, credential)
            setLoggedIn(true, user.takeIf(String::isNotBlank), credential, url)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            baseUrl = null
            secret = ""
            setLoggedIn(false)
            false
        }
    }

    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.serverUrl.isBlank()) return
        baseUrl = info.serverUrl.trimEnd('/')
        secret = info.token
        setLoggedIn(true, info.username.takeIf(String::isNotBlank), info.token, baseUrl)
    }

    override suspend fun search(query: String): List<TrackSearchResult> {
        val numeric = query.trim().substringAfterLast('/').toLongOrNull()
        if (numeric != null) return listOfNotNull(getManga(numeric)?.toSearch())
        val data = graphQl(SEARCH_QUERY, buildJsonObject { put("query", query.trim()) })
        return data["mangas"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.toSearch() }
    }

    override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = getManga(
        track.remoteId,
    )?.let { manga ->
        track.copy(
            title = manga.stringField("title") ?: track.title,
            totalChapters = manga.objField("chapters")?.longField("totalCount") ?: track.totalChapters,
            lastChapterRead = manga.objField("latestReadChapter")?.doubleField("chapterNumber") ?: 0.0,
            status = when (manga.longField("unreadCount")) {
                0L -> TrackStatus.COMPLETED.value
                manga.objField("chapters")?.longField("totalCount") -> TrackStatus.PLAN_TO_READ.value
                else -> TrackStatus.READING.value
            },
            trackingUrl = "${requireBase()}/manga/${track.remoteId}",
        )
    }

    override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord {
        val chapters = graphQl(UNREAD_QUERY, buildJsonObject { put("mangaId", track.remoteId) })
            .objField("chapters")?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { (it.doubleField("chapterNumber") ?: Double.MAX_VALUE) <= track.lastChapterRead + 0.001 }
            .mapNotNull { it.longField("id") }
        graphQl(
            MARK_QUERY,
            buildJsonObject {
                put(
                    "chapters",
                    buildJsonArray {
                        chapters.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            },
        )
        graphQl(TRACK_QUERY, buildJsonObject { put("mangaId", track.remoteId) })
        return findRemote(track) ?: track
    }

    private suspend fun getManga(id: Long): JsonObject? = graphQl(
        GET_QUERY,
        buildJsonObject {
            put("mangaId", id)
        },
    ).objField("manga")
    private suspend fun graphQl(
        query: String,
        variables: JsonObject,
        user: String = username.orEmpty(),
        credential: String = secret,
    ): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val builder = Request.Builder().url(
            requireBase() + "/api/graphql",
        ).post(jsonRequestBody(payload.toString()))
        if (user.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(user, credential))
        } else if (credential.isNotBlank()) {
            builder.header("Authorization", "Bearer $credential")
        }
        val text = TrackerHttpClient(requireBase(), client, requestTimeoutMillis).execute(builder.build())
        val root = defaultTrackerJson.parseToJsonElement(text).jsonObject
        if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) throw TrackerApiException("Suwayomi API error")
        return root.objField("data") ?: throw TrackerApiException("Suwayomi response did not contain data")
    }
    private fun requireBase() = baseUrl ?: throw TrackerApiException("Not logged in to Suwayomi")
    private fun JsonObject.toSearch(): TrackSearchResult? {
        val mangaId = longField("id") ?: return null
        val title = stringField("title") ?: return null
        return TrackSearchResult(
            id,
            mangaId,
            title,
            objField("chapters")?.longField("totalCount") ?: 0L,
            "${requireBase()}/${stringField("thumbnailUrl").orEmpty().trimStart('/')}",
            "${requireBase()}/manga/$mangaId",
            stringField("description").orEmpty(),
        )
    }
    private companion object {
        private const val FIELDS = "id title description thumbnailUrl unreadCount chapters { totalCount } latestReadChapter { chapterNumber }"
        private const val GET_QUERY = "query GetManga(\$mangaId: Int!) { manga(id: \$mangaId) { $FIELDS } }"
        private const val SEARCH_QUERY = "query Search(\$query: String!) { mangas(condition: {title: {includesInsensitive: \$query}}) { nodes { $FIELDS } } }"
        private const val UNREAD_QUERY = "query Unread(\$mangaId: Int!) { chapters(condition: {mangaId: \$mangaId, isRead: false}) { nodes { id chapterNumber } } }"
        private const val MARK_QUERY = "mutation Mark(\$chapters: [Int!]!) { updateChapters(input: {ids: \$chapters, patch: {isRead: true}}) { __typename } }"
        private const val TRACK_QUERY = "mutation Track(\$mangaId: Int!) { trackProgress(input: {mangaId: \$mangaId}) { __typename } }"
    }
}
