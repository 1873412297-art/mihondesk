package mihon.companion.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.MangaKey
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncReport
import mihon.sync.transport.gdrive.GoogleDriveTransport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Two phone-style peers syncing through the real GoogleDriveTransport over an in-memory Drive. */
class DriveRoundTripTest {
    @TempDir
    lateinit var dir: File

    private val drive = InMemoryDrive()
    private val key = MangaKey(42, "/a")

    private inner class Peer(name: String) {
        val store = LibraryStore(File(dir, "$name/library.pb"), Dispatchers.Unconfined)
        val state = FileSyncStateStore(File(dir, "$name/state.json"))
        private val repository = CompanionSyncRepository(store) { state.getDeviceId() }

        suspend fun sync(): SyncReport {
            val report = SyncEngine(repository, GoogleDriveTransport(drive), state).syncNow()
            if (report.success && report.pushedChangeset != null) store.acknowledge(repository.exportedSeq)
            return report
        }
    }

    @Test
    fun `imported backup reaches the other device and read marks come back`(): Unit = runBlocking {
        val phone = Peer("phone")
        val desktop = Peer("desktop")

        // Old Mihon timestamps must still be pushed: export is driven by dirty seq, not by time.
        val backup = IncomingLibrary(
            mangas = listOf(
                manga("/a", modified = 10, chapters = listOf(chapter("c1"), chapter("c2")), categories = listOf(7)),
            ),
            categories = listOf(category("Reading", 7)),
            deviceId = phone.state.getDeviceId(),
        )
        phone.store.update { LibraryMerge.merge(it, backup, markDirty = true) }

        phone.sync().pushedChangeset shouldNotBe null
        phone.store.load().pendingChanges shouldBe 0

        desktop.sync().mangaInserted shouldBe 1
        val received = desktop.store.load().mangas.single()
        received.categoryNames shouldBe listOf("Reading")

        desktop.store.edit {
            LibraryEdits.setChaptersRead(it, key, setOf("c2"), true, now = 50_000, deviceId = "desktop")
        }
        desktop.sync().pushedChangeset shouldNotBe null

        phone.sync().success shouldBe true
        phone.store.load().mangas.single().manga.chapters.first { it.url == "c2" }.read shouldBe true
    }

    @Test
    fun `pulled data is not echoed back`(): Unit = runBlocking {
        val a = Peer("a")
        val b = Peer("b")
        a.store.update {
            LibraryMerge.merge(it, IncomingLibrary(listOf(manga("/a")), emptyList(), deviceId = "a"), markDirty = true)
        }
        a.sync()

        b.sync().pulledChangesetCount shouldBe 1
        b.sync().pushedChangeset shouldBe null
        a.sync().pushedChangeset shouldBe null
    }
}
