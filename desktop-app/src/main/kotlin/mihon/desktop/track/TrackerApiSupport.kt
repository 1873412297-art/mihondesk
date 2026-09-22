package mihon.desktop.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopPolicyProxySelector
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val DEFAULT_TRACKER_TIMEOUT_MILLIS = 15_000L

/** Sentinel used by tracker constructors: keep the timeout configured on the injected client. */
internal const val INHERIT_CLIENT_TIMEOUT_MILLIS = 0L

internal val TRACKER_JSON_MEDIA_TYPE = "application/json".toMediaType()
internal fun jsonRequestBody(json: String) = json.toByteArray(Charsets.UTF_8).toRequestBody(TRACKER_JSON_MEDIA_TYPE)

internal val defaultTrackerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal const val DEFAULT_TRACKER_USER_AGENT = "MihonW/0.1 (Windows)"

internal fun defaultTrackerHttpClient(
    policyProvider: () -> DesktopNetworkPolicy = { DesktopNetworkPolicy() },
): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(DEFAULT_TRACKER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    // HTTP/1.1 only: HTTP/2 streams through local forwarding proxies (Clash/mihomo class)
    // are terminated mid-request ("unexpected end of stream"); trackers gain nothing from h2.
    .protocols(listOf(Protocol.HTTP_1_1))
    .proxySelector(DesktopPolicyProxySelector(policyProvider))
    .addInterceptor { chain ->
        val request = chain.request()
        if (request.header("User-Agent") == null) {
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", DEFAULT_TRACKER_USER_AGENT)
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }
    .build()

/** Raised for any tracker API/transport failure that is not a plain HTTP status error. */
open class TrackerApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Raised when a tracker API returns a non-successful HTTP status. */
class TrackerHttpException(
    val code: Int,
    val responseBody: String? = null,
) : TrackerApiException(
    buildString {
        append("Tracker API request failed with HTTP ").append(code)
        val body = responseBody?.takeIf { it.isNotBlank() }
        if (body != null) {
            append(": ").append(body.take(500))
        }
    },
)

/** Raised for trackers that are listed in the UI but intentionally not implemented. */
class TrackerNotSupportedException(trackerName: String) :
    UnsupportedOperationException("$trackerName tracking is not supported on Mihon Desktop")

/**
 * Small OkHttp wrapper shared by the real tracker implementations.
 *
 * The client, base URL and timeout are injectable so tests can point trackers at a local
 * [com.sun.net.httpserver.HttpServer] while production keeps sane defaults.
 */
internal class TrackerHttpClient(
    baseUrl: String,
    client: OkHttpClient,
    requestTimeoutMillis: Long = 0L,
) {
    val baseUrl: String = baseUrl.trimEnd('/')

    private val client: OkHttpClient = if (requestTimeoutMillis > 0L) {
        client.newBuilder()
            .callTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    } else {
        // Respect the timeout configured on the injected client (and the 15s default client).
        client
    }

    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw TrackerHttpException(response.code, body)
                }
                body
            }
        } catch (error: TrackerApiException) {
            throw error
        } catch (error: IOException) {
            throw TrackerApiException("Tracker request to ${request.url} failed", error)
        }
    }
}
