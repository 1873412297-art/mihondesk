package mihon.desktop.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopSyncServerManagerStartupTest {
    @TempDir
    lateinit var tempDir: Path

    private class SlowAdvertiser(private val delayMs: Long) : DesktopSyncServerManager.Advertiser {
        val started = CountDownLatch(1)

        @Volatile override var isAdvertising = false
        override fun start(port: Int, deviceName: String, token: String?) {
            Thread.sleep(delayMs)
            isAdvertising = true
            started.countDown()
        }
        override fun updateAdvertisement(port: Int, deviceName: String, quickPairEnabled: Boolean, token: String?) {}
        override fun close() {
            isAdvertising = false
        }
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    @Test
    fun `start does not block on slow LAN discovery or mDNS registration`() {
        val advertiser = SlowAdvertiser(delayMs = 4_000)
        val manager = DesktopSyncServerManager(
            storageDir = tempDir,
            lanAddressProvider = {
                Thread.sleep(2_000)
                InetAddress.getLoopbackAddress()
            },
            advertiserFactory = { advertiser },
        )
        try {
            val begin = System.nanoTime()
            val ok = manager.start(port = freePort(), token = "secret", deviceName = "Test PC")
            val elapsedMs = (System.nanoTime() - begin) / 1_000_000
            assertTrue(ok, "server should start: ${manager.lastError}")
            assertTrue(manager.isRunning)
            assertTrue(elapsedMs < 1_500, "start() took $elapsedMs ms; must not wait for mDNS/LAN probing")
            // Advertisement still happens in the background.
            assertTrue(advertiser.started.await(15, TimeUnit.SECONDS), "advertisement should complete in background")
        } finally {
            val stopBegin = System.nanoTime()
            manager.stop()
            val stopMs = (System.nanoTime() - stopBegin) / 1_000_000
            assertTrue(stopMs < 3_000, "stop() took $stopMs ms")
        }
    }

    @Test
    fun `advertiser failures are captured and do not fail start`() {
        val manager = DesktopSyncServerManager(
            storageDir = tempDir,
            lanAddressProvider = { InetAddress.getLoopbackAddress() },
            advertiserFactory = { error("no multicast") },
        )
        try {
            assertTrue(manager.start(port = freePort(), token = "secret"))
            manager.awaitAdvertisementIdle(10_000)
            assertEquals("no multicast", manager.advertiserLastError)
            assertTrue(manager.isRunning)
        } finally {
            manager.stop()
        }
    }
}
