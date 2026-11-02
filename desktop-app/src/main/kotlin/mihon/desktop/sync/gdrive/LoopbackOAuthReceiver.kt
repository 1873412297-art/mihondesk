package mihon.desktop.sync.gdrive

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

class OAuthCancelledException(message: String) : Exception(message)

/** One-shot loopback redirect receiver on 127.0.0.1 with an OS-assigned port (RFC 8252). */
class LoopbackOAuthReceiver(private val expectedState: String) : AutoCloseable {
    private val result = CompletableDeferred<String>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    val redirectUri: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            val params = exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
                val (k, v) = it.split('=', limit = 2)
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
            val ok = params["state"] == expectedState && !params["code"].isNullOrBlank()
            val html = if (ok) {
                "<h2>mihondesk: Google Drive 已连接</h2><p>可以关闭此页面并返回应用。You can close this tab.</p>"
            } else {
                "<h2>mihondesk: 授权未完成</h2><p>${params["error"] ?: "invalid response"}</p>"
            }
            val bytes = "<!doctype html><meta charset=utf-8>$html".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(if (ok) 200 else 400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            when {
                ok -> result.complete(params.getValue("code"))
                params.containsKey("error") || params.containsKey("state") ->
                    result.completeExceptionally(OAuthCancelledException(params["error"] ?: "state mismatch"))
            }
        }
        server.start()
    }

    suspend fun awaitCode(timeoutMillis: Long = 5 * 60_000L): String = withTimeout(timeoutMillis) { result.await() }

    override fun close() = server.stop(0)
}
