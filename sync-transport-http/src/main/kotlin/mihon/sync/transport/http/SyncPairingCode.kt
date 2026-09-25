package mihon.sync.transport.http

data class SyncPairingCode(
    val host: String,
    val port: Int,
    val token: String,
) {
    fun toUriString(): String = "mihonsync://$host:$port#$token"
    fun toUri(): String = toUriString()

    fun toHttpBaseUrl(): String = "http://$host:$port"

    companion object {
        fun parseOrNull(raw: String): SyncPairingCode? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null

            val withoutScheme = when {
                trimmed.startsWith("mihonsync://", ignoreCase = true) -> trimmed.substring("mihonsync://".length)
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring("http://".length)
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring("https://".length)
                else -> trimmed
            }

            val hashIdx = withoutScheme.indexOf('#')
            val hostPortPart: String
            val tokenPart: String
            if (hashIdx >= 0) {
                hostPortPart = withoutScheme.substring(0, hashIdx)
                tokenPart = withoutScheme.substring(hashIdx + 1).trim()
            } else {
                hostPortPart = withoutScheme
                tokenPart = ""
            }

            val colonIdx = hostPortPart.lastIndexOf(':')
            val host: String
            val port: Int
            if (colonIdx >= 0) {
                host = hostPortPart.substring(0, colonIdx).trim()
                port = hostPortPart.substring(colonIdx + 1).trim().toIntOrNull() ?: 45831
            } else {
                host = hostPortPart.trim()
                port = 45831
            }

            if (host.isBlank() || tokenPart.isBlank()) return null
            return SyncPairingCode(host = host, port = port, token = tokenPart)
        }
    }
}
