package mihon.desktop.sync

import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import mihon.sync.server.SyncServiceAdvertiser
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Owns the in-app sync HTTP server and its mDNS advertisement.
 *
 * Startup cost note: the HTTP server itself binds in ~0.3 s, but JmDNS creation + service
 * registration (multicast probing/announcing) takes ~9 s on Windows and enumerating network
 * interfaces takes ~1.5 s. Those steps only matter for LAN discovery, so they run on a dedicated
 * background thread and never block [start]/[stop] callers.
 */
class DesktopSyncServerManager(
    private val storageDir: Path,
    private val lanAddressProvider: () -> InetAddress = { getLanBindAddress() },
    private val advertiserFactory: (InetAddress) -> Advertiser = { JmdnsAdvertiser(SyncServiceAdvertiser(it)) },
) : AutoCloseable {

    /** Abstraction over the mDNS advertiser so startup behaviour can be tested without the network. */
    interface Advertiser : AutoCloseable {
        val isAdvertising: Boolean
        fun start(port: Int, deviceName: String, token: String?)
        fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?)
    }

    private class JmdnsAdvertiser(private val delegate: SyncServiceAdvertiser) : Advertiser {
        override val isAdvertising: Boolean get() = delegate.isAdvertising
        override fun start(port: Int, deviceName: String, token: String?) = delegate.start(port, deviceName, token)
        override fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) =
            delegate.updateAdvertisement(port, deviceName, quickPairEnabled, token)
        override fun close() = delegate.close()
    }

    private var server: SyncServer? = null
    private var store: SqliteChangesetStore? = null

    // Only touched from [advertiserExecutor].
    @Volatile private var advertiser: Advertiser? = null

    @Volatile private var advertisementRequested = false

    private val advertiserExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mihon-sync-mdns").apply { isDaemon = true }
    }

    private fun runAdvertiserTask(task: () -> Unit) {
        try {
            advertiserExecutor.execute(task)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Manager closed; nothing to advertise.
        }
    }

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
                if (!advertisementRequested) {
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

            mihon.desktop.logging.DesktopLogger.info("DesktopSyncServerManager", "Starting sync server on port $port")
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
            mihon.desktop.logging.DesktopLogger.info("DesktopSyncServerManager", "Sync server running on port $port")

            true
        } catch (e: Throwable) {
            mihon.desktop.logging.DesktopLogger.error(
                "DesktopSyncServerManager",
                "Failed to start sync server on port $port",
                e,
            )
            stop()
            lastError = e.message ?: "Failed to start sync server"
            false
        }
    }

    /**
     * Starts (or restarts) the mDNS advertisement in the background. Should be called after [start] succeeds.
     * Advertising failures are non-fatal and are captured in [advertiserLastError].
     */
    fun startAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {
        advertisementRequested = true
        runAdvertiserTask {
            try {
                val begin = System.nanoTime()
                val current = advertiser ?: advertiserFactory(lanAddressProvider()).also { advertiser = it }
                val effectiveToken = if (quickPairEnabled) token else null
                current.start(port = port, deviceName = effectiveName(deviceName), token = effectiveToken)
                advertiserLastError = null
                mihon.desktop.logging.DesktopLogger.info(
                    "DesktopSyncServerManager",
                    "mDNS advertisement ready after ${(System.nanoTime() - begin) / 1_000_000} ms",
                )
            } catch (e: Throwable) {
                advertiserLastError = e.message ?: "Failed to start mDNS advertisement"
            }
        }
    }

    /** Updates the running advertisement (e.g. quick-pair toggle or device name change). No-op if not advertising. */
    fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {
        runAdvertiserTask {
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
    }

    @Volatile var advertiserLastError: String? = null
        private set

    fun stopAdvertisement() {
        advertisementRequested = false
        runAdvertiserTask {
            try {
                advertiser?.close()
            } catch (_: Throwable) {}
            advertiser = null
        }
    }

    /** Waits until queued advertisement work has finished (tests / shutdown). */
    fun awaitAdvertisementIdle(timeoutMillis: Long): Boolean {
        if (advertiserExecutor.isShutdown) {
            return advertiserExecutor.awaitTermination(
                timeoutMillis,
                TimeUnit.MILLISECONDS,
            )
        }
        return try {
            advertiserExecutor.submit {}.get(timeoutMillis, TimeUnit.MILLISECONDS)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun stop() {
        mihon.desktop.logging.DesktopLogger.info("DesktopSyncServerManager", "Stopping sync server")
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
        // Give the mDNS goodbye a brief chance on shutdown without holding the app hostage.
        advertiserExecutor.shutdown()
        advertiserExecutor.awaitTermination(1, TimeUnit.SECONDS)
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
