package mihon.desktop.extension

import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopProxyMode
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class DesktopPolicyProxySelector(
    private val policyProvider: () -> DesktopNetworkPolicy,
    private val defaultSelector: ProxySelector? = ProxySelector.getDefault(),
) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val policy = policyProvider()
        return when (policy.proxyMode) {
            // OkHttp tries proxies in order, so keep a direct route as the last resort when the
            // system proxy is unreachable or refuses a host.
            DesktopProxyMode.SYSTEM -> {
                val system = defaultSelector?.select(uri).orEmpty()
                if (system.isEmpty() || system.contains(Proxy.NO_PROXY)) {
                    system.ifEmpty { listOf(Proxy.NO_PROXY) }
                } else {
                    system + Proxy.NO_PROXY
                }
            }
            DesktopProxyMode.DIRECT -> listOf(Proxy.NO_PROXY)
            DesktopProxyMode.HTTP -> listOf(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress.createUnresolved(policy.proxyHost, policy.proxyPort),
                ),
            )
            DesktopProxyMode.SOCKS -> listOf(
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved(policy.proxyHost, policy.proxyPort),
                ),
            )
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        defaultSelector?.connectFailed(uri, sa, ioe)
    }
}
