package mihon.desktop.sync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.DesktopRuntime
import mihon.desktop.activity.ActivityCenter
import mihon.desktop.activity.ActivityKind
import mihon.desktop.activity.ActivityState
import mihon.desktop.preferences.DesktopPreferences
import mihon.sync.engine.SyncReport
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class DesktopSyncSchedulerTriggerTest {
    private fun report(success: Boolean, error: String? = null) = SyncReport(
        success = success,
        pulledChangesetCount = 0,
        mangaInserted = 0,
        mangaMerged = 0,
        chapterInserted = 0,
        chapterMerged = 0,
        categoriesLinked = 0,
        pushedChangeset = null,
        errorMessage = error,
    )

    private val configured = DesktopPreferences().copy(
        syncEnabled = true,
        syncServerEnabled = true,
        syncServerToken = "token",
        syncIntervalMinutes = 60,
        lastSyncEpochMillis = 1_000_000L,
    )

    @Test
    fun `local changes sync promptly, otherwise the interval applies`() {
        shouldAutoSync(configured, now = 1_000_000L + 60_000L, localDirty = true) shouldBe true
        shouldAutoSync(configured, now = 1_000_000L + 60_000L, localDirty = false) shouldBe false
        shouldAutoSync(configured, now = 1_000_000L + 3_600_000L, localDirty = false) shouldBe true
        shouldAutoSync(configured.copy(syncEnabled = false), now = 9_999_999L, localDirty = true) shouldBe false
        shouldAutoSync(configured.copy(syncServerToken = ""), now = 9_999_999L, localDirty = true) shouldBe false
    }

    private suspend fun awaitUntil(check: () -> Boolean) = withTimeout(5_000) {
        while (!check()) delay(10)
    }

    @Test
    fun `bursts of requests coalesce into one sync recorded in the activity center`() = runBlocking {
        val runtime = DesktopRuntime.forTesting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runtime.preferences.updatePreferences { configured }
            val runs = AtomicInteger()
            val center = ActivityCenter()
            val scheduler = DesktopSyncScheduler(
                preferenceStore = runtime.preferences,
                library = runtime.library,
                scope = scope,
                activityCenter = center,
                requestDebounceMillis = 100,
                runner = {
                    runs.incrementAndGet()
                    report(success = true)
                },
            )
            repeat(5) { scheduler.requestSync() }
            awaitUntil {
                center.items.value.any { it.kind == ActivityKind.SYNC && it.state == ActivityState.SUCCEEDED }
            }
            delay(300)
            runs.get() shouldBe 1
        } finally {
            scope.cancel()
            runtime.shutdown()
        }
    }

    @Test
    fun `failed syncs show the reason and retry from the activity center`() = runBlocking {
        val runtime = DesktopRuntime.forTesting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runtime.preferences.updatePreferences { configured }
            val runs = AtomicInteger()
            val center = ActivityCenter()
            val scheduler = DesktopSyncScheduler(
                preferenceStore = runtime.preferences,
                library = runtime.library,
                scope = scope,
                activityCenter = center,
                requestDebounceMillis = 10,
                runner = {
                    if (runs.incrementAndGet() == 1) report(false, "Connection refused") else report(true)
                },
            )
            scheduler.requestSync()
            awaitUntil { center.items.value.any { it.state == ActivityState.FAILED } }
            val failed = center.items.value.single { it.kind == ActivityKind.SYNC }
            failed.failureReason shouldBe "Connection refused"
            center.retry(failed.id) shouldBe true
            awaitUntil { center.items.value.single { it.kind == ActivityKind.SYNC }.state == ActivityState.SUCCEEDED }
            runs.get() shouldBe 2
        } finally {
            scope.cancel()
            runtime.shutdown()
        }
    }
}
