package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpRequest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DesktopNetworkHelperTest {

    private lateinit var server: HttpServer
    private lateinit var helper: DesktopNetworkHelper
    private var serverPort: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/test") { exchange ->
            val response = "Hello from mock server".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        serverPort = server.address.port
        helper = DesktopNetworkHelper()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `rejects request when domain is not in whitelist`() {
        runBlocking {
            val url = "http://127.0.0.1:" + serverPort + "/test"
            val req = BrokerHttpRequest(
                method = "GET",
                url = url,
            )

            val res = helper.executeBrokeredRequest(req)
            res.statusCode shouldBe 403
            res.error?.contains("Access denied") shouldBe true
        }
    }

    @Test
    fun `allows request when exact domain or wildcard is registered`() {
        runBlocking {
            helper.registerExtensionDomains("test.ext", listOf("127.0.0.1"))

            val url = "http://127.0.0.1:" + serverPort + "/test"
            val req = BrokerHttpRequest(
                method = "GET",
                url = url,
            )

            val res = helper.executeBrokeredRequest(req)
            res.statusCode shouldBe 200
            res.body shouldBe "Hello from mock server"

            // Unregister and ensure blocked
            helper.unregisterExtensionDomains("test.ext")
            val blockedRes = helper.executeBrokeredRequest(req)
            blockedRes.statusCode shouldBe 403
        }
    }

    @Test
    fun `wildcard domain matches subdomains`() {
        helper.registerExtensionDomains("test.wildcard", listOf("*.example.com"))
        helper.isDomainAllowed("example.com") shouldBe true
        helper.isDomainAllowed("api.example.com") shouldBe true
        helper.isDomainAllowed("v2.api.example.com") shouldBe true
        helper.isDomainAllowed("evil-example.com") shouldBe false
        helper.isDomainAllowed("notexample.com") shouldBe false
    }

    @Test
    fun `keeps manual session cookies off HTTP while applying custom user agent`() {
        var receivedCookie: String? = null
        var receivedUa: String? = null

        server.createContext("/header-check") { exchange ->
            receivedCookie = exchange.requestHeaders.getFirst("Cookie")
            receivedUa = exchange.requestHeaders.getFirst("User-Agent")
            val resp = "OK".toByteArray()
            exchange.sendResponseHeaders(200, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }

        val tempCookieFile = java.nio.file.Files.createTempFile("cookies-test", ".json")
        try {
            val cookieStore = DesktopCookieStore(tempCookieFile)
            cookieStore.setCookies(
                domain = "127.0.0.1",
                cookies = mapOf("cf_clearance" to "cf123", "token" to "tok456"),
                customUserAgent = "MihonBypassUA/2.0",
            )

            val helperWithCookies = DesktopNetworkHelper(cookieStore = cookieStore)
            helperWithCookies.registerExtensionDomains("local", listOf("127.0.0.1"))

            runBlocking {
                val res = helperWithCookies.executeBrokeredRequest(
                    BrokerHttpRequest(
                        method = "GET",
                        url = "http://127.0.0.1:$serverPort/header-check",
                    ),
                )
                res.statusCode shouldBe 200
                receivedCookie shouldBe null
                receivedUa shouldBe "MihonBypassUA/2.0"
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempCookieFile)
        }
    }

    @Test
    fun `allows any domain when universal wildcard pattern is registered`() {
        helper.registerExtensionDomains("wildcard.ext", listOf("*"))
        helper.isDomainAllowed("img.everia.club") shouldBe true
        helper.isDomainAllowed("static.example.org") shouldBe true
        helper.isDomainAllowed("127.0.0.1") shouldBe true
    }

    @Test
    fun `source web view follows a login redirect the extension itself cannot call`() {
        val owner = "eu.kanade.tachiyomi.extension.zh.hipmh"
        val signin = "https://m.hipmh.com/api/auth/signin"
        val login = "https://oidc.idxbase.com/oauth2/auth"
        val requests = mutableListOf<okhttp3.Request>()
        DesktopNetworkHelper(
            client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val redirect = if (request.url.toString() == signin) login else null
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(if (redirect == null) 200 else 302).message("fixture")
                    .apply { redirect?.let { header("Location", it) } }
                    .body("login form".toResponseBody()).build()
            }.build(),
        ).use { browsing ->
            browsing.registerExtensionDomains(owner, listOf("*.hipmh.com"))
            runBlocking {
                val page = browsing.executeBrokeredRequest(
                    BrokerHttpRequest("GET", signin, extensionId = owner),
                    browsing = true,
                )
                page.statusCode shouldBe 200
                page.finalUrl shouldBe login
                page.body shouldBe "login form"
                requests.map { it.url.toString() } shouldBe listOf(signin, login)
                browsing.executeBrokeredRequest(BrokerHttpRequest("GET", login, extensionId = owner))
                    .failureKind shouldBe mihon.extension.ipc.NetworkFailureKind.DOMAIN_DENIED
                browsing.isDomainAllowed("oidc.idxbase.com", owner) shouldBe false
            }
        }
    }

    @Test
    fun `replaces a bare Mozilla token and keeps a full browser user agent`() {
        val seen = mutableListOf<String?>()
        server.createContext("/ua") { exchange ->
            seen += exchange.requestHeaders.getFirst("User-Agent")
            val resp = "OK".toByteArray()
            exchange.sendResponseHeaders(200, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }
        helper.registerExtensionDomains("ua.ext", listOf("127.0.0.1"))
        val url = "http://127.0.0.1:$serverPort/ua"
        val full = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0"
        runBlocking {
            helper.executeBrokeredRequest(
                BrokerHttpRequest("GET", url, headers = mapOf("User-Agent" to "Mozilla/5.0"), extensionId = "ua.ext"),
            ).statusCode shouldBe 200
            helper.executeBrokeredRequest(
                BrokerHttpRequest("GET", url, headers = mapOf("User-Agent" to full), extensionId = "ua.ext"),
            ).statusCode shouldBe 200
        }
        seen shouldBe listOf(helper.userAgentFor(url), full)
    }
}
