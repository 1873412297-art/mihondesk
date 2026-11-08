package mihon.desktop.webview

import mihon.desktop.extension.DesktopDohProvider
import mihon.desktop.extension.DesktopNetworkPolicy
import mihon.desktop.extension.DesktopProxyMode

/**
 * Chromium switches that make the login window use the same network route as source requests.
 *
 * Without them the window follows the OS proxy while OkHttp follows the app's setting. Cloudflare
 * binds `cf_clearance` to the client IP, so a challenge solved over one route is rejected on the
 * other and the user sees "logged in, still blocked".
 *
 * The User-Agent is deliberately left alone: the helper reports Chromium's own UA back to the main
 * process, and the cookie is only valid together with that UA.
 */
internal object BrowserNetworkArguments {
    fun forPolicy(policy: DesktopNetworkPolicy): List<String> = buildList {
        when (policy.proxyMode) {
            // Chromium already reads the Windows proxy settings.
            DesktopProxyMode.SYSTEM -> Unit
            DesktopProxyMode.DIRECT -> add("--no-proxy-server")
            DesktopProxyMode.HTTP -> add("--proxy-server=http://${hostPort(policy)}")
            DesktopProxyMode.SOCKS -> {
                add("--proxy-server=socks5://${hostPort(policy)}")
                // Chromium resolves names locally for SOCKS unless every name maps to a placeholder,
                // which keeps lookups on the proxy the way OkHttp's unresolved addresses do.
                add("--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE ${policy.proxyHost.trim()}")
            }
        }
        // Mirror DesktopDns: DoH only where this machine resolves names itself.
        if (policy.dohProvider != DesktopDohProvider.OFF && policy.proxyMode != DesktopProxyMode.HTTP &&
            policy.proxyMode != DesktopProxyMode.SOCKS
        ) {
            add("--enable-features=DnsOverHttps")
            add("--force-fieldtrials=DoHTrial/Group1")
            add("--force-fieldtrial-params=DoHTrial.Group1:Fallback/true/Templates/${policy.dohProvider.url}")
        }
    }

    /** IPv6 literals need brackets in a proxy URL. */
    private fun hostPort(policy: DesktopNetworkPolicy): String {
        val host = policy.proxyHost.trim()
        val bracketed = if (':' in host && !host.startsWith('[')) "[$host]" else host
        return "$bracketed:${policy.proxyPort}"
    }
}
