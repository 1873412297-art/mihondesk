package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A discovered sync server instance on the local network.
 *
 * @param serviceName  Raw NSD service name – the stable identity used for de-duplication and
 *                     removal when the service is lost (the display name may not be unique).
 * @param name   Display name from the mDNS TXT `name` key.
 * @param host   Resolved IP or hostname.
 * @param port   TCP port the sync HTTP server is listening on.
 * @param token  Auth token from TXT `token` key; null when quick-pair is off or parser returns no TXT.
 */
data class DiscoveredSyncService(
    val serviceName: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String?,
    val version: Int,
)

/**
 * Pure helper – builds a [DiscoveredSyncService] from an NSD [NsdServiceInfo] attribute map.
 *
 * Extracted as a top-level function for unit-testability (no Android context needed).
 *
 * @param name    Raw service name (used as fallback display name).
 * @param host    Resolved host address string.
 * @param port    Service port.
 * @param attrs   TXT attribute map; may be empty on older APIs.
 * @return A [DiscoveredSyncService], or null if the version key is present but ≠ 1.
 */
fun buildDiscoveredSyncService(
    name: String,
    host: String,
    port: Int,
    attrs: Map<String, String?>,
): DiscoveredSyncService? {
    val versionStr = attrs["v"]
    val version = versionStr?.toIntOrNull()
    // Forward-compat: ignore non-numeric version or future protocol versions != 1
    if (versionStr != null && version != 1) return null

    val displayName = attrs["name"]?.takeIf { it.isNotBlank() } ?: name
    val token = attrs["token"]?.takeIf { it.isNotBlank() }
    return DiscoveredSyncService(
        serviceName = name,
        name = displayName,
        host = host,
        port = port,
        token = token,
        version = version ?: 1,
    )
}

/**
 * Discovers Mihon sync servers on the local network using Android NSD (mDNS / DNS-SD).
 *
 * - Emits a live [Flow] of the current discovered list via [discoveries].
 * - Call [refresh] to restart discovery without re-creating the object.
 * - Call [close] to stop (idempotent).
 *
 * Token read-back: [NsdServiceInfo.getAttributes] is API 21+; on older parsers that do not return
 * TXT attributes, [token] will be null and the user must scan the QR code or paste the pairing code manually.
 *
 * Concurrency: at most [MAX_PARALLEL_RESOLVES] simultaneous resolutions; extra requests are queued
 * and resolved as in-flight requests finish.
 */
class LanSyncDiscovery(context: Context) : AutoCloseable {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveriesState = MutableStateFlow<List<DiscoveredSyncService>>(emptyList())
    private val closed = AtomicBoolean(false)
    private val restartPending = AtomicBoolean(false)
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolveSemaphore = Semaphore(MAX_PARALLEL_RESOLVES)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Live list of currently-visible sync servers. Updates whenever a service is found/lost/resolved.
     */
    fun discoveries(): StateFlow<List<DiscoveredSyncService>> = discoveriesState.asStateFlow()

    /**
     * Initiates NSD discovery. Safe to call multiple times – stops the previous session first.
     */
    fun refresh() {
        if (closed.get()) return
        discoveriesState.value = emptyList()
        resolveQueue.clear()
        if (discoveryListener != null) {
            restartPending.set(true)
            stopDiscovery()
        } else {
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        if (closed.get()) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && !closed.get()) {
                    mainHandler.postDelayed({
                        if (!closed.get()) {
                            startDiscovery()
                        }
                    }, 500)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (restartPending.compareAndSet(true, false) && !closed.get()) {
                    startDiscovery()
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onDiscoveryStopped(serviceType: String) {
                if (restartPending.compareAndSet(true, false) && !closed.get()) {
                    startDiscovery()
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (closed.get()) return
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveriesState.update { list ->
                    list.filterNot { it.serviceName == serviceInfo.serviceName }
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Throwable) {
            discoveryListener = null
        }
    }

    private fun stopDiscovery() {
        val l = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(l)
        } catch (_: Throwable) {
            if (restartPending.compareAndSet(true, false) && !closed.get()) {
                startDiscovery()
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (closed.get()) return
        if (!resolveSemaphore.tryAcquire()) {
            resolveQueue.offer(serviceInfo)
            return
        }
        dispatchResolve(serviceInfo)
    }

    private fun dispatchResolve(serviceInfo: NsdServiceInfo) {
        if (closed.get()) {
            resolveSemaphore.release()
            return
        }
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        finishResolve()
                    }

                    override fun onServiceResolved(si: NsdServiceInfo) {
                        try {
                            if (closed.get()) return

                            val attrs = readAttributes(si)
                            val host = si.host?.hostAddress ?: return
                            val discovered = buildDiscoveredSyncService(
                                name = si.serviceName,
                                host = host,
                                port = si.port,
                                attrs = attrs,
                            ) ?: return

                            discoveriesState.update { list ->
                                // Replace existing entry with same service name, or append.
                                val without = list.filterNot { it.serviceName == discovered.serviceName }
                                without + discovered
                            }
                        } finally {
                            finishResolve()
                        }
                    }
                },
            )
        } catch (_: Throwable) {
            finishResolve()
        }
    }

    private fun finishResolve() {
        resolveSemaphore.release()
        processNextResolve()
    }

    private fun processNextResolve() {
        if (closed.get()) {
            resolveQueue.clear()
            return
        }
        val next = resolveQueue.poll() ?: return
        if (resolveSemaphore.tryAcquire()) {
            dispatchResolve(next)
        } else {
            resolveQueue.offer(next)
        }
    }

    /**
     * Reads TXT attributes from a resolved [NsdServiceInfo].
     * [NsdServiceInfo.getAttributes] is available on API 21+; returns an empty map on failure.
     */
    private fun readAttributes(si: NsdServiceInfo): Map<String, String?> {
        return try {
            si.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8) }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            restartPending.set(false)
            resolveQueue.clear()
            mainHandler.removeCallbacksAndMessages(null)
            stopDiscovery()
        }
    }

    companion object {
        const val SERVICE_TYPE = "_mihonsync._tcp."
        private const val MAX_PARALLEL_RESOLVES = 4
    }
}
