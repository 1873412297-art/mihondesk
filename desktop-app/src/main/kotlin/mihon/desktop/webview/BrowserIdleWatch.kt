package mihon.desktop.webview

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.desktop.logging.DesktopLogger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Returns true once a browser session has been idle for the configured timeout. */
internal fun browserIdleExpired(lastActivityNanos: Long, nowNanos: Long, timeoutMillis: Long): Boolean =
    nowNanos - lastActivityNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

/** A daemon coroutine watches browser activity; idle sessions release their native process tree. */
internal class BrowserIdleWatch(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
    private val sessionId: String,
    private val close: () -> Unit,
    private val clock: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val lastActivityNanos = AtomicLong(clock())
    private var job: Job? = null

    fun touch() {
        lastActivityNanos.set(clock())
    }

    fun start() {
        check(job == null) { "Idle watch already started" }
        job = scope.launch {
            while (isActive) {
                delay(timeoutMillis.coerceAtMost(1_000))
                if (browserIdleExpired(lastActivityNanos.get(), clock(), timeoutMillis)) {
                    DesktopLogger.info("DesktopWebViewManager", "Closing idle browser session $sessionId")
                    this@BrowserIdleWatch.close.invoke()
                    break
                }
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
    }
}
