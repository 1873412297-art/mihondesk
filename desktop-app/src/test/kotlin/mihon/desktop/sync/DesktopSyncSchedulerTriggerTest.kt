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

internal fun syncReport(success: Boolean, error: String? = null, pulled: Int = 0, inserted: Int = 0) = SyncReport(
    success = success,
    pulledChangesetCount = pulled,
    mangaInserted = inserted,
    mangaMerged = 0,
    chapterInserted = 0,
    chapterMerged = 0,
    categoriesLinked = 0,
    pushedChangeset = null,
    errorMessage = error,
)

internal class FakeSyncBackend(
    var ready: Boolean = true,
    var dirty: Boolean = false,
    private val run: suspend (Int) -> SyncReport? = { syncReport(true) },
) : SyncBackend {
    val runs = AtomicInteger()
    override val sourceIdentifier: String = "gdrive:appDataFolder"
    override fun isReady(): Boolean = ready
    override suspend fun hasLocalChanges(): Boolean = dirty
    override suspend fun syncNow(): SyncReport? = run(runs.incrementAndGet())
}

class DesktopSyncSchedulerTriggerTest {
    private val configured = DesktopPreferences().copy(
        syncIntervalMinutes = 60,
        lastSyncEpochMillis = 1_000_000L,
    )

    private fun auto(ready: Boolean, now: Long, dirty: Boolean, interval: Int = 60) = shouldAutoSync(
        driveReady = ready,
        intervalMinutes = interval,
        lastSyncAt = 1_000_000L,
        now = now,
        localDirty = dirty,
    )

    @Test
    fun `local changes sync promptly, otherwise the interval applies, and only when Drive is ready`() {
        auto(true, 1_060_000L, dirty = true) shouldBe true
        auto(true, 1_060_000L, dirty = false) shouldBe false
        auto(true, 1_000_000L + 3_600_000L, dirty = false) shouldBe true
        auto(true, 9_999_999L, dirty = false, interval = 0) shouldBe false
        auto(false, 9_999_999L, dirty = true) shouldBe false
    }

    private suspend fun awaitUntil(check: () -> Boolean) = withTimeout(5_000) {
        while (!check()) delay(10)
    }

    @Test
    fun `bursts of requests coalesce into one sync recorded in the activity center`(): Unit = runBlocking {
        val runtime = DesktopRuntime.forTesting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runtime.preferences.updatePreferences { configured }
            val center = ActivityCenter()
            val backend = FakeSyncBackend()
            val scheduler = DesktopSyncScheduler(
                preferenceStore = runtime.preferences,
                library = runtime.library,
                scope = scope,
                activityCenter = center,
                requestDebounceMillis = 100,
                backend = backend,
            )
            repeat(5) { scheduler.requestSync() }
            awaitUntil {
                center.items.value.any { it.kind == ActivityKind.SYNC && it.state == ActivityState.SUCCEEDED }
            }
            delay(300)
            backend.runs.get() shouldBe 1
        } finally {
            scope.cancel()
            runtime.shutdown()
        }
    }

    @Test
    fun `requests are ignored while Drive is not signed in`(): Unit = runBlocking {
        val runtime = DesktopRuntime.forTesting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val backend = FakeSyncBackend(ready = false, dirty = true)
            val scheduler = DesktopSyncScheduler(
                preferenceStore = runtime.preferences,
                library = runtime.library,
                scope = scope,
                requestDebounceMillis = 10,
                backend = backend,
            )
            scheduler.requestSync()
            scheduler.checkAndRunAutoSync() shouldBe null
            delay(200)
            backend.runs.get() shouldBe 0
        } finally {
            scope.cancel()
            runtime.shutdown()
        }
    }

    @Test
    fun `failed syncs show the reason and retry from the activity center`(): Unit = runBlocking {
        val runtime = DesktopRuntime.forTesting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runtime.preferences.updatePreferences { configured }
            val center = ActivityCenter()
            val backend = FakeSyncBackend(
                run = { n -> if (n == 1) syncReport(false, "Connection refused") else syncReport(true) },
            )
            val scheduler = DesktopSyncScheduler(
                preferenceStore = runtime.preferences,
                library = runtime.library,
                scope = scope,
                activityCenter = center,
                requestDebounceMillis = 10,
                backend = backend,
            )
            scheduler.requestSync()
            awaitUntil { center.items.value.any { it.state == ActivityState.FAILED } }
            val failed = center.items.value.single { it.kind == ActivityKind.SYNC }
            failed.failureReason shouldBe "Connection refused"
            center.retry(failed.id) shouldBe true
            awaitUntil { center.items.value.single { it.kind == ActivityKind.SYNC }.state == ActivityState.SUCCEEDED }
            backend.runs.get() shouldBe 2
        } finally {
            scope.cancel()
            runtime.shutdown()
        }
    }
}
