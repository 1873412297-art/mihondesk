package mihon.desktop.extension

import mihon.desktop.preferences.DesktopPreferenceStore

enum class DesktopProxyMode { SYSTEM, DIRECT, HTTP, SOCKS }

/** DNS-over-HTTPS resolvers; the bootstrap addresses let the resolver be reached without plain DNS. */
enum class DesktopDohProvider(val url: String, val bootstrap: List<String>) {
    OFF("", emptyList()),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("https://dns-unfiltered.adguard.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
}

data class DesktopNetworkPolicy(
    val proxyMode: DesktopProxyMode = DesktopProxyMode.SYSTEM,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 30,
    val userAgent: String = "",
    val dohProvider: DesktopDohProvider = DesktopDohProvider.OFF,
) {
    fun validate(): DesktopNetworkPolicy = apply {
        require(connectTimeoutSeconds in 1..120 && readTimeoutSeconds in 1..300) { "Invalid network timeout" }
        if (proxyMode == DesktopProxyMode.HTTP || proxyMode == DesktopProxyMode.SOCKS) {
            require(
                proxyHost.isNotBlank() && proxyHost.none {
                    it.isWhitespace() || it in "/@"
                },
            ) { "Enter a proxy hostname or IP address" }
            require(proxyPort in 1..65535) { "Proxy port must be between 1 and 65535" }
        }
        require(userAgent.none { it == '\r' || it == '\n' }) { "User-Agent must be one line" }
    }
}

class DesktopNetworkSettingsStore(private val preferences: DesktopPreferenceStore) {
    fun load(): DesktopNetworkPolicy {
        val fields = preferences.propertiesWithPrefix("network.")
        return DesktopNetworkPolicy(
            proxyMode =
            fields["network.proxy_mode"]?.let { name -> DesktopProxyMode.entries.firstOrNull { it.name == name } }
                ?: DesktopProxyMode.SYSTEM,
            proxyHost = fields["network.proxy_host"].orEmpty(),
            proxyPort = fields["network.proxy_port"]?.toIntOrNull() ?: 8080,
            connectTimeoutSeconds = fields["network.connect_timeout_seconds"]?.toIntOrNull() ?: 15,
            readTimeoutSeconds = fields["network.read_timeout_seconds"]?.toIntOrNull() ?: 30,
            userAgent = fields["network.user_agent"].orEmpty(),
            dohProvider = fields["network.doh_provider"]
                ?.let { name -> DesktopDohProvider.entries.firstOrNull { it.name == name } }
                ?: DesktopDohProvider.OFF,
        )
    }

    fun save(policy: DesktopNetworkPolicy) {
        policy.validate()
        preferences.update {
            setProperty("network.proxy_mode", policy.proxyMode.name)
            setProperty("network.proxy_host", policy.proxyHost.trim())
            setProperty("network.proxy_port", policy.proxyPort.toString())
            setProperty("network.connect_timeout_seconds", policy.connectTimeoutSeconds.toString())
            setProperty("network.read_timeout_seconds", policy.readTimeoutSeconds.toString())
            setProperty("network.user_agent", policy.userAgent.trim())
            setProperty("network.doh_provider", policy.dohProvider.name)
        }
    }
}
