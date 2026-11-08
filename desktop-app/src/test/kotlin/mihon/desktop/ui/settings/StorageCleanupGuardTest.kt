package mihon.desktop.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageCleanupGuardTest {
    @Test
    fun `cleanup failure clears the busy flag and reports the error`() {
        val busy = mutableListOf<Boolean>()
        var failed = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        scope.guardStorageCleanup(
            setBusy = { busy += it },
            reportFailure = { failed = true },
        ) { error("disk full") }

        assertEquals(listOf(true, false), busy)
        assertTrue(failed)
    }

    @Test
    fun `cancellation clears the busy flag without reporting a failure`() {
        val busy = mutableListOf<Boolean>()
        var failed = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        scope.guardStorageCleanup(
            setBusy = { busy += it },
            reportFailure = { failed = true },
        ) { throw CancellationException("left settings") }

        assertEquals(listOf(true, false), busy)
        assertFalse(failed)
    }
}
