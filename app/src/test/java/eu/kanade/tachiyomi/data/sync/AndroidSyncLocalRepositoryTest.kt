package eu.kanade.tachiyomi.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class AndroidSyncLocalRepositoryTest {

    private lateinit var database: Database
    private lateinit var repo: AndroidSyncLocalRepository

    @BeforeEach
    fun setup() = runBlocking {
        database = AndroidSyncTestHelper.createInMemoryDatabase()
        repo = AndroidSyncLocalRepository(database)
    }

    @Test
    fun `applyChangeset inserts new manga, chapters, tracking, and categories`() = runBlocking {
        val incomingManga = AndroidBackupManga(
            source = 100L,
            url = "/manga/one",
            title = "Test Manga One",
            author = "Author A",
            favorite = true,
            lastModifiedAt = 500L,
            version = 1L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch/1",
                    name = "Chapter 1",
                    read = true,
                    bookmark = true,
                    lastPageRead = 15L,
                    chapterNumber = 1f,
                    lastModifiedAt = 500L,
                    version = 1L,
                ),
            ),
            history = listOf(
                AndroidBackupHistory(
                    url = "/ch/1",
                    lastRead = 450L,
                    readDuration = 300L,
                ),
            ),
            tracking = listOf(
                AndroidBackupTracking(
                    syncId = 1,
                    libraryId = 0L,
                    title = "Remote Tracker Title",
                    lastChapterRead = 1f,
                    score = 9.5f,
                    status = 2,
                ),
            ),
            categories = listOf(1L),
        )

        val changeset = Changeset(
            deviceId = "device-remote",
            baseSchema = 1,
            cursor = 10L,
            producedAt = 1000L,
            upserts = EntityDelta(
                mangas = listOf(incomingManga),
                categories = listOf(AndroidBackupCategory(name = "Action", order = 1L, flags = 0L)),
            ),
        )

        val result = repo.applyChangeset(changeset)
        assertEquals(1, result.mangaInserted)
        assertEquals(1, result.chapterInserted)

        // Verify in DB
        val dbManga = database.mangasQueries.getMangaByUrlAndSource("/manga/one", 100L).awaitAsOne()
        assertEquals("Test Manga One", dbManga.title)
        assertEquals("Author A", dbManga.author)
        assertTrue(dbManga.favorite)

        val dbChapters = database.chaptersQueries.getChaptersByMangaId(dbManga._id, 0).awaitAsList()
        assertEquals(1, dbChapters.size)
        val chap = dbChapters.first()
        assertEquals("/ch/1", chap.url)
        assertTrue(chap.read)
        assertTrue(chap.bookmark)
        assertEquals(15L, chap.last_page_read)

        val dbHistory = database.historyQueries.getHistoryByMangaId(dbManga._id).awaitAsList()
        assertEquals(1, dbHistory.size)
        assertEquals(300L, dbHistory.first().time_read)

        val dbTracks = database.manga_syncQueries.getTracksByMangaId(dbManga._id).awaitAsList()
        assertEquals(1, dbTracks.size)
        assertEquals(1L, dbTracks.first().sync_id)
        assertEquals(9.5, dbTracks.first().score, 0.01)
    }

    @Test
    fun `applyChangeset merges existing manga and chapters using LWW policy`() = runBlocking {
        // First insert an existing manga with watermark 100
        val mangaId = database.mangasQueries.insertReturningId(
            source = 100L,
            url = "/manga/two",
            artist = "Local Artist",
            author = "Local Author",
            description = "Local Description",
            genre = listOf("Shounen"),
            title = "Manga Two",
            status = 1L,
            thumbnailUrl = null,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 100L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 1L,
            notes = "",
            memo = JsonObject(emptyMap()),
        ).awaitAsOne()
        database.mangasQueries.touchMangaLastModified(100L, mangaId)

        val chapId = database.chaptersQueries.insertReturningId(
            mangaId = mangaId,
            url = "/ch/1",
            name = "Chapter 1",
            scanlator = "Scanlator",
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = 1.0,
            sourceOrder = 1L,
            dateFetch = 100L,
            dateUpload = 100L,
            version = 1L,
            memo = JsonObject(emptyMap()),
        ).awaitAsOne()
        database.chaptersQueries.touchChapterLastModified(100L, chapId)

        // Incoming changeset with newer watermark (200L) marks favorite=true, read=true
        val incomingManga = AndroidBackupManga(
            source = 100L,
            url = "/manga/two",
            title = "Manga Two Remote",
            favorite = true,
            lastModifiedAt = 200L,
            version = 2L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch/1",
                    name = "Chapter 1",
                    read = true,
                    bookmark = true,
                    lastPageRead = 20L,
                    chapterNumber = 1f,
                    lastModifiedAt = 200L,
                    version = 2L,
                ),
            ),
        )

        val changeset = Changeset(
            deviceId = "device-z",
            baseSchema = 1,
            cursor = 5L,
            producedAt = 2000L,
            upserts = EntityDelta(mangas = listOf(incomingManga)),
        )

        val result = repo.applyChangeset(changeset)
        assertEquals(0, result.mangaInserted)
        assertEquals(1, result.mangaMerged)
        assertEquals(0, result.chapterInserted)
        assertEquals(1, result.chapterMerged)

        // Verify updated state
        val updatedManga = database.mangasQueries.getMangaByUrlAndSource("/manga/two", 100L).awaitAsOne()
        assertTrue(updatedManga.favorite)
        assertEquals(200L, updatedManga.last_modified_at)

        val updatedChap = database.chaptersQueries.getChapterByUrlAndMangaId("/ch/1", mangaId).awaitAsOne()
        assertTrue(updatedChap.read)
        assertTrue(updatedChap.bookmark)
        assertEquals(20L, updatedChap.last_page_read)
        assertEquals(200L, updatedChap.last_modified_at)
    }

    @Test
    fun `applyChangeset is idempotent when applied repeatedly`() = runBlocking {
        val incomingManga = AndroidBackupManga(
            source = 100L,
            url = "/manga/idem",
            title = "Idempotent Manga",
            favorite = true,
            lastModifiedAt = 300L,
            version = 1L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch/1",
                    name = "Ch 1",
                    read = true,
                    lastPageRead = 5L,
                    lastModifiedAt = 300L,
                    version = 1L,
                ),
            ),
        )

        val changeset = Changeset(
            deviceId = "device-test",
            baseSchema = 1,
            cursor = 1L,
            producedAt = 1000L,
            upserts = EntityDelta(mangas = listOf(incomingManga)),
        )

        // First application
        val res1 = repo.applyChangeset(changeset)
        assertEquals(1, res1.mangaInserted)
        assertEquals(1, res1.chapterInserted)

        // Second application of same changeset
        val res2 = repo.applyChangeset(changeset)
        assertEquals(0, res2.mangaInserted)
        assertEquals(1, res2.mangaMerged)
        assertEquals(0, res2.chapterInserted)
        assertEquals(1, res2.chapterMerged)

        // Verify state is completely unchanged
        val dbManga = database.mangasQueries.getMangaByUrlAndSource("/manga/idem", 100L).awaitAsOne()
        assertTrue(dbManga.favorite)
        assertEquals(300L, dbManga.last_modified_at)

        val dbChap = database.chaptersQueries.getChapterByUrlAndMangaId("/ch/1", dbManga._id).awaitAsOne()
        assertTrue(dbChap.read)
        assertEquals(5L, dbChap.last_page_read)
        assertEquals(300L, dbChap.last_modified_at)
    }

    @Test
    fun `exportDelta exports items modified since watermark and excludes LocalSource`() = runBlocking {
        // Insert standard manga with watermark 50
        val manga1 = database.mangasQueries.insertReturningId(
            source = 100L,
            url = "/manga/remote1",
            artist = null,
            author = null,
            description = null,
            genre = null,
            title = "Remote Manga 1",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 1L,
            notes = "",
            memo = JsonObject(emptyMap()),
        ).awaitAsOne()
        database.mangasQueries.touchMangaLastModified(50L, manga1)

        // Insert manga with watermark 150
        val manga2 = database.mangasQueries.insertReturningId(
            source = 200L,
            url = "/manga/remote2",
            artist = null,
            author = null,
            description = null,
            genre = null,
            title = "Remote Manga 2",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 1L,
            notes = "",
            memo = JsonObject(emptyMap()),
        ).awaitAsOne()
        database.mangasQueries.touchMangaLastModified(150L, manga2)

        // Insert local manga (source 0) with watermark 200
        val mangaLocal = database.mangasQueries.insertReturningId(
            source = 0L,
            url = "/local/manga",
            artist = null,
            author = null,
            description = null,
            genre = null,
            title = "Local Manga",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 1L,
            notes = "",
            memo = JsonObject(emptyMap()),
        ).awaitAsOne()
        database.mangasQueries.touchMangaLastModified(200L, mangaLocal)

        // Export since 0 -> should include remote1 and remote2, but NOT local
        val delta0 = repo.exportDelta(sinceCursor = 0L)
        val urls0 = delta0.mangas.map { it.url }
        assertEquals(listOf("/manga/remote1", "/manga/remote2"), urls0)

        // Export since 100 -> should only include remote2
        val delta100 = repo.exportDelta(sinceCursor = 100L)
        val urls100 = delta100.mangas.map { it.url }
        assertEquals(listOf("/manga/remote2"), urls100)

        // Export since 250 -> empty
        val delta250 = repo.exportDelta(sinceCursor = 250L)
        assertTrue(delta250.mangas.isEmpty())
    }

    @Test
    fun `history sync is idempotent and takes max time_read and last_read`() = runBlocking {
        val incomingManga = AndroidBackupManga(
            source = 100L,
            url = "/manga/hist-test",
            title = "History Test Manga",
            lastModifiedAt = 1000L,
            chapters = listOf(
                AndroidBackupChapter(
                    url = "/ch/1",
                    name = "Chapter 1",
                    lastModifiedAt = 1000L,
                ),
            ),
            history = listOf(
                AndroidBackupHistory(
                    url = "/ch/1",
                    lastRead = 1000L,
                    readDuration = 500L,
                ),
            ),
        )

        val cs1 = Changeset(
            deviceId = "device-A",
            baseSchema = 1,
            cursor = 1L,
            producedAt = 1000L,
            upserts = EntityDelta(mangas = listOf(incomingManga)),
        )

        repo.applyChangeset(cs1)

        val dbManga = database.mangasQueries.getMangaByUrlAndSource("/manga/hist-test", 100L).awaitAsOne()
        val h1 = database.historyQueries.getHistoryByMangaId(dbManga._id).awaitAsList().first()
        assertEquals(500L, h1.time_read)
        assertEquals(1000L, h1.last_read?.time)

        // Apply older history: should NOT regress lastRead, and should NOT double or decrease time_read
        val olderHistoryManga = incomingManga.copy(
            history = listOf(
                AndroidBackupHistory(
                    url = "/ch/1",
                    lastRead = 800L,
                    readDuration = 300L,
                ),
            ),
        )
        val cs2 = cs1.copy(cursor = 2L, upserts = EntityDelta(mangas = listOf(olderHistoryManga)))
        repo.applyChangeset(cs2)

        val h2 = database.historyQueries.getHistoryByMangaId(dbManga._id).awaitAsList().first()
        assertEquals(500L, h2.time_read)
        assertEquals(1000L, h2.last_read?.time)

        // Apply newer history with larger duration
        val newerHistoryManga = incomingManga.copy(
            history = listOf(
                AndroidBackupHistory(
                    url = "/ch/1",
                    lastRead = 1200L,
                    readDuration = 800L,
                ),
            ),
        )
        val cs3 = cs1.copy(cursor = 3L, upserts = EntityDelta(mangas = listOf(newerHistoryManga)))
        repo.applyChangeset(cs3)

        val h3 = database.historyQueries.getHistoryByMangaId(dbManga._id).awaitAsList().first()
        assertEquals(800L, h3.time_read)
        assertEquals(1200L, h3.last_read?.time)
    }

    @Test
    fun `symmetric tie-breaking uses origin deviceId from memo`() = runBlocking {
        // Device A inserts manga at watermark 500
        val memoBytesA = """{"sync_device_id":"device-A"}""".encodeToByteArray()
        val mangaA = AndroidBackupManga(
            source = 100L,
            url = "/manga/tie-break",
            title = "Title from Device A",
            lastModifiedAt = 500L,
            memo = memoBytesA,
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-A",
                baseSchema = 1,
                cursor = 1L,
                producedAt = 500L,
                upserts = EntityDelta(mangas = listOf(mangaA)),
            ),
        )

        val dbManga1 = database.mangasQueries.getMangaByUrlAndSource("/manga/tie-break", 100L).awaitAsOne()
        assertEquals("Title from Device A", dbManga1.title)

        // Incoming from device-B with identical watermark 500
        // Because "device-B" > "device-A", device-B should WIN
        val memoBytesB = """{"sync_device_id":"device-B"}""".encodeToByteArray()
        val mangaB = AndroidBackupManga(
            source = 100L,
            url = "/manga/tie-break",
            title = "Title from Device B",
            lastModifiedAt = 500L,
            memo = memoBytesB,
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-B",
                baseSchema = 1,
                cursor = 1L,
                producedAt = 500L,
                upserts = EntityDelta(mangas = listOf(mangaB)),
            ),
        )

        val dbManga2 = database.mangasQueries.getMangaByUrlAndSource("/manga/tie-break", 100L).awaitAsOne()
        assertEquals("Title from Device B", dbManga2.title)

        // Incoming again from device-A with identical watermark 500
        // Because "device-A" < "device-B", device-A should LOSE, title remains from B
        repo.applyChangeset(
            Changeset(
                deviceId = "device-A",
                baseSchema = 1,
                cursor = 2L,
                producedAt = 500L,
                upserts = EntityDelta(mangas = listOf(mangaA)),
            ),
        )

        val dbManga3 = database.mangasQueries.getMangaByUrlAndSource("/manga/tie-break", 100L).awaitAsOne()
        assertEquals("Title from Device B", dbManga3.title)
    }

    @Test
    fun `favorite uses LWW on favoriteModifiedAt with deviceId tie-breaking`() = runBlocking {
        val mangaFavTrue = AndroidBackupManga(
            source = 100L,
            url = "/manga/fav-lww",
            title = "Fav LWW",
            favorite = true,
            lastModifiedAt = 500L,
            favoriteModifiedAt = 200L,
            memo = """{"sync_device_id":"device-B"}""".encodeToByteArray(),
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-B",
                baseSchema = 1,
                cursor = 1L,
                producedAt = 500L,
                upserts = EntityDelta(mangas = listOf(mangaFavTrue)),
            ),
        )

        val m1 = database.mangasQueries.getMangaByUrlAndSource("/manga/fav-lww", 100L).awaitAsOne()
        assertTrue(m1.favorite)

        // Older favoriteModifiedAt (100L < 200L) with favorite=false should NOT un-favorite
        val mangaFavFalseOlder = mangaFavTrue.copy(
            favorite = false,
            favoriteModifiedAt = 100L,
            lastModifiedAt = 600L,
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-A",
                baseSchema = 1,
                cursor = 2L,
                producedAt = 600L,
                upserts = EntityDelta(mangas = listOf(mangaFavFalseOlder)),
            ),
        )

        val m2 = database.mangasQueries.getMangaByUrlAndSource("/manga/fav-lww", 100L).awaitAsOne()
        assertTrue(m2.favorite)

        // Newer favoriteModifiedAt (300L > 200L) with favorite=false SHOULD un-favorite
        val mangaFavFalseNewer = mangaFavTrue.copy(
            favorite = false,
            favoriteModifiedAt = 300L,
            lastModifiedAt = 700L,
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-A",
                baseSchema = 1,
                cursor = 3L,
                producedAt = 700L,
                upserts = EntityDelta(mangas = listOf(mangaFavFalseNewer)),
            ),
        )

        val m3 = database.mangasQueries.getMangaByUrlAndSource("/manga/fav-lww", 100L).awaitAsOne()
        assertFalse(m3.favorite)
    }

    @Test
    fun `categories are linked to manga on import`() = runBlocking {
        val cat = AndroidBackupCategory(name = "Sci-Fi", order = 1L, flags = 0L)
        val manga = AndroidBackupManga(
            source = 100L,
            url = "/manga/cat-link",
            title = "Sci-Fi Manga",
            lastModifiedAt = 500L,
            categories = listOf(1L),
        )
        repo.applyChangeset(
            Changeset(
                deviceId = "device-remote",
                baseSchema = 1,
                cursor = 1L,
                producedAt = 500L,
                upserts = EntityDelta(
                    mangas = listOf(manga),
                    categories = listOf(cat),
                ),
            ),
        )

        val dbManga = database.mangasQueries.getMangaByUrlAndSource("/manga/cat-link", 100L).awaitAsOne()
        val linkedCategories = database.categoriesQueries.getCategoriesByMangaId(dbManga._id).awaitAsList()
        assertEquals(1, linkedCategories.size)
        assertEquals("Sci-Fi", linkedCategories.first().name)
    }
}
