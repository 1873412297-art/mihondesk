package mihon.sync.engine

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.Tombstone
import mihon.sync.transport.api.SyncCursorConflictException
import mihon.sync.transport.api.SyncTransport
import org.junit.jupiter.api.Test

/**
 * Regression for "Cursor gap detected: expected 222, got 787": the local cursor history had
 * diverged far from the server's (e.g. the server's changeset store was recreated), so neither
 * "last acked + 1" nor a fresh clock value could ever be accepted and auto-sync failed forever.
 */
class SyncEngineCursorRecoveryTest {
    /** Emulates the sync server's monotonic per-device cursor validation. */
    private class StrictServer(var head: Long, private val advertiseExpected: Boolean = true) : SyncTransport {
        val accepted = mutableListOf<Long>()

        override suspend fun push(changeset: Changeset) {
            if (changeset.cursor != head + 1) {
                throw SyncCursorConflictException(
                    "Cursor gap detected: expected ${head + 1}, got ${changeset.cursor}",
                    expectedCursor = if (advertiseExpected) head + 1 else null,
                )
            }
            head = changeset.cursor
            accepted += changeset.cursor
        }

        override suspend fun pull(sinceCursors: Map<String, Long>, excludeDeviceId: String?) = emptyList<Changeset>()
        override suspend fun headCursor(excludeDeviceId: String?) = 0L
    }

    private class DirtyRepository : SyncLocalRepository {
        var modifiedAt = 1_000L
        override suspend fun exportDelta(sinceCursor: Long): EntityDelta =
            if (modifiedAt > sinceCursor) {
                EntityDelta(mangas = listOf(AndroidBackupManga(source = 1, url = "/m", lastModifiedAt = modifiedAt)))
            } else {
                EntityDelta()
            }
        override suspend fun exportTombstones(sinceCursor: Long): List<Tombstone> = emptyList()
        override suspend fun applyChangeset(changeset: Changeset) = SyncApplyResult()
    }

    @Test
    fun `push realigns to the cursor the server expects and stays aligned afterwards`() = runBlocking {
        val server = StrictServer(head = 221)
        val state = InMemorySyncStateStore("desktop", lastPushCursor = 785, currentCursor = 786)
        val repo = DirtyRepository()
        val engine = SyncEngine(repo, server, state)

        val first = engine.syncNow()
        first.success shouldBe true
        first.pushedChangeset?.cursor shouldBe 222L
        state.getLastPushCursor() shouldBe 222L

        repo.modifiedAt = 2_000L
        val second = engine.syncNow()
        second.success shouldBe true
        second.pushedChangeset?.cursor shouldBe 223L
        server.accepted shouldBe listOf(222L, 223L)
    }

    @Test
    fun `servers that do not advertise the expected cursor keep the previous recovery`() = runBlocking {
        val server = StrictServer(head = 4, advertiseExpected = false)
        // Classic crash window: clock persisted one ahead of the last acked push.
        val state = InMemorySyncStateStore("desktop", lastPushCursor = 4, currentCursor = 5)
        val report = SyncEngine(DirtyRepository(), server, state).syncNow()
        report.success shouldBe true
        report.pushedChangeset?.cursor shouldBe 5L
    }
}
