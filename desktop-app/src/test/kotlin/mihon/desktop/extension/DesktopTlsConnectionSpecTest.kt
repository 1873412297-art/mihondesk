package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketException
import javax.net.ssl.SSLHandshakeException

/**
 * bilimanga (哔哩漫畫): through the user's system proxy OkHttp's MODERN_TLS ClientHello was reset
 * ("Remote host terminated the handshake") while a JDK-default ClientHello succeeded. Design:
 * MODERN_TLS stays the default; only a host whose handshake is reset is retried, and remembered,
 * with the compatible (all JDK-enabled versions and suites) spec.
 */
class DesktopTlsConnectionSpecTest {

    @Test
    fun `brokered source client defaults to MODERN_TLS`() {
        val helper = DesktopNetworkHelper(OkHttpClient())
        helper.connectionSpecs() shouldBe listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
        helper.close()
    }

    @Test
    fun `compatible spec offers every JDK-enabled version and suite`() {
        val spec = DesktopTls.compatibleSpecs.first { it.isTls }
        spec.cipherSuites shouldBe null
        spec.tlsVersions shouldBe null
    }

    @Test
    fun `handshake resets are recognised, other failures are not`() {
        DesktopTls.isHandshakeReset(SSLHandshakeException("Remote host terminated the handshake")) shouldBe true
        DesktopTls.isHandshakeReset(SocketException("Connection reset")) shouldBe true
        DesktopTls.isHandshakeReset(IOException("timeout")) shouldBe false
        DesktopTls.isHandshakeReset(SSLHandshakeException("PKIX path building failed")) shouldBe false
    }

    @Test
    fun `reset host is retried with the compatible client and remembered`() {
        val fallbackHosts = DesktopTls.FallbackHosts()
        val compatibleCalls = mutableListOf<String>()
        var defaultCalls = 0
        val interceptor = DesktopTls.FallbackInterceptor(fallbackHosts) { request ->
            compatibleCalls += request.url.host
            ok(request)
        }
        val reset: (Request) -> Response = {
            defaultCalls++
            throw SSLHandshakeException("Remote host terminated the handshake")
        }
        val request = Request.Builder().url("https://www.bilimanga.net/").build()
        interceptor.proceed(request, reset).code shouldBe 200
        fallbackHosts.contains("www.bilimanga.net") shouldBe true
        interceptor.proceed(request, reset).code shouldBe 200
        defaultCalls shouldBe 1
        compatibleCalls shouldBe listOf("www.bilimanga.net", "www.bilimanga.net")
        interceptor.proceed(Request.Builder().url("https://ok.example/").build(), ::ok).code shouldBe 200
        fallbackHosts.contains("ok.example") shouldBe false
    }

    @Test
    fun `cleartext requests are never retried`() {
        val interceptor = DesktopTls.FallbackInterceptor(DesktopTls.FallbackHosts()) { error("no fallback") }
        val request = Request.Builder().url("http://plain.example/").build()
        runCatching { interceptor.proceed(request) { throw SocketException("Connection reset") } }
            .exceptionOrNull()?.javaClass shouldBe SocketException::class.java
    }

    private fun ok(request: Request) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(200).message("OK").body("".toResponseBody()).build()
}
