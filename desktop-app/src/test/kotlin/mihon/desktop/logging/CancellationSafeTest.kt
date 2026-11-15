package mihon.desktop.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CancellationSafeTest {

    @Test
    fun `returns success and ordinary failures like runCatching`() {
        runSuspendCatching { 42 }.getOrNull() shouldBe 42
        runSuspendCatching { error("boom") }.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
    }

    @Test
    fun `rethrows cancellation instead of returning a failure`() {
        assertThrows<CancellationException> {
            runSuspendCatching { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `a cancelled coroutine does not continue past the call`() = runTest {
        val started = CompletableDeferred<Unit>()
        var continuedAfterCancel = false
        val job = launch {
            runSuspendCatching {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            continuedAfterCancel = true
        }
        started.await()
        job.cancel()
        job.join()
        yield()
        continuedAfterCancel shouldBe false
    }
}
