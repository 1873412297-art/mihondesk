package mihon.extension.network

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

/**
 * TLS policy for app HTTP clients. MODERN_TLS stays the default. Some sites (bilimanga.net through the
 * user's local proxy) reset OkHttp's MODERN_TLS ClientHello ("Remote host terminated the handshake")
 * yet accept the JDK-default ClientHello. Such a host is retried once with [compatibleSpecs] (all
 * JDK-enabled versions and suites) and remembered for the rest of the process.
 */
object TlsFallback {
    val defaultSpecs: List<ConnectionSpec> = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
    val compatibleSpecs: List<ConnectionSpec> = listOf(
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).allEnabledTlsVersions().allEnabledCipherSuites().build(),
        ConnectionSpec.CLEARTEXT,
    )

    /** Hosts that needed the compatible ClientHello; shared by every client in the process. */
    class FallbackHosts {
        private val hosts = ConcurrentHashMap.newKeySet<String>()
        fun contains(host: String) = host.lowercase() in hosts
        fun add(host: String) = hosts.add(host.lowercase())
    }

    val sharedFallbackHosts = FallbackHosts()

    fun isHandshakeReset(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty().lowercase()
            if (current is SSLException &&
                ("terminated the handshake" in message || "connection reset" in message || "unexpected end" in message)
            ) {
                return true
            }
            if (current is SocketException && "connection reset" in message) return true
            current = current.cause
        }
        return false
    }

    class FallbackInterceptor(
        private val hosts: FallbackHosts,
        private val compatible: (Request) -> Response,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = proceed(chain.request(), chain::proceed)

        fun proceed(request: Request, default: (Request) -> Response): Response {
            if (!request.url.isHttps) return default(request)
            if (hosts.contains(request.url.host)) return compatible(request)
            return try {
                default(request)
            } catch (error: IOException) {
                if (!isHandshakeReset(error)) throw error
                hosts.add(request.url.host)
                compatible(request)
            }
        }
    }
}

/** MODERN_TLS by default, compatible ClientHello for hosts that reset it. Call on the final builder. */
fun OkHttpClient.Builder.withTlsFallback(
    hosts: TlsFallback.FallbackHosts = TlsFallback.sharedFallbackHosts,
): OkHttpClient {
    val base = connectionSpecs(TlsFallback.defaultSpecs).build()
    val compatible = base.newBuilder().connectionSpecs(TlsFallback.compatibleSpecs).build()
    return base.newBuilder()
        .addInterceptor(TlsFallback.FallbackInterceptor(hosts) { compatible.newCall(it).execute() })
        .build()
}
