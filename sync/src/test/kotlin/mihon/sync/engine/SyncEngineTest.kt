package mihon.sync.engine

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import mihon.sync.transport.api.SyncTransport
import org.junit.jupiter.api.Test

class SyncEngineTest {

    private class FakeTransport(
        val pulledChangesets: MutableList<Changeset> = mutableListOf(),
        val pushedChangesets: MutableList<Changeset> = mutableListOf(),
    ) : SyncTransport {
        override suspend fun push(changeset: Changeset) {
            pushedChangesets.add(changeset)
        }

        override suspend fun pull(
            sinceCursors: Map<String, Long>,
            excludeDeviceId: String?,
        ): List<Changeset> {
            return pulledChangesets.filter {
                val minCursor = sinceCursors[it.deviceId] ?: 0L
                it.deviceId != excludeDeviceId && it.cursor > minCursor
            }
        }

        override suspend fun pull(sinceCursor: Long, excludeDeviceId: String?): List<Changeset> {
            return pulledChangesets.filter { it.deviceId != excludeDeviceId && it.cursor > sinceCursor }
        }

        override suspend fun headCursor(excludeDeviceId: String?): Long {
            return pulledChangesets.filter { it.deviceId != excludeDeviceId }.maxOfOrNull { it.cursor } ?: 0L
        }
    }

    private class FakeLocalRepository : SyncLocalRepository {
        var localDelta = EntityDelta()
        val applied = mutableListOf<Changeset>()

        override suspend fun exportDelta(sinceCursor: Long): EntityDelta = localDelta

        override suspend fun exportTombstones(sinceCursor: Long): List<Tombstone> = emptyList()

        override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult {
            applied.add(changeset)
            return SyncApplyResult(
                mangaInserted = changeset.upserts.mangas.size,
                mangaMerged = 0,
                chapterInserted = 0,
                chapterMerged = 0,
                categoriesLinked = 0,
                overriddenItems = listOf(
                    SyncOverriddenItem(
                        entityType = EntityType.MANGA,
                        entityKey = "1:/manga/remote",
                        localWatermark = 50L,
                        remoteWatermark = 100L,
                        reason = "Remote is newer",
                    ),
                ),
            )
        }
    }

    @Test
    fun `syncNow pulls remote changes, updates cursor, and pushes local changes`(): Unit = runBlocking {
        val stateStore = InMemorySyncStateStore("local-dev")
        val transport = FakeTransport()
        val repository = FakeLocalRepository()

        // Remote has 1 changeset
        val remoteCs = Changeset(
            deviceId = "remote-dev",
            cursor = 5L,
            producedAt = 1000L,
            upserts = EntityDelta(
                mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/remote")),
            ),
        )
        transport.pulledChangesets.add(remoteCs)

        // Local has 1 manga to push
        repository.localDelta = EntityDelta(
            mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/local", lastModifiedAt = 1774480000000L)),
        )

        val engine = SyncEngine(repository, transport, stateStore)
        val report = engine.syncNow()

        report.success shouldBe true
        report.pulledChangesetCount shouldBe 1
        report.mangaInserted shouldBe 1
        report.overriddenItems.size shouldBe 1
        report.pushedChangeset?.cursor shouldBe 1L
        report.pushedChangeset?.upserts?.mangas?.first()?.url shouldBe "/manga/local"

        stateStore.getLastPullWatermark("remote-dev") shouldBe 5L
        stateStore.getLastPushCursor() shouldBe 1L
        stateStore.getLastPushWatermark() shouldBe 1774480000000L
        transport.pushedChangesets.size shouldBe 1
        repository.applied.size shouldBe 1
    }

    @Test
    fun `syncNow pulls multi-device peer changesets with separate watermarks`(): Unit = runBlocking {
        val stateStore = InMemorySyncStateStore("local-dev")
        val transport = FakeTransport()
        val repository = FakeLocalRepository()

        // Peer A is at cursor 20, Peer B is at cursor 1
        transport.pulledChangesets.add(
            Changeset(deviceId = "peerA", cursor = 20L, producedAt = 1000L, upserts = EntityDelta()),
        )
        transport.pulledChangesets.add(
            Changeset(deviceId = "peerB", cursor = 1L, producedAt = 1001L, upserts = EntityDelta()),
        )

        val engine = SyncEngine(repository, transport, stateStore)
        val report = engine.syncNow()

        report.pulledChangesetCount shouldBe 2
        stateStore.getLastPullWatermark("peerA") shouldBe 20L
        stateStore.getLastPullWatermark("peerB") shouldBe 1L

        // Next sync with peerB pushing cursor 2 should only pull peerB.2.pb
        transport.pulledChangesets.add(
            Changeset(deviceId = "peerB", cursor = 2L, producedAt = 1002L, upserts = EntityDelta()),
        )
        val report2 = engine.syncNow()
        report2.pulledChangesetCount shouldBe 1
        stateStore.getLastPullWatermark("peerB") shouldBe 2L
    }

