package mihon.desktop.track

import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopProxyMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class TrackerProxySelectorTest {

    @Test
    fun `SYSTEM delegates to a supplied default selector`() {
        val targetUri = URI("https://api.myanimelist.net/v2")
        val upstreamProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("upstream.proxy", 8888))
        var selectCalled = false
        var connectFailedCalled = false

        val mockDefaultSelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                if (uri == targetUri) {
                    selectCalled = true
                }
                return listOf(upstreamProxy)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                connectFailedCalled = true
            }
        }

        val selector = TrackerProxySelector(
            policyProvider = { DesktopNetworkPolicy(proxyMode = DesktopProxyMode.SYSTEM) },
            defaultSelector = mockDefaultSelector,
        )

        val proxies = selector.select(targetUri)
        assertTrue(selectCalled)
        assertEquals(listOf(upstreamProxy), proxies)

        selector.connectFailed(targetUri, upstreamProxy.address(), IOException("connect failed"))
        assertTrue(connectFailedCalled)
    }

    @Test
    fun `SYSTEM falls back to NO_PROXY when default selector is null`() {
        val selector = TrackerProxySelector(
            policyProvider = { DesktopNetworkPolicy(proxyMode = DesktopProxyMode.SYSTEM) },
            defaultSelector = null,
        )
        val proxies = selector.select(URI("https://api.anilist.co"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `DIRECT returns NO_PROXY`() {
        val selector = TrackerProxySelector(
            policyProvider = { DesktopNetworkPolicy(proxyMode = DesktopProxyMode.DIRECT) },
        )
        val proxies = selector.select(URI("https://api.bangumi.tv"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `HTTP and SOCKS produce the right proxy type and unresolved address`() {
        val httpPolicy = DesktopNetworkPolicy(
            proxyMode = DesktopProxyMode.HTTP,
            proxyHost = "127.0.0.1",
            proxyPort = 7890,
        )
        val httpSelector = TrackerProxySelector(policyProvider = { httpPolicy })
        val httpProxies = httpSelector.select(URI("https://api.myanimelist.net"))

        assertEquals(1, httpProxies.size)
        val httpProxy = httpProxies.first()
        assertEquals(Proxy.Type.HTTP, httpProxy.type())
        val httpAddress = httpProxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", httpAddress.hostString)
        assertEquals(7890, httpAddress.port)
        assertTrue(httpAddress.isUnresolved)

        val socksPolicy = DesktopNetworkPolicy(
            proxyMode = DesktopProxyMode.SOCKS,
            proxyHost = "proxy.example.com",
            proxyPort = 1080,
        )
        val socksSelector = TrackerProxySelector(policyProvider = { socksPolicy })
        val socksProxies = socksSelector.select(URI("https://api.anilist.co"))

        assertEquals(1, socksProxies.size)
        val socksProxy = socksProxies.first()
        assertEquals(Proxy.Type.SOCKS, socksProxy.type())
        val socksAddress = socksProxy.address() as InetSocketAddress
        assertEquals("proxy.example.com", socksAddress.hostString)
        assertEquals(1080, socksAddress.port)
        assertTrue(socksAddress.isUnresolved)
    }

    @Test
    fun `changing the policy between two select calls on the SAME selector instance yields different proxies`() {
        var currentPolicy = DesktopNetworkPolicy(proxyMode = DesktopProxyMode.DIRECT)
        val selector = TrackerProxySelector(policyProvider = { currentPolicy })

        val firstProxies = selector.select(URI("https://api.bangumi.tv"))
        assertEquals(listOf(Proxy.NO_PROXY), firstProxies)

        currentPolicy = DesktopNetworkPolicy(
            proxyMode = DesktopProxyMode.HTTP,
            proxyHost = "127.0.0.1",
            proxyPort = 9090,
        )

        val secondProxies = selector.select(URI("https://api.bangumi.tv"))
        assertEquals(1, secondProxies.size)
        val secondProxy = secondProxies.first()
        assertEquals(Proxy.Type.HTTP, secondProxy.type())
        val secondAddress = secondProxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", secondAddress.hostString)
        assertEquals(9090, secondAddress.port)
        assertTrue(secondAddress.isUnresolved)
    }
}
