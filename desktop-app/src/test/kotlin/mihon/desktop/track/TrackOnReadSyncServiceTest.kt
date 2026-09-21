package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TrackOnReadSyncServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reading chapter updates tracking record and syncs or enqueues`() = runBlocking {
        val dbFile = tempDir.resolve("test-library.db")
        val queueFile = tempDir.resolve("tracking-queue.json")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/one-piece",
                title = "One Piece",
            ),
        )

        // Bind tracker 1 (MAL) and tracker 2 (AniList)
        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "One Piece",
                lastChapterRead = 10.0,
                totalChapters = 100,
            ),
        )
        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 2L,
                remoteId = 202L,
                title = "One Piece",
                lastChapterRead = 5.0,
                totalChapters = 100,
            ),
        )

        val queue = OfflineTrackingQueue(queueFile)
        val malTracker = TrackerTestFakeTracker(id = 1L, name = "MyAnimeList")
        val aniListTracker = TrackerTestFakeTracker(id = 2L, name = "AniList")
        val trackerManager = DesktopTrackerManager(listOf(malTracker, aniListTracker))
        // MAL is logged in, AniList is not
        malTracker.login(mapOf("username" to "MALUser", "password" to "pass"))

        val syncService = TrackOnReadSyncService(
            repository = repo,
            mutationPort = repo,
            trackerManager = trackerManager,
            trackingQueue = queue,
        )

        // Read chapter 15 (ahead of both 10 and 5)
        val synced = syncService.onChapterRead(mangaId, 15.0)
        assertEquals(1, synced) // MAL synced remotely
        assertEquals(15.0, malTracker.updates.single().lastChapterRead)

        // Both DB records are updated to chapter 15
        val updatedTracks = repo.trackingSnapshot(mangaId)
        val malTrack = updatedTracks.first { it.trackerId == 1L }
        val anilistTrack = updatedTracks.first { it.trackerId == 2L }
        assertEquals(15.0, malTrack.lastChapterRead)
        assertEquals(15.0, anilistTrack.lastChapterRead)

        // AniList (unlogged) is queued in offline queue
        val pending = queue.peekAll()
        assertEquals(1, pending.size)
        assertEquals(2L, pending.first().trackerId)
        assertEquals(15.0, pending.first().chapterNumber)

        // Now AniList logs in and queue is flushed
        aniListTracker.login(mapOf("token" to "anilist-token"))
        val flushed = syncService.flushPending()
        assertEquals(1, flushed)
        assertTrue(queue.peekAll().isEmpty())
        assertEquals(15.0, aniListTracker.updates.single().lastChapterRead)
        repo.close()
    }

    @Test
    fun `completing last chapter sets track status to COMPLETED`() = runBlocking {
        val dbFile = tempDir.resolve("test-completed.db")
        val queueFile = tempDir.resolve("tracking-queue-completed.json")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/short-story",
                title = "Short Story",
            ),
        )

        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "Short Story",
                lastChapterRead = 4.0,
                totalChapters = 5,
                status = TrackStatus.READING.value,
            ),
        )

        val queue = OfflineTrackingQueue(queueFile)
        val trackerManager = DesktopTrackerManager()
        val syncService = TrackOnReadSyncService(
            repository = repo,
            mutationPort = repo,
            trackerManager = trackerManager,
            trackingQueue = queue,
        )

        syncService.onChapterRead(mangaId, 5.0)

        val updated = repo.trackingSnapshot(mangaId).first()
        assertEquals(5.0, updated.lastChapterRead)
        assertEquals(TrackStatus.COMPLETED.value, updated.status)
        repo.close()
    }

    @Test
    fun `oneshot chapter with negative number syncs lastChapterRead as 1 to logged in tracker`() = runBlocking {
        val dbFile = tempDir.resolve("test-oneshot.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/one-shot",
                title = "One Shot Manga",
            ),
        )

        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "One Shot Manga",
                lastChapterRead = 0.0,
                totalChapters = 1,
            ),
        )

        val malTracker = TrackerTestFakeTracker(id = 1L, name = "MyAnimeList")
        malTracker.login(mapOf("username" to "MALUser", "password" to "pass"))
        val trackerManager = DesktopTrackerManager(listOf(malTracker))

        val syncService = TrackOnReadSyncService(
            repository = repo,
            mutationPort = repo,
            trackerManager = trackerManager,
            trackingQueue = null,
        )

        val synced = syncService.onChapterRead(mangaId, -1.0)
        assertEquals(1, synced)
        assertEquals(1.0, malTracker.updates.single().lastChapterRead)

        val updated = repo.trackingSnapshot(mangaId).single()
        assertEquals(1.0, updated.lastChapterRead)
        assertEquals(TrackStatus.COMPLETED.value, updated.status)
        repo.close()
    }

    @Test
    fun `second read of negative chapter number does not double sync`() = runBlocking {
        val dbFile = tempDir.resolve("test-oneshot-idempotent.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/one-shot-2",
                title = "One Shot Manga 2",
            ),
        )

        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "One Shot Manga 2",
                lastChapterRead = 0.0,
                totalChapters = 1,
            ),
        )

        val malTracker = TrackerTestFakeTracker(id = 1L, name = "MyAnimeList")
        malTracker.login(mapOf("username" to "MALUser", "password" to "pass"))
        val trackerManager = DesktopTrackerManager(listOf(malTracker))

        val syncService = TrackOnReadSyncService(
            repository = repo,
            mutationPort = repo,
            trackerManager = trackerManager,
            trackingQueue = null,
        )

        val syncedFirst = syncService.onChapterRead(mangaId, -1.0)
        assertEquals(1, syncedFirst)
        assertEquals(1, malTracker.updates.size)
        assertEquals(1.0, malTracker.updates.last().lastChapterRead)

        // Second read of the same -1.0 chapter must skip and not double-sync
        val syncedSecond = syncService.onChapterRead(mangaId, -1.0)
        assertEquals(0, syncedSecond)
        assertEquals(1, malTracker.updates.size)

        val updated = repo.trackingSnapshot(mangaId).single()
        assertEquals(1.0, updated.lastChapterRead)
        repo.close()
    }

    @Test
    fun `positive chapter number syncs normally`() = runBlocking {
        val dbFile = tempDir.resolve("test-positive.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)

        val mangaId = repo.insertManga(
            MangaRecord(
                sourceId = 100L,
                url = "/manga/series",
                title = "Series Manga",
            ),
        )

        repo.insertTracking(
            TrackingRecord(
                mangaId = mangaId,
                trackerId = 1L,
                remoteId = 101L,
                title = "Series Manga",
                lastChapterRead = 5.0,
                totalChapters = 50,
            ),
        )

        val malTracker = TrackerTestFakeTracker(id = 1L, name = "MyAnimeList")
        malTracker.login(mapOf("username" to "MALUser", "password" to "pass"))
        val trackerManager = DesktopTrackerManager(listOf(malTracker))

        val syncService = TrackOnReadSyncService(
            repository = repo,
            mutationPort = repo,
            trackerManager = trackerManager,
            trackingQueue = null,
        )

        val synced = syncService.onChapterRead(mangaId, 6.0)
        assertEquals(1, synced)
        assertEquals(6.0, malTracker.updates.single().lastChapterRead)

        val updated = repo.trackingSnapshot(mangaId).single()
        assertEquals(6.0, updated.lastChapterRead)
        repo.close()
    }
}
