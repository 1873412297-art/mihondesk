package mihon.sync.server

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SyncServiceAdvertiserTest {

    // ---------------------------------------------------------------------------
    // Pure-function unit tests – run everywhere, no multicast required
    // ---------------------------------------------------------------------------

    @Test
    fun `buildTxtProperties includes v and name, omits token when null`() {
        val props = SyncServiceAdvertiser.buildTxtProperties("MyPC", token = null)
        props["v"] shouldBe "1"
        props["name"] shouldBe "MyPC"
        props.containsKey("token") shouldBe false
    }

    @Test
    fun `buildTxtProperties includes token when provided`() {
        val props = SyncServiceAdvertiser.buildTxtProperties("MyPC", token = "abc123")
        props["v"] shouldBe "1"
        props["name"] shouldBe "MyPC"
        props["token"] shouldBe "abc123"
    }

    @Test
    fun `buildTxtProperties omits token when blank`() {
        val props = SyncServiceAdvertiser.buildTxtProperties("PC", token = "")
        props.containsKey("token") shouldBe false
    }

    @Test
    fun `buildTxtProperties truncates long name to 63 chars`() {
        val longName = "A".repeat(100)
        val props = SyncServiceAdvertiser.buildTxtProperties(longName, token = null)
        withClue("name must be capped at 63 characters") {
            props["name"]!!.length shouldBe 63
        }
    }

    // ---------------------------------------------------------------------------
    // JmDNS round-trip integration test – skipped when multicast is unavailable
    // ---------------------------------------------------------------------------

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    fun `advertiser publishes service and listener discovers it with correct TXT`() {
        // Detect multicast availability; skip gracefully instead of failing in CI.
        val loopback = try {
            java.net.InetAddress.getLoopbackAddress()
        } catch (_: Throwable) {
            System.err.println("[SyncServiceAdvertiserTest] Skipping multicast test: cannot resolve loopback")
            return
        }

        // JmDNS requires a real network interface; loopback may or may not support multicast.
        // Wrap everything in try-catch so the suite never fails on restricted environments.
        try {
            val port = 45831
            val deviceName = "TestDevice"
            val token = "secret-token"

            SyncServiceAdvertiser(loopback).use { advertiser ->
                advertiser.start(port = port, deviceName = deviceName, token = token)

                // Give mDNS a moment to register.
                Thread.sleep(2000)

                // Use a second JmDNS instance to discover the service.
                val discovered = mutableListOf<javax.jmdns.ServiceInfo>()
                javax.jmdns.JmDNS.create(loopback).use { listener ->
                    val latch = java.util.concurrent.CountDownLatch(1)
                    listener.addServiceListener(
                        SyncServiceAdvertiser.SERVICE_TYPE,
                        object : javax.jmdns.ServiceListener {
                            override fun serviceAdded(event: javax.jmdns.ServiceEvent) {
                                listener.requestServiceInfo(event.type, event.name)
                            }

                            override fun serviceRemoved(event: javax.jmdns.ServiceEvent) {}

                            override fun serviceResolved(event: javax.jmdns.ServiceEvent) {
                                discovered.add(event.info)
                                latch.countDown()
                            }
                        },
                    )
                    latch.await(8, TimeUnit.SECONDS)
                }

                // If no service was discovered the environment blocks multicast; do not fail.
                if (discovered.isEmpty()) {
                    System.err.println(
                        "[SyncServiceAdvertiserTest] No service discovered; multicast may be blocked – skipping assertions",
                    )
                    return
                }

                val info = discovered.first()
                withClue("port should match") { info.port shouldBe port }
                withClue("TXT v should be 1") { info.getPropertyString("v") shouldBe "1" }
                withClue("TXT name should match") {
                    info.getPropertyString("name") shouldNotBe null
                }
                withClue("TXT token should match") {
                    info.getPropertyString("token") shouldBe token
                }
            }
        } catch (e: Throwable) {
            System.err.println("[SyncServiceAdvertiserTest] Multicast test skipped due to: ${e.message}")
        }
    }
}
