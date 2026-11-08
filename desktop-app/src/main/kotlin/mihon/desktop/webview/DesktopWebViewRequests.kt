package mihon.desktop.webview
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.logging.DesktopLogger
import mihon.extension.ipc.BrokerHttpRequest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

suspend fun DesktopWebViewManager.handleExtensionRequest(
    request: mihon.extension.ipc.WebViewRequest,
): mihon.extension.ipc.WebViewResponse {
    val key = "${request.extensionId}:${request.sourceId}:${request.sessionId}"
    return try {
        extensionSessions[key]?.touch()
        val value = when (request.action) {
            "open" -> {
                extensionSessions.remove(key)?.close()
                val session = open(request.sourceId, request.extensionId, request.url, request.headers)
                extensionSessions[key] = session
                try {
                    session.awaitLoaded()
                } catch (
                    failure: Throwable,
                ) {
                    extensionSessions.remove(key)?.close()
                    throw failure
                }
            }
            "evaluate" -> (extensionSessions[key] ?: error("Browser session is not open")).evaluate(request.script)
            "destroy" -> {
                extensionSessions.remove(key)?.close()
                ""
            }
            "cookies", "getCookie" -> cookieRequest(request)
            "setCookie" -> setCookieRequest(request)
            "userAgent" -> network.userAgentFor(request.url.ifBlank { "https://localhost/" })
            else -> error("Unsupported WebView action: ${request.action}")
        }
        mihon.extension.ipc.WebViewResponse(true, value)
    } catch (
        cancelled: CancellationException,
    ) {
        throw cancelled
    } catch (failure: Exception) {
        mihon.extension.ipc.WebViewResponse(
            false,
            error =
            failure.message ?: "Browser operation failed",
        )
    }
}

internal suspend fun DesktopWebViewManager.cookieRequest(request: mihon.extension.ipc.WebViewRequest): String {
    extensionSessions.entries.filter {
        it.key.startsWith("${request.extensionId}:${request.sourceId}:")
    }.forEach { it.value.exportCookies() }
    if (request.url.isBlank()) return ""
    return cookies.loadForRequest(request.extensionId, request.url.toHttpUrl())
        .joinToString("; ") { "${it.name}=${it.value}" }
}

internal suspend fun DesktopWebViewManager.setCookieRequest(request: mihon.extension.ipc.WebViewRequest): String {
    val url = request.url.toHttpUrl()
    check(network.isDomainAllowed(url.host, request.extensionId)) { "Cookie origin is not allowed" }
    val cookie = Cookie.parse(url, request.value) ?: error("Invalid browser cookie")
    cookies.saveFromResponse(request.extensionId, url, listOf(cookie))
    extensionSessions.entries.filter {
        it.key.startsWith("${request.extensionId}:${request.sourceId}:")
    }.forEach { it.value.setCookie(request.url, cookie) }
    return ""
}
