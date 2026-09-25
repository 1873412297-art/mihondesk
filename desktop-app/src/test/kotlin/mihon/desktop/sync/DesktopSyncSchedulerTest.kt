package mihon.desktop.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import mihon.sync.transport.http.HttpTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Path

class DesktopSyncSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `auto sync triggers only when enabled and interval elapsed`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        val serverDbFile = tempDir.resolve("server-changesets.db")
        val serverStore = SqliteChangesetStore.open(serverDbFile)
        val serverPort = ServerSocket(0).use { it.localPort }
        val server = SyncServer(
            host = "127.0.0.1",
            port = serverPort,
            token = "test-token",
            store = serverStore,
        )
        server.start(wait = false)
        try {
            val prefFile = tempDir.resolve("prefs.properties")
            val prefStore = DesktopPreferenceStore(prefFile)

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
                    syncServerEnabled = true,
                    syncServerPort = serverPort,
                    syncServerToken = "test-token",
                    syncIntervalMinutes = 15,
                ),
            )
            assertNull(scheduler.checkAndRunAutoSync())

            // 2. Sync enabled, but syncServerToken is blank
            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncServerEnabled = true,
                    syncServerPort = serverPort,
                    syncServerToken = "",
                    syncIntervalMinutes = 15,
                ),
            )
            assertNull(scheduler.checkAndRunAutoSync())

            // 3. Sync enabled, interval not elapsed
            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncServerEnabled = true,
                    syncServerPort = serverPort,
                    syncServerToken = "test-token",
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
            server.stop()
            serverStore.close()
            repo.close()
        }
    }

    @Test
    fun `syncNow executes full cycle and records import report`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test2.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        val serverDbFile = tempDir.resolve("server-changesets2.db")
        val serverStore = SqliteChangesetStore.open(serverDbFile)
        val serverPort = ServerSocket(0).use { it.localPort }
        val server = SyncServer(
            host = "127.0.0.1",
            port = serverPort,
            token = "secret123",
            store = serverStore,
        )
        server.start(wait = false)
        try {
            val prefFile = tempDir.resolve("prefs2.properties")
            val prefStore = DesktopPreferenceStore(prefFile)

            // Prepare a remote changeset in server via HttpTransport
            val remoteTransport = HttpTransport(
                baseUrl = "http://127.0.0.1:$serverPort",
                token = "secret123",
                ownsClient = true,
            )
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
            remoteTransport.close()

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

            val simulatedTime = 600_000L
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncServerEnabled = true,
                    syncServerPort = serverPort,
                    syncServerToken = "secret123",
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
            server.stop()
            serverStore.close()
            repo.close()
        }
    }

    @Test
    fun `syncNow fails cleanly when http token is blank`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test-http-blank.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val prefFile = tempDir.resolve("prefs-http-blank.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncServerToken = "",
                ),
            )

            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = scope,
            )

            val report = scheduler.syncNow()
            assertFalse(report.success)
            assertEquals("Builtin sync server token not configured", report.errorMessage)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `syncNow succeeds via HttpTransport against embedded sync-server`(): Unit = runBlocking {
        val dbFile = tempDir.resolve("sync-test-http-success.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        val serverDbFile = tempDir.resolve("server-changesets.db")
        val serverStore = SqliteChangesetStore.open(serverDbFile)
        val serverPort = ServerSocket(0).use { it.localPort }
        val server = SyncServer(
            host = "127.0.0.1",
            port = serverPort,
            token = "secret123",
            store = serverStore,
        )
        server.start(wait = false)
        try {
            val prefFile = tempDir.resolve("prefs-http-success.properties")
            val prefStore = DesktopPreferenceStore(prefFile)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            prefStore.save(
                DesktopPreferences(
                    syncEnabled = true,
                    syncServerPort = server.port,
                    syncServerToken = "secret123",
                    syncDeviceId = "desktop-http-dev",
                ),
            )

            val scheduler = DesktopSyncScheduler(
                preferenceStore = prefStore,
                library = repo,
                scope = scope,
            )

            val report = scheduler.syncNow()
            assertTrue(report.success)
            assertEquals(0, report.pulledChangesetCount)
        } finally {
            server.stop()
            serverStore.close()
            repo.close()
        }
    }
}
