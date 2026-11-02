package mihon.desktop.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopSyncSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `auto sync runs via Drive when the interval elapsed or local changes wait`(): Unit = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("sync-test.db"))
        try {
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            var now = 1_000_000_000L
            prefStore.save(DesktopPreferences(syncIntervalMinutes = 15, lastSyncEpochMillis = now))
            val backend = FakeSyncBackend()
            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                clock = { now },
                backend = backend,
            )
            now += 10 * 60_000L
            scheduler.checkAndRunAutoSync() shouldBe null
            backend.dirty = true
            scheduler.checkAndRunAutoSync()!!.success shouldBe true
            backend.dirty = false
            now += 16 * 60_000L
            scheduler.checkAndRunAutoSync()!!.success shouldBe true
            backend.runs.get() shouldBe 2
            prefStore.load().lastSyncEpochMillis shouldBe now
            prefStore.load().lastSyncMessage shouldContain "Pulled:"
        } finally {
            repo.close()
        }
    }

    @Test
    fun `syncNow records an import report for the Drive source`(): Unit = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("sync-test2.db"))
        try {
            val scheduler = DesktopSyncScheduler(
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs2.properties")),
                library = repo,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                clock = { 600_000L },
                backend = FakeSyncBackend(run = { syncReport(true, pulled = 1, inserted = 1) }),
            )
            scheduler.syncNow()!!.success shouldBe true
            val latest = repo.latestImportReport()
            latest shouldNotBe null
            latest!!.counts.mangaInserted shouldBe 1L
            latest.sourcePath shouldBe "gdrive:appDataFolder"
        } finally {
            repo.close()
        }
    }

    @Test
    fun `syncNow without a signed-in Drive is a no-op`(): Unit = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("sync-test3.db"))
        try {
            val scheduler = DesktopSyncScheduler(
                preferenceStore = DesktopPreferenceStore(tempDir.resolve("prefs3.properties")),
                library = repo,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                backend = FakeSyncBackend(ready = false),
            )
            scheduler.syncNow() shouldBe null
            repo.latestImportReport() shouldBe null
        } finally {
            repo.close()
        }
    }
}
