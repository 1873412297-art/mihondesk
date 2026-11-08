package mihon.desktop.webview

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BrowserIdleWatchTest {
    @Test fun `activity resets the idle deadline and expiration is inclusive`() {
        assertFalse(browserIdleExpired(lastActivityNanos = 1_000, nowNanos = 5_000, timeoutMillis = 1))
        assertTrue(browserIdleExpired(lastActivityNanos = 1_000, nowNanos = 1_001_000, timeoutMillis = 1))
        assertFalse(browserIdleExpired(lastActivityNanos = 2_000, nowNanos = 1_001_000, timeoutMillis = 1))
    }

    @Test fun `idle watcher closes its session exactly once`() = runTest {
        val closes = AtomicInteger()
        val watch = BrowserIdleWatch(backgroundScope, timeoutMillis = 40, sessionId = "test", close = {
            closes.incrementAndGet()
        }, clock = { TimeUnit.MILLISECONDS.toNanos(currentTime) })
        watch.start()
        advanceTimeBy(40)
        runCurrent()
        assertEquals(1, closes.get())
        watch.close()
    }

    @Test fun `closing the watch prevents an idle callback`() = runTest {
        val closes = AtomicInteger()
        val watch = BrowserIdleWatch(backgroundScope, timeoutMillis = 20, sessionId = "test", close = {
            closes.incrementAndGet()
        }, clock = { TimeUnit.MILLISECONDS.toNanos(currentTime) })
        watch.start()
        watch.close()
        advanceTimeBy(60)
        runCurrent()
        assertEquals(0, closes.get())
    }
}