    @Test
    fun `syncNow does not push or advance watermark if there are no local changes`(): Unit = runBlocking {
        val stateStore = InMemorySyncStateStore("local-dev")
        val transport = FakeTransport()
        val repository = FakeLocalRepository()
        repository.localDelta = EntityDelta() // empty

        val engine = SyncEngine(repository, transport, stateStore)
        val report = engine.syncNow()

        report.pushedChangeset shouldBe null
        transport.pushedChangesets shouldBe emptyList()
        stateStore.getLastPushCursor() shouldBe 0L
        stateStore.getLastPushWatermark() shouldBe 0L
    }

    private class ScriptedPushTransport(
        private val onPush: (Changeset) -> Unit,
    ) : SyncTransport {
        val pushedChangesets = mutableListOf<Changeset>()

        override suspend fun push(changeset: Changeset) {
            pushedChangesets.add(changeset)
            onPush(changeset)
        }

        override suspend fun pull(sinceCursors: Map<String, Long>, excludeDeviceId: String?): List<Changeset> =
            emptyList()

        override suspend fun pull(sinceCursor: Long, excludeDeviceId: String?): List<Changeset> = emptyList()

        override suspend fun headCursor(excludeDeviceId: String?): Long = 0L
    }

    @Test
    fun `syncNow self-heals crash between nextCursor and push when server is one cursor behind`(): Unit =
        runBlocking {
            // Crash window: nextCursor() persisted cursor 5 locally, but the process died before
            // the push reached the server, so the server still expects cursor 5 while the next
            // push offers 6 (gap 409).
            val stateStore = InMemorySyncStateStore(
                deviceId = "local-dev",
                lastPushCursor = 4L,
                currentCursor = 5L,
            )
            val repository = FakeLocalRepository()
            repository.localDelta = EntityDelta(
                mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/local", lastModifiedAt = 2000L)),
            )

            var serverLastCursor = 4L
            val transport = ScriptedPushTransport { changeset ->
                when {
                    changeset.cursor == serverLastCursor -> Unit // identical-payload replay: accepted (204)
                    changeset.cursor == serverLastCursor + 1 -> serverLastCursor = changeset.cursor
                    else -> throw mihon.sync.transport.api.SyncCursorConflictException(
                        "Cursor gap detected: expected ${serverLastCursor + 1}, got ${changeset.cursor}",
                    )
                }
            }

            val engine = SyncEngine(repository, transport, stateStore)
            val report = engine.syncNow()

            report.success shouldBe true
            // First attempt (cursor 6) is rejected; recovery retries with the gap-filling cursor 5.
            transport.pushedChangesets.map { it.cursor } shouldBe listOf(6L, 5L)
            // The locally persisted changes were not lost in the retry.
            transport.pushedChangesets.last().upserts.mangas.first().url shouldBe "/manga/local"
            stateStore.getLastPushCursor() shouldBe 5L
            stateStore.getLastPushWatermark() shouldBe 2000L
            report.pushedChangeset?.cursor shouldBe 5L
        }

    @Test
    fun `syncNow stays healthy after crash between ack and persisting the push cursor`(): Unit =
        runBlocking {
            // Crash window: the push of cursor 5 was acked by the server, but the process died
            // before setLastPushCursor/setLastPushWatermark persisted. The server already holds
            // cursor 5 with our earlier payload; the local watermark still points at the same
            // delta, which must be re-pushed under a fresh cursor without data loss or failure.
            val stateStore = InMemorySyncStateStore(
                deviceId = "local-dev",
                lastPushCursor = 4L,
                currentCursor = 5L,
            )
            val repository = FakeLocalRepository()
            repository.localDelta = EntityDelta(
                mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/local", lastModifiedAt = 2000L)),
            )

            val storedPayloads = mutableMapOf<Long, String>()
            var serverLastCursor = 5L
            storedPayloads[5L] = "/manga/local"
            val transport = ScriptedPushTransport { changeset ->
                val key = changeset.upserts.mangas.firstOrNull()?.url
                when {
                    changeset.cursor == serverLastCursor && storedPayloads[changeset.cursor] == key -> Unit
                    changeset.cursor == serverLastCursor + 1 -> {
                        serverLastCursor = changeset.cursor
                        storedPayloads[changeset.cursor] = key ?: ""
                    }
                    else -> throw mihon.sync.transport.api.SyncCursorConflictException(
                        "Cursor conflict (current head: $serverLastCursor)",
                    )
                }
            }

            val engine = SyncEngine(repository, transport, stateStore)
            val report = engine.syncNow()

            report.success shouldBe true
            stateStore.getLastPushCursor() shouldBe 6L
            stateStore.getLastPushWatermark() shouldBe 2000L
            transport.pushedChangesets.last().upserts.mangas.first().url shouldBe "/manga/local"
        }

    @Test
    fun `syncNow gives up after a bounded number of unresolved cursor conflicts`(): Unit = runBlocking {
        val stateStore = InMemorySyncStateStore("local-dev")
        val repository = FakeLocalRepository()
        repository.localDelta = EntityDelta(
            mangas = listOf(AndroidBackupManga(source = 1L, url = "/manga/local", lastModifiedAt = 2000L)),
        )
        val transport = ScriptedPushTransport {
            throw mihon.sync.transport.api.SyncCursorConflictException("unresolvable divergence")
        }

        val engine = SyncEngine(repository, transport, stateStore)
        val report = engine.syncNow()

        report.success shouldBe false
        transport.pushedChangesets.size shouldBe SyncEngine.MAX_PUSH_ATTEMPTS
    }
}
