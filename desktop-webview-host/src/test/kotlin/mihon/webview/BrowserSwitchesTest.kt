package mihon.webview

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test

class BrowserSwitchesTest {
    private fun accepted(vararg args: String) = BrowserSwitches.accepted(
        buildJsonObject { putJsonArray("browserArgs") { args.forEach { add(it) } } },
    )

    @Test
    fun `network route switches pass through`() {
        accepted(
            "--proxy-server=socks5://proxy.lan:1080",
            "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE proxy.lan",
            "--no-proxy-server",
            "--enable-features=DnsOverHttps",
        ) shouldContainExactly listOf(
            "--proxy-server=socks5://proxy.lan:1080",
            "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE proxy.lan",
            "--no-proxy-server",
            "--enable-features=DnsOverHttps",
        )
    }

    @Test
    fun `switches that weaken the browser are dropped`() {
        accepted(
            "--no-sandbox",
            "--remote-debugging-port=9222",
            "--load-extension=C:/evil",
            "--disable-web-security",
            "--enable-features=DnsOverHttps,SomethingElse",
            "--no-proxy-server-and-more",
            "--proxy-server=http://a:1\n--no-sandbox",
        ) shouldContainExactly emptyList()
    }

    @Test
    fun `a configuration without switches yields none`() {
        BrowserSwitches.accepted(buildJsonObject { }) shouldContainExactly emptyList()
    }
}
