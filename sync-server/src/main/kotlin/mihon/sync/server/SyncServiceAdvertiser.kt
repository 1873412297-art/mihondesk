package mihon.sync.server

import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Advertises the Mihon sync server via mDNS (JmDNS) so Android NSD can discover it.
 *
 * Service type: `_mihonsync._tcp.local.`
 * TXT record keys:
 *  - `v`     – protocol version (always "1")
 *  - `name`  – display name (truncated to 63 chars)
 *  - `token` – auth token; **only present when [quickPairEnabled] is true**
 *
 * Advertising failures are non-fatal: the caller (DesktopSyncServerManager) must catch
 * and log them without interrupting HTTP server operation.
 */
class SyncServiceAdvertiser(
    /** LAN IP address to bind JmDNS to. Prefer a site-local IPv4 address. */
    private val bindAddress: InetAddress,
) : AutoCloseable {

    @Volatile private var jmdns: JmDNS? = null

    @Volatile private var currentInfo: ServiceInfo? = null

    val isAdvertising: Boolean
        get() = currentInfo != null

    /**
     * Starts advertising. Idempotent – calling again while already running is a no-op unless
     * you want to change parameters; use [updateAdvertisement] for live updates.
     *
     * @param port  TCP port the HTTP server is listening on.
     * @param deviceName  Human-readable display name (shown in discovery UI on the phone).
     * @param token       Auth token; pass `null` or empty to withhold token from TXT (default, safe).
     */
    @Synchronized
    fun start(port: Int, deviceName: String, token: String?) {
        if (jmdns == null) {
            jmdns = JmDNS.create(bindAddress)
        }
        val info = buildServiceInfo(port = port, deviceName = deviceName, token = token)
        // Unregister any previous registration before registering the new one.
        currentInfo?.let { jmdns?.unregisterService(it) }
        jmdns?.registerService(info)
        currentInfo = info
    }

    /**
     * Updates the TXT record of an already-running advertisement in place.
     * If the advertiser is not running, this is a no-op.
     *
     * @param deviceName     New display name (truncated to 63 chars).
     * @param quickPairEnabled  When true the token is written into TXT; when false it is omitted.
     * @param token          Auth token (used only when [quickPairEnabled] is true).
     * @param port           Port must match the currently-running HTTP server.
     */
    @Synchronized
    fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {
        val dns = jmdns ?: return
        val effectiveToken = if (quickPairEnabled) token else null
        val info = buildServiceInfo(port = port, deviceName = deviceName, token = effectiveToken)
        currentInfo?.let { dns.unregisterService(it) }
        dns.registerService(info)
        currentInfo = info
    }

    @Synchronized
    override fun close() {
        try {
            currentInfo?.let { jmdns?.unregisterService(it) }
        } catch (_: Throwable) {}
        currentInfo = null
        try {
            jmdns?.close()
        } catch (_: Throwable) {}
        jmdns = null
    }

    companion object {
        const val SERVICE_TYPE = "_mihonsync._tcp.local."
        private const val MAX_NAME_LENGTH = 63

        /**
         * Builds the TXT property map.  This is a pure function so it can be unit-tested
         * independently of the JmDNS lifecycle.
         *
         * @param deviceName    Display name; truncated to [MAX_NAME_LENGTH] chars.
         * @param token         When non-null and non-blank it is written to the `token` key.
         * @return Mutable map ready for [ServiceInfo.create].
         */
        fun buildTxtProperties(deviceName: String, token: String?): Map<String, String> {
            val properties = mutableMapOf<String, String>()
            properties["v"] = "1"
            properties["name"] = deviceName.take(MAX_NAME_LENGTH)
            if (!token.isNullOrBlank()) {
                properties["token"] = token
            }
            return properties
        }

        private fun buildServiceInfo(port: Int, deviceName: String, token: String?): ServiceInfo {
            val properties = buildTxtProperties(deviceName = deviceName, token = token)
            return ServiceInfo.create(
                SERVICE_TYPE,
                deviceName.take(MAX_NAME_LENGTH),
                port,
                0,
                0,
                properties,
            )
        }
    }
}
