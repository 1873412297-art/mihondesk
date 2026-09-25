package mihon.desktop.sync

import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import mihon.sync.server.SyncServiceAdvertiser
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path

class DesktopSyncServerManager(
    private val storageDir: Path,
) : AutoCloseable {

    private var server: SyncServer? = null
    private var store: SqliteChangesetStore? = null
    private var advertiser: SyncServiceAdvertiser? = null

    var lastError: String? = null
        private set

    val isRunning: Boolean
        @Synchronized get() = server?.isRunning == true

    @Synchronized
    fun start(
        port: Int,
        token: String,
        deviceName: String = "",
        quickPairEnabled: Boolean = false,
    ): Boolean {
        if (token.isBlank()) {
            lastError = "Token cannot be empty"
            return false
        }
        if (port !in 1024..65535) {
            lastError = "Port must be between 1024 and 65535"
            return false
        }

        if (server?.isRunning == true) {
            if (server?.port == port && server?.token == token) {
                if (advertiser?.isAdvertising != true) {
                    startAdvertisement(port, deviceName, quickPairEnabled, token)
                } else {
                    updateAdvertisement(port, deviceName, quickPairEnabled, token)
                }
                return true
            }
            stop()
        }

        return try {
            Files.createDirectories(storageDir)
            val dbFile = storageDir.resolve("changesets.db")
            val changesetStore = SqliteChangesetStore.open(dbFile)
            store = changesetStore

            val srv = SyncServer(
                host = "0.0.0.0",
                port = port,
                token = token,
                store = changesetStore,
            )
            srv.start(wait = false)
            server = srv
            lastError = null

            startAdvertisement(port, deviceName, quickPairEnabled, token)

            true
        } catch (e: Throwable) {
            stop()
            lastError = e.message ?: "Failed to start sync server"
            false
        }
    }

    /**
     * Starts (or restarts) the mDNS advertisement.  Should be called after [start] succeeds.
     *
     * Advertising failures are non-fatal and are captured in [advertiserLastError].
     *
     * @param port           HTTP server port (must match what was passed to [start]).
     * @param deviceName     Human-readable name shown in Android discovery UI.
     * @param quickPairEnabled  When true the token is included in TXT for one-tap pairing.
     * @param token          Auth token (used only when [quickPairEnabled] is true).
     */
    @Synchronized
    fun startAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {
        try {
            val bindAddr = getLanBindAddress()
            if (advertiser == null) {
                advertiser = SyncServiceAdvertiser(bindAddr)
            }
            val effectiveToken = if (quickPairEnabled) token else null
            advertiser?.start(port = port, deviceName = effectiveName(deviceName), token = effectiveToken)
        } catch (e: Throwable) {
            advertiserLastError = e.message ?: "Failed to start mDNS advertisement"
        }
    }

    /**
     * Updates the running advertisement in place (e.g. when quick-pair toggle or device name changes).
     * No-op if not advertising.
     */
    @Synchronized
    fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {
        try {
            advertiser?.updateAdvertisement(
                port = port,
                deviceName = effectiveName(deviceName),
                quickPairEnabled = quickPairEnabled,
                token = token,
            )
        } catch (e: Throwable) {
            advertiserLastError = e.message ?: "Failed to update mDNS advertisement"
        }
    }

    @Volatile var advertiserLastError: String? = null
        private set

    @Synchronized
    fun stopAdvertisement() {
        try {
            advertiser?.close()
        } catch (_: Throwable) {}
        advertiser = null
    }

    @Synchronized
    fun stop() {
        stopAdvertisement()

        try {
            server?.stop()
        } catch (_: Throwable) {}
        server = null

        try {
            store?.close()
        } catch (_: Throwable) {}
        store = null
    }

    override fun close() {
        stop()
    }

    companion object {
        fun getLocalIpAddresses(): List<String> {
            val result = mutableListOf<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return listOf("127.0.0.1")
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            result.add(addr.hostAddress)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore network resolution errors
            }
            // Prefer RFC1918 site-local addresses (real LAN) over virtual/TUN/CGNAT adapters,
            // then sort alphabetically so the ordering is stable across launches.
            return result.distinct().sortedWith(
                compareBy<String> { addr ->
                    try {
                        !Inet4Address.getByName(addr).isSiteLocalAddress
                    } catch (_: Throwable) {
                        true
                    }
                }.thenBy { it },
            ).ifEmpty { listOf("127.0.0.1") }
        }

        /** Returns the first site-local IPv4 address suitable for JmDNS binding. */
        internal fun getLanBindAddress(): InetAddress {
            val preferred = getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
            return try {
                InetAddress.getByName(preferred)
            } catch (_: Throwable) {
                InetAddress.getLoopbackAddress()
            }
        }

        const val MAX_NAME_LENGTH = 63

        /** Falls back to OS hostname when the user has not set a custom device name. Truncates to [MAX_NAME_LENGTH] chars. */
        internal fun effectiveName(userDeviceName: String): String {
            val name = userDeviceName.ifBlank {
                try {
                    java.net.InetAddress.getLocalHost().hostName
                } catch (_: Throwable) {
                    "Mihon Desktop"
                }
            }
            return name.take(MAX_NAME_LENGTH)
        }

        fun effectiveSelectedIp(remembered: String, localIps: List<String>): String {
            return if (remembered.isNotBlank() && remembered in localIps) {
                remembered
            } else {
                localIps.firstOrNull() ?: "127.0.0.1"
            }
        }
    }
}
