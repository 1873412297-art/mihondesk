package mihon.desktop.webview

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.DesktopDohProvider
import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopProxyMode
import org.junit.jupiter.api.Test

class BrowserNetworkArgumentsTest {
    private fun args(policy: DesktopNetworkPolicy) = BrowserNetworkArguments.forPolicy(policy)

    @Test
    fun `system proxy mode adds nothing because chromium already follows windows`() {
        args(DesktopNetworkPolicy()) shouldBe emptyList()
    }

    @Test
    fun `direct mode turns the proxy off`() {
        args(DesktopNetworkPolicy(proxyMode = DesktopProxyMode.DIRECT)) shouldContainExactly listOf("--no-proxy-server")
    }

    @Test
    fun `http proxy is passed through`() {
        val policy = DesktopNetworkPolicy(proxyMode = DesktopProxyMode.HTTP, proxyHost = "127.0.0.1", proxyPort = 7890)
        args(policy) shouldContainExactly listOf("--proxy-server=http://127.0.0.1:7890")
    }

    @Test
    fun `socks proxy resolves names on the proxy and leaves the proxy host itself resolvable`() {
        val policy = DesktopNetworkPolicy(proxyMode = DesktopProxyMode.SOCKS, proxyHost = "proxy.lan", proxyPort = 1080)
        args(policy) shouldContainExactly listOf(
            "--proxy-server=socks5://proxy.lan:1080",
            "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE proxy.lan",
        )
    }

    @Test
    fun `ipv6 proxy literals are bracketed`() {
        val policy = DesktopNetworkPolicy(proxyMode = DesktopProxyMode.HTTP, proxyHost = "::1", proxyPort = 8080)
        args(policy) shouldContain "--proxy-server=http://[::1]:8080"
    }

    @Test
    fun `doh is enabled only for routes this machine resolves`() {
        val direct =
            DesktopNetworkPolicy(proxyMode = DesktopProxyMode.DIRECT, dohProvider = DesktopDohProvider.CLOUDFLARE)
        args(direct) shouldContain "--enable-features=DnsOverHttps"
        args(direct).any { it.contains("https://cloudflare-dns.com/dns-query") } shouldBe true

        val proxied = direct.copy(proxyMode = DesktopProxyMode.SOCKS, proxyHost = "proxy.lan", proxyPort = 1080)
        args(proxied) shouldNotContain "--enable-features=DnsOverHttps"
    }
}
