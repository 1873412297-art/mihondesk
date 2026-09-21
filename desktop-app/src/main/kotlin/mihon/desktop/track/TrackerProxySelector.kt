package mihon.desktop.track

import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopProxyMode
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

internal class TrackerProxySelector(
    private val policyProvider: () -> DesktopNetworkPolicy,
    private val defaultSelector: ProxySelector? = ProxySelector.getDefault(),
) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val policy = policyProvider()
        return when (policy.proxyMode) {
            DesktopProxyMode.SYSTEM -> defaultSelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
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
