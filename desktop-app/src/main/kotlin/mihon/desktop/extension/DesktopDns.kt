package mihon.desktop.extension

import mihon.extension.network.withTlsFallback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS for a [DesktopNetworkPolicy].
 *
 * DoH only applies to direct connections. With an HTTP or SOCKS proxy OkHttp hands the proxy an
 * unresolved host name, so the proxy resolves it and no lookup leaves this machine; resolving here
 * as well would leak the host name around the proxy. In SYSTEM mode the OS proxy may or may not be
 * used for a given host, so DoH is still installed and is simply unused for proxied routes.
 */
internal object DesktopDns {
    fun forPolicy(policy: DesktopNetworkPolicy, bootstrapClient: OkHttpClient): Dns {
        val provider = policy.dohProvider
        if (provider == DesktopDohProvider.OFF) return Dns.SYSTEM
        if (policy.proxyMode == DesktopProxyMode.HTTP || policy.proxyMode == DesktopProxyMode.SOCKS) return Dns.SYSTEM
        return DnsOverHttps.Builder()
            // The resolver must not route through itself or through a proxy the policy left unused.
            .client(bootstrapClient.newBuilder().dns(Dns.SYSTEM).build())
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrap.map(InetAddress::getByName))
            .includeIPv6(true)
            .build()
            // A DoH outage must not take every source offline with it.
            .withSystemFallback()
    }

    /**
     * For clients built once (covers, trackers): follows the current policy on every lookup, so a
     * DoH change in settings applies without rebuilding the client.
     */
    fun following(policyProvider: () -> DesktopNetworkPolicy): Dns {
        // The resolver connection gets the same TLS fallback as every other desktop client.
        val bootstrap = OkHttpClient.Builder().withTlsFallback()
        var applied: Pair<DesktopNetworkPolicy, Dns>? = null
        return Dns { hostname ->
            val policy = policyProvider()
            val dns = synchronized(this) {
                applied?.takeIf { it.first == policy }?.second
                    ?: forPolicy(policy, bootstrap).also { applied = policy to it }
            }
            dns.lookup(hostname)
        }
    }

    private fun Dns.withSystemFallback(): Dns = Dns { hostname ->
        try {
            lookup(hostname)
        } catch (error: java.net.UnknownHostException) {
            mihon.desktop.logging.DesktopLogger.warn(
                "DesktopDns",
                "DoH lookup failed for $hostname; using system DNS",
                error,
            )
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
