package mihon.desktop.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.transport.file.FileTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopSyncSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `auto sync triggers only when enabled and interval elapsed`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val prefFile = tempDir.resolve("prefs.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val syncDir = tempDir.resolve("sync-folder")
            Files.createDirectories(syncDir)

            var simulatedTime = 1_000_000_000L
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = scope,
                clock = { simulatedTime },
            )

            // 1. Sync is disabled
            prefStore.save(
                DesktopPreferences(
                    syncEnabled = false,
                    syncDirectoryPath = syncDir.toString(),
                    syncIntervalMinutes = 15,
                ),
            )
            assertNull(scheduler.checkAndRunAutoSync())

            // 2. Sync enabled, but syncDirectoryPath is blank
            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncDirectoryPath = "",
                    syncIntervalMinutes = 15,
                ),
            )
            assertNull(scheduler.checkAndRunAutoSync())

            // 3. Sync enabled, interval not elapsed
            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncDirectoryPath = syncDir.toString(),
                    syncIntervalMinutes = 15,
                    lastSyncEpochMillis = simulatedTime,
                ),
            )
            simulatedTime += 10 * 60_000L // only 10 minutes
            assertNull(scheduler.checkAndRunAutoSync())

            // 4. Interval elapsed (16 minutes > 15 minutes)
            simulatedTime += 6 * 60_000L
            val report = scheduler.checkAndRunAutoSync()
            assertNotNull(report)
            assertTrue(report!!.success)
            assertEquals(simulatedTime, prefStore.load().lastSyncEpochMillis)
            assertTrue(prefStore.load().lastSyncMessage.contains("Pulled:"))
        } finally {
            repo.close()
        }
    }

    @Test
    fun `syncNow executes full cycle and records import report`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test2.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val prefFile = tempDir.resolve("prefs2.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val syncDir = tempDir.resolve("sync-folder2")
            Files.createDirectories(syncDir)

            // Prepare a remote changeset in sync folder
            val remoteTransport = FileTransport(syncDir, "phone-device")
            val remoteChangeset = Changeset(
                deviceId = "phone-device",
                baseSchema = 1,
                cursor = 1L,
                producedAt = 500_000L,
                upserts = EntityDelta(
                    mangas = listOf(
                        AndroidBackupManga(
                            source = 100L,
                            url = "/remote/manga",
                            title = "Remote Title",
                            artist = "Remote Artist",
                            author = "Remote Author",
                            description = "Remote Desc",
                            genre = listOf("Action"),
                            status = 1,
                            thumbnailUrl = "https://example.com/cover.png",
                            favorite = true,
                            initialized = true,
                            lastModifiedAt = 500_000L,
                        ),
                    ),
                ),
            )
            remoteTransport.push(remoteChangeset)

            // Local has a manga too
            repo.insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = 100L,
                    url = "/local/manga",
                    title = "Local Title",
                    favorite = true,
                ),
            )

            var simulatedTime = 600_000L
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncDirectoryPath = syncDir.toString(),
                    syncDeviceId = "desktop-device",
                ),
            )

            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = scope,
                clock = { simulatedTime },
            )

            val report = scheduler.syncNow()
            assertTrue(report.success)
            assertEquals(1, report.pulledChangesetCount)
            assertEquals(1, report.mangaInserted)
            assertNotNull(report.pushedChangeset)

            // Verify local DB now has remote manga
            val remoteManga = repo.findManga(100L, "/remote/manga")
            assertNotNull(remoteManga)
            assertEquals("Remote Title", remoteManga?.title)

            // Verify import report was written
            val latestReport = repo.latestImportReport()
            assertNotNull(latestReport)
            assertEquals(1L, latestReport!!.counts.mangaInserted)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `syncNow fails cleanly when directory is unconfigured`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test3.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val prefFile = tempDir.resolve("prefs3.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncDirectoryPath = "",
                ),
            )

            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = scope,
            )

            val report = scheduler.syncNow()
            assertFalse(report.success)
            assertEquals("Sync directory not configured", report.errorMessage)
        } finally {
            repo.close()
        }
    }
}
