package mihon.sync.transport.gdrive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import mihon.sync.transport.api.SyncCursorConflictException
import org.junit.jupiter.api.Test

class FakeSnapshotStore : DriveSnapshotStore {
    var bytes: ByteArray? = null
    var writes = 0

    override suspend fun read(): ByteArray? = bytes

    override suspend fun write(data: ByteArray) {
        writes++
        bytes = data
    }
}

private fun manga(url: String, modified: Long, chapters: List<AndroidBackupChapter> = emptyList()) =
    AndroidBackupManga(source = 1, url = url, title = url, lastModifiedAt = modified, chapters = chapters)

private fun chapter(url: String, modified: Long, read: Boolean = false, page: Long = 0) =
    AndroidBackupChapter(url = url, name = url, read = read, lastPageRead = page, lastModifiedAt = modified)

private fun cs(
    device: String,
    cursor: Long,
    vararg mangas: AndroidBackupManga,
    tombstones: List<Tombstone> = emptyList(),
) = Changeset(
    deviceId = device,
    cursor = cursor,
    producedAt = cursor,
    upserts = EntityDelta(mangas = mangas.toList()),
    tombstones = tombstones,
)

class GoogleDriveTransportTest {
    @Test
    fun `push then pull from another device`(): Unit = runBlocking {
        val store = FakeSnapshotStore()
        val desktop = GoogleDriveTransport(store)
        desktop.push(cs("desktop", 1, manga("/a", 10)))
        val phone = GoogleDriveTransport(store)
        val pulled = phone.pull(emptyMap(), excludeDeviceId = "phone")
        pulled shouldHaveSize 1
        pulled.single().upserts.mangas.single().url shouldBe "/a"
        phone.pull(emptyMap(), excludeDeviceId = "desktop") shouldHaveSize 0
    }

    @Test
    fun `pull honours per-device cursors and head cursor`(): Unit = runBlocking {
        val t = GoogleDriveTransport(FakeSnapshotStore())
        t.push(cs("a", 1, manga("/1", 1)))
        t.push(cs("a", 2, manga("/2", 2)))
        t.push(cs("b", 1, manga("/3", 3)))
        t.pull(mapOf("a" to 1L)).map { it.deviceId to it.cursor }.toSet() shouldBe setOf("a" to 2L, "b" to 1L)
        t.headCursor(excludeDeviceId = "b") shouldBe 2L
        t.headCursor() shouldBe 2L
    }

    @Test
    fun `identical replay is idempotent and divergent cursor reuse conflicts`(): Unit = runBlocking {
        val store = FakeSnapshotStore()
        val t = GoogleDriveTransport(store)
        val first = cs("a", 1, manga("/1", 1))
        t.push(first)
        t.push(first)
        t.pull() shouldHaveSize 1
        shouldThrow<SyncCursorConflictException> { t.push(cs("a", 1, manga("/other", 5))) }
        shouldThrow<SyncCursorConflictException> { t.push(cs("a", 0, manga("/old", 5))) }
    }

    @Test
    fun `compaction merges old changesets with per-item last-modified wins`(): Unit = runBlocking {
        val t = GoogleDriveTransport(FakeSnapshotStore(), maxChangesetsPerDevice = 2)
        t.push(cs("a", 1, manga("/m", 10, listOf(chapter("/c1", 10, read = true), chapter("/c2", 10, page = 3)))))
        t.push(cs("a", 2, manga("/m", 5, listOf(chapter("/c1", 5, read = false), chapter("/c2", 20, page = 9)))))
        t.push(cs("a", 3, manga("/n", 1)))
        val all = t.pull()
        all shouldHaveSize 2
        val merged = all.first()
        merged.cursor shouldBe 2L
        val m = merged.upserts.mangas.single()
        m.lastModifiedAt shouldBe 10L
        m.chapters.first { it.url == "/c1" }.read shouldBe true
        m.chapters.first { it.url == "/c2" }.lastPageRead shouldBe 9L
        t.pull(mapOf("a" to 2L)).single().cursor shouldBe 3L
    }

    @Test
    fun `history merge keeps latest read and tombstones keep latest delete`() {
        val a = cs(
            "a",
            1,
            manga("/m", 1).copy(history = listOf(AndroidBackupHistory("/c", lastRead = 100))),
            tombstones = listOf(Tombstone(EntityType.MANGA, "1:/x", 5)),
        )
        val b = cs(
            "a",
            2,
            manga("/m", 1).copy(history = listOf(AndroidBackupHistory("/c", lastRead = 50))),
            tombstones = listOf(Tombstone(EntityType.MANGA, "1:/x", 9)),
        )
        val merged = ChangesetCompactor.merge(listOf(a, b))
        merged.upserts.mangas.single().history.single().lastRead shouldBe 100L
        merged.tombstones.single().deletedAt shouldBe 9L
        merged.cursor shouldBe 2L
    }

    @Test
    fun `corrupt snapshot surfaces as error instead of being overwritten`(): Unit = runBlocking {
        val store = FakeSnapshotStore().apply { bytes = byteArrayOf(9, 9, 9, 9) }
        shouldThrow<DriveSnapshotCorruptException> { GoogleDriveTransport(store).pull() }
        store.writes shouldBe 0
    }

    @Test
    fun `devices write independent documents so a slow push cannot delete a peer changeset`(): Unit = runBlocking {
        val backend = MultiDocumentStore()
        val desktop = GoogleDriveTransport(backend)
        val phone = GoogleDriveTransport(backend)
        desktop.push(cs("desktop", 1, manga("/d1", 1)))
        phone.push(cs("phone", 1, manga("/p1", 1)))
        desktop.push(cs("desktop", 2, manga("/d2", 2)))

        desktop.pull(excludeDeviceId = "desktop").map { it.deviceId to it.cursor } shouldBe listOf("phone" to 1L)
        phone.pull(excludeDeviceId = "phone").map { it.deviceId to it.cursor }.toSet() shouldBe
            setOf("desktop" to 1L, "desktop" to 2L)
    }
}

/** Backend where each device id maps to its own document, like the production Drive store. */
private class MultiDocumentStore(
    private val documents: MutableMap<String, ByteArray> = mutableMapOf(),
    private val document: String = "legacy",
) : DriveSnapshotStore {
    override suspend fun read(): ByteArray? = documents[document]
    override suspend fun write(data: ByteArray) {
        documents[document] = data
    }

    override suspend fun readAll(): List<ByteArray> = documents.values.toList()
    override fun deviceStore(deviceId: String): DriveSnapshotStore = MultiDocumentStore(documents, deviceId)
}
