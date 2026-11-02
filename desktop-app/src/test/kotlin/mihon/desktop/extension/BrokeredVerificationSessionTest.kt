package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpRequest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * EZmanga: Cloudflare binds cf_clearance to the browser's User-Agent. Extensions set their own
 * User-Agent (and sometimes Cookie) headers, which previously suppressed the WebView session.
 */
class BrokeredVerificationSessionTest {
    private fun exchange(withClearance: Boolean): Pair<String?, String?> {
        var ua: String? = null
        var cookie: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ua = ex.requestHeaders.getFirst("User-Agent")
            cookie = ex.requestHeaders.getFirst("Cookie")
            ex.sendResponseHeaders(200, 2)
            ex.responseBody.use { it.write("OK".toByteArray()) }
        }
        server.start()
        val file = Files.createTempFile("cookies", ".json")
        try {
            val store = DesktopCookieStore(file)
            val url = "http://127.0.0.1:${server.address.port}/api"
            if (withClearance) {
                store.saveFromResponse(
                    "ext.ez",
                    url.toHttpUrl(),
                    listOf(Cookie.parse(url.toHttpUrl(), "cf_clearance=v; Max-Age=3600")!!),
                )
            }
            val helper = DesktopNetworkHelper(cookieStore = store)
            helper.registerExtensionDomains("ext.ez", listOf("127.0.0.1"))
            runBlocking {
                helper.executeBrokeredRequest(
                    BrokerHttpRequest(
                        "GET",
                        url,
                        headers = mapOf("User-Agent" to "ExtUA/1", "Cookie" to "lang=en"),
                        extensionId = "ext.ez",
                    ),
                ).statusCode shouldBe 200
            }
            webUa = helper.userAgentFor(url)
            helper.close()
        } finally {
            server.stop(0)
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}.sessions.json"))
        }
        return ua to cookie
    }

    private var webUa = ""

    @Test
    fun `verified session cookie and web view user agent override extension headers`() {
        val (ua, cookie) = exchange(withClearance = true)
        ua shouldBe webUa
        cookie!! shouldContain "lang=en"
        cookie shouldContain "cf_clearance=v"
    }

    @Test
    fun `extension user agent is kept when there is no verified session`() {
        val (ua, cookie) = exchange(withClearance = false)
        ua shouldBe "ExtUA/1"
        cookie shouldBe "lang=en"
    }
}
