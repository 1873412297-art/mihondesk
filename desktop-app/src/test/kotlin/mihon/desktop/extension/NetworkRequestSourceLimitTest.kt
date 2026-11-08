package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.extension.ipc.RequestPriority
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** The per-source cap on background traffic that keeps batch downloads under a site's WAF limit. */
class NetworkRequestSourceLimitTest {
    private class Probe {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val release = CompletableDeferred<Unit>()

        suspend fun hold() {
            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                release.await()
            } finally {
                running.decrementAndGet()
            }
        }
    }

    @Test
    fun `one source downloads at most three pages at once across all its hosts`(): Unit = runBlocking {
        NetworkRequestScheduler().use { scheduler ->
            val probe = Probe()
            // Pages of one chapter are often spread over several CDN hosts.
            val jobs = (0 until 10).map { index ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.withPermit("cdn${index % 4}.example", "ext:42", RequestPriority.BACKGROUND) {
                        probe.hold()
                    }
                }
            }
            probe.running.get() shouldBe 3
            probe.release.complete(Unit)
            withTimeout(5_000) { jobs.awaitAll() }
            probe.peak.get() shouldBe 3
        }
    }

    @Test
    fun `another source is not held back by a busy one`(): Unit = runBlocking {
        NetworkRequestScheduler().use { scheduler ->
            val busy = Probe()
            val other = Probe()
            val jobs = (0 until 6).map {
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.withPermit("a.example", "ext:1", RequestPriority.BACKGROUND) { busy.hold() }
                }
            } + (0 until 3).map {
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.withPermit("b.example", "ext:2", RequestPriority.BACKGROUND) { other.hold() }
                }
            }
            busy.running.get() shouldBe 3
            other.running.get() shouldBe 3
            busy.release.complete(Unit)
            other.release.complete(Unit)
            withTimeout(5_000) { jobs.awaitAll() }
        }
    }

    @Test
    fun `the reader is not limited by the background cap of its own source`(): Unit = runBlocking {
        NetworkRequestScheduler().use { scheduler ->
            val downloads = Probe()
            val jobs = (0 until 3).map {
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.withPermit("a.example", "ext:1", RequestPriority.BACKGROUND) { downloads.hold() }
                }
            }
            val read = CompletableDeferred<Unit>()
            val reader = async(start = CoroutineStart.UNDISPATCHED) {
                scheduler.withPermit("a.example", "ext:1", RequestPriority.READER) { read.complete(Unit) }
            }
            withTimeout(5_000) { read.await() }
            downloads.release.complete(Unit)
            withTimeout(5_000) { (jobs + reader).awaitAll() }
        }
    }
}
