package mihon.sync.transport.http

import kotlinx.coroutines.runBlocking
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.Tombstone
import mihon.sync.engine.InMemorySyncStateStore
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.server.SqliteChangesetStore
import mihon.sync.server.SyncServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class HttpSyncIntegrationTest {

    private val token = "integration-test-token"
    private var serverPort = 0
    private var server: SyncServer? = null
    private var store: SqliteChangesetStore? = null

    @BeforeEach
    fun setUp() {
        serverPort = ServerSocket(0).use { it.localPort }
        val s = SqliteChangesetStore.inMemory()
        store = s
        val srv = SyncServer(
            host = "127.0.0.1",
            port = serverPort,
            token = token,
            store = s,
        )
        srv.start(wait = false)
        server = srv
    }

    @AfterEach
    fun tearDown() {
        server?.stop()
        store?.close()
    }

    private class TestLocalRepository : SyncLocalRepository {
        val mangas = mutableMapOf<String, AndroidBackupManga>()
        val chapters = mutableMapOf<String, MutableMap<String, AndroidBackupChapter>>()
        val locallyModified = mutableSetOf<String>()

        override suspend fun exportDelta(sinceCursor: Long): EntityDelta {
            val deltaMangas = mangas.values
                .filter { it.url in locallyModified && it.lastModifiedAt > sinceCursor }
                .map { manga ->
                    val mChaps = chapters[manga.url]?.values?.toList() ?: emptyList()
                    manga.copy(chapters = mChaps)
                }
            locallyModified.clear()
            return EntityDelta(mangas = deltaMangas)
        }

        override suspend fun exportTombstones(sinceCursor: Long): List<Tombstone> = emptyList()

        override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult {
            var mInserted = 0
            var mMerged = 0
            var cInserted = 0
            var cMerged = 0

            for (incoming in changeset.upserts.mangas) {
                val existing = mangas[incoming.url]
                if (existing == null) {
                    mangas[incoming.url] = incoming
                    mInserted++
                } else {
                    val merged = SyncMergePolicy.mergeManga(
                        existing = existing,
                        incoming = incoming,
                        existingDeviceId = "",
                        incomingDeviceId = changeset.deviceId,
                    )
                    mangas[incoming.url] = merged
                    mMerged++
                }

                val chapMap = chapters.computeIfAbsent(incoming.url) { mutableMapOf() }
                for (inChap in incoming.chapters) {
                    val existingChap = chapMap[inChap.url]
                    if (existingChap == null) {
                        chapMap[inChap.url] = inChap
                        cInserted++
                    } else {
                        val mergedChap = SyncMergePolicy.mergeChapter(
                            existing = existingChap,
                            incoming = inChap,
                            existingDeviceId = "",
                            incomingDeviceId = changeset.deviceId,
                        )
                        chapMap[inChap.url] = mergedChap
                        cMerged++
                    }
                }
            }

            return SyncApplyResult(
                mangaInserted = mInserted,
                mangaMerged = mMerged,
                chapterInserted = cInserted,
                chapterMerged = cMerged,
            )
        }
    }

    @Test
    fun `two SyncEngines converge over HttpTransport and SyncServer`() = runBlocking {
        val serverUrl = "http://127.0.0.1:$serverPort"

        // Setup Device A (desktop)
        val repoA = TestLocalRepository()
        val stateA = InMemorySyncStateStore("desktop-dev")
        val transportA = HttpTransport(serverUrl, token)
        var nowA = 10_000L
        val engineA = SyncEngine(repoA, transportA, stateA, clock = { nowA })

        // Setup Device B (phone)
        val repoB = TestLocalRepository()
        val stateB = InMemorySyncStateStore("phone-dev")
        val transportB = HttpTransport(serverUrl, token)
        var nowB = 10_000L
        val engineB = SyncEngine(repoB, transportB, stateB, clock = { nowB })

        // Step 1: Device A creates a manga with 2 chapters
        val ch1 = AndroidBackupChapter(
            url = "/ch1",
            name = "Chapter 1",
            read = false,
            lastPageRead = 0,
            lastModifiedAt = 10_000L,
        )
        val ch2 = AndroidBackupChapter(
            url = "/ch2",
            name = "Chapter 2",
            read = false,
            lastPageRead = 0,
            lastModifiedAt = 10_000L,
        )
        val manga1 = AndroidBackupManga(
            source = 1L,
            url = "/manga1",
            title = "Test Manga",
            favorite = true,
            lastModifiedAt = 10_000L,
            favoriteModifiedAt = 10_000L,
            chapters = listOf(ch1, ch2),
        )
        repoA.mangas[manga1.url] = manga1
        repoA.chapters[manga1.url] = mutableMapOf(ch1.url to ch1, ch2.url to ch2)
        repoA.locallyModified.add(manga1.url)

        // Device A syncs: should push changeset cursor 1
        val reportA1 = engineA.syncNow()
        assertTrue(reportA1.success)
        assertNotNull(reportA1.pushedChangeset)
        assertEquals(1L, reportA1.pushedChangeset?.cursor)

        // Step 2: Device B syncs: should pull changeset from Device A
        val reportB1 = engineB.syncNow()
        assertTrue(reportB1.success)
        assertEquals(1, reportB1.pulledChangesetCount)
        assertEquals(1, reportB1.mangaInserted)
        assertEquals(2, reportB1.chapterInserted)

        // Verify Device B has the manga and chapters
        val bManga = repoB.mangas["/manga1"]
        assertNotNull(bManga)
        assertEquals("Test Manga", bManga?.title)
        val bChaps = repoB.chapters["/manga1"]
        assertNotNull(bChaps)
        assertEquals(false, bChaps?.get("/ch1")?.read)

        // Step 3: Device B reads Chapter 1 and marks progress
        nowB = 15_000L
        val updatedCh1 = ch1.copy(
            read = true,
            lastPageRead = 24,
            lastModifiedAt = 15_000L,
        )
        val updatedManga = manga1.copy(
            lastModifiedAt = 15_000L,
            chapters = listOf(updatedCh1, ch2),
        )
        repoB.mangas[updatedManga.url] = updatedManga
        repoB.chapters[updatedManga.url]!!["/ch1"] = updatedCh1
        repoB.locallyModified.add(updatedManga.url)

        // Device B syncs: pushes update to server
        val reportB2 = engineB.syncNow()
        assertTrue(reportB2.success)
        assertNotNull(reportB2.pushedChangeset)
        assertEquals(1L, reportB2.pushedChangeset?.cursor)

        // Step 4: Device A syncs: pulls update from Device B
        nowA = 16_000L
        val reportA2 = engineA.syncNow()
        assertTrue(reportA2.success)
        assertEquals(1, reportA2.pulledChangesetCount)
        assertEquals(1, reportA2.mangaMerged)
        assertEquals(2, reportA2.chapterMerged)

        // Verify Device A now has Chapter 1 marked as read with page 24
        val aCh1 = repoA.chapters["/manga1"]?.get("/ch1")
        assertNotNull(aCh1)
        assertEquals(true, aCh1?.read)
        assertEquals(24, aCh1?.lastPageRead)

        // Step 5: Both sync again - completely idempotent (0 new items, no error)
        val reportA3 = engineA.syncNow()
        assertTrue(reportA3.success)
        assertEquals(0, reportA3.pulledChangesetCount)

        val reportB3 = engineB.syncNow()
        assertTrue(reportB3.success)
        assertEquals(0, reportB3.pulledChangesetCount)

        // Step 6: Verify full convergence
        assertEquals(repoA.mangas.keys, repoB.mangas.keys)
        assertEquals(repoA.chapters["/manga1"]?.get("/ch1")?.read, repoB.chapters["/manga1"]?.get("/ch1")?.read)
        assertEquals(
            repoA.chapters["/manga1"]?.get("/ch1")?.lastPageRead,
            repoB.chapters["/manga1"]?.get("/ch1")?.lastPageRead,
        )
    }

    @Test
    fun `engine recovers from 409 cursor gap after crash between nextCursor and push`() = runBlocking {
        val serverUrl = "http://127.0.0.1:$serverPort"
        val repo = TestLocalRepository()
        val state = InMemorySyncStateStore("desktop-dev")
        val transport = HttpTransport(serverUrl, token)
        var now = 10_000L
        val engine = SyncEngine(repo, transport, state, clock = { now })

        // First sync succeeds; the server now holds cursor 1 for this device.
        repo.mangas["/m1"] = AndroidBackupManga(source = 1L, url = "/m1", lastModifiedAt = 10_000L)
        repo.locallyModified.add("/m1")
        val report1 = engine.syncNow()
        assertTrue(report1.success)
        assertEquals(1L, report1.pushedChangeset?.cursor)

        // Simulate the crash window: a cursor is allocated and persisted locally, but the
        // process dies before the push reaches the server.
        state.nextCursor()

        // Next sync offers cursor 3 while the server still expects 2 -> 409 gap. The engine
        // must heal by re-pushing the identical delta under the gap-filling cursor 2.
        now = 20_000L
        repo.mangas["/m2"] = AndroidBackupManga(source = 1L, url = "/m2", lastModifiedAt = 20_000L)
        repo.locallyModified.add("/m2")
        val report2 = engine.syncNow()
        assertTrue(report2.success)
        assertEquals(2L, report2.pushedChangeset?.cursor)
        assertEquals("/m2", report2.pushedChangeset?.upserts?.mangas?.first()?.url)

        // A further sync is fully healthy again (no conflict loop).
        val report3 = engine.syncNow()
        assertTrue(report3.success)
        assertEquals(null, report3.pushedChangeset)

        transport.close()
    }
}
