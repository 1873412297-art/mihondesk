package mihon.desktop.extension

import okhttp3.ConnectionSpec

/**
 * TLS settings for source traffic. OkHttp's default MODERN_TLS spec narrows the JDK's cipher list,
 * and some sites (seen with bilimanga.net through a local system proxy) reset that ClientHello
 * with "Remote host terminated the handshake" while the JDK default ClientHello succeeds. Offer
 * every TLS version and cipher suite the runtime enables, like a plain JDK HTTPS connection does.
 */
object DesktopTls {
    val connectionSpecs: List<ConnectionSpec> = listOf(
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .allEnabledTlsVersions()
            .allEnabledCipherSuites()
            .build(),
        ConnectionSpec.CLEARTEXT,
    )
}
