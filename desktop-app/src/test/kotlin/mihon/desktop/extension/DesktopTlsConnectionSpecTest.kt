package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLContext

/**
 * Regression for the bilimanga (哔哩漫畫) "TLS: Remote host terminated the handshake" failure:
 * OkHttp's default MODERN_TLS spec trims the JDK's ClientHello cipher list; through the user's
 * system proxy that ClientHello was reset while the JDK default ClientHello succeeded. Source
 * traffic must offer the full set of JDK-enabled TLS versions and cipher suites.
 */
class DesktopTlsConnectionSpecTest {

    private val jdkEnabledSuites = SSLContext.getDefault().createSSLEngine().enabledCipherSuites.toList()

    private fun List<ConnectionSpec>.tlsSpec() = first { it.isTls }

    @Test
    fun `brokered source client offers every JDK-enabled cipher suite`() {
        val helper = DesktopNetworkHelper(OkHttpClient())
        val spec = helper.connectionSpecs().tlsSpec()
        spec.cipherSuites shouldBe null
        spec.tlsVersions shouldBe null
        helper.connectionSpecs().any { it == ConnectionSpec.CLEARTEXT } shouldBe true
        helper.close()
    }

    @Test
    fun `desktop tls spec applied to a socket keeps the JDK cipher list`() {
        val engine = SSLContext.getDefault().createSSLEngine()
        val socketSuites = engine.enabledCipherSuites.toList()
        val modern = ConnectionSpec.MODERN_TLS.cipherSuites!!.map { it.javaName }
        // Sanity: MODERN_TLS really is narrower than the JDK default, which is what broke the handshake.
        (socketSuites.size > socketSuites.count { it in modern }) shouldBe true
        DesktopTls.connectionSpecs.tlsSpec().cipherSuites shouldBe null
        jdkEnabledSuites shouldContainAll socketSuites
    }
}
