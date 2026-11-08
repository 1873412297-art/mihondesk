package mihon.desktop.library.sync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.MangaRecord
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * End-to-end convergence checks against the real SQLDelight repository, as opposed to the
 * in-memory fakes in `SyncEngineTest` and the pure-function checks in `SyncMergePolicyTest`.
 */
class SqlDelightSyncConvergenceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `applying the same changeset twice does not change the library`(): Unit = runBlocking {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("idempotent.db")).use { repository ->
            val syncRepository = SqlDelightSyncLocalRepository(repository.database)
            repository.insertManga(MangaRecord(sourceId = 10, url = "/m", title = "Local", lastModifiedAt = 50))

            val incoming = changeset(
                mangas = listOf(
                    AndroidBackupManga(
                        source = 10,
                        url = "/m",
                        title = "Remote",
                        lastModifiedAt = 200,
                        chapters = listOf(AndroidBackupChapter(url = "/c1", name = "C1", lastModifiedAt = 200)),
                    ),
                ),
            )

            syncRepository.applyChangeset(incoming)
            val afterFirst = snapshot(repository)
            val result = syncRepository.applyChangeset(incoming)

            snapshot(repository) shouldBe afterFirst
            // The second apply re-merges the same rows rather than inserting duplicates.
            result.mangaInserted shouldBe 0
            result.chapterInserted shouldBe 0
        }
    }

    @Test
    fun `devices converge when both edit the same manga in the same millisecond`(): Unit = runBlocking {
        // Equal watermarks exercise the deviceId tie break, the branch where a naive
        // "incoming always wins" implementation would keep flip-flopping between devices.
        val left = open("left.db", deviceId = "device-a")
        val right = open("right.db", deviceId = "device-b")
        try {
            left.repository.insertManga(
                MangaRecord(sourceId = 10, url = "/m", title = "A title", lastModifiedAt = 100),
            )
            right.repository.insertManga(
                MangaRecord(sourceId = 10, url = "/m", title = "B title", lastModifiedAt = 100),
            )

            left.apply(right.export())
            right.apply(left.export())

            val leftTitle = left.repository.findManga(10, "/m")!!.title
            val rightTitle = right.repository.findManga(10, "/m")!!.title
            leftTitle shouldBe rightTitle
            // "device-b" sorts above "device-a", so its title wins on both devices.
            leftTitle shouldBe "B title"
        } finally {
            left.repository.close()
            right.repository.close()
        }
    }

    @Test
    fun `a newer local edit wins over an older remote edit on both devices`(): Unit = runBlocking {
        val left = open("older.db", deviceId = "device-a")
        val right = open("newer.db", deviceId = "device-b")
        try {
            left.repository.insertManga(MangaRecord(sourceId = 10, url = "/m", title = "Old", lastModifiedAt = 100))
            right.repository.insertManga(MangaRecord(sourceId = 10, url = "/m", title = "New", lastModifiedAt = 300))

            left.apply(right.export())
            right.apply(left.export())

            left.repository.findManga(10, "/m")!!.title shouldBe "New"
            right.repository.findManga(10, "/m")!!.title shouldBe "New"
        } finally {
            left.repository.close()
            right.repository.close()
        }
    }

    @Test
    fun `removing a manga from the library propagates as unfavorite instead of a row deletion`(): Unit =
        runBlocking {
            val source = open("favorite.db", deviceId = "device-a")
            val peer = open("peer.db", deviceId = "device-b")
            try {
                source.repository.insertManga(
                    MangaRecord(sourceId = 10, url = "/m", title = "M", favorite = true, lastModifiedAt = 100),
                )
                val existing = source.repository.findManga(10, "/m")!!
                source.repository.updateManga(
                    existing.copy(favorite = false, lastModifiedAt = 400, favoriteModifiedAt = 400),
                )
                peer.repository.insertManga(MangaRecord(sourceId = 10, url = "/m", title = "M", lastModifiedAt = 100))

                peer.apply(source.export())

                // Without tombstone propagation (docs/library-sync-plan.md §7.3) removal still
                // converges: the row survives so chapters and history are not silently dropped.
                peer.repository.findManga(10, "/m")!!.favorite shouldBe false
            } finally {
                source.repository.close()
                peer.repository.close()
            }
        }

    @Test
    fun `an incoming tombstone leaves local rows intact because deletion is not propagated yet`(): Unit =
        runBlocking {
            DesktopLibraryDatabaseFactory.open(tempDir.resolve("tombstone.db")).use { repository ->
                val syncRepository = SqlDelightSyncLocalRepository(repository.database)
                repository.insertManga(MangaRecord(sourceId = 10, url = "/m", title = "M", lastModifiedAt = 100))

                val result = syncRepository.applyChangeset(
                    changeset(
                        mangas = emptyList(),
                        tombstones = listOf(Tombstone(EntityType.MANGA, "10:/m", deletedAt = 500)),
                    ),
                )

                result.mangaInserted shouldBe 0
                result.mangaMerged shouldBe 0
                // Documented boundary: physical deletion stays local until plan Phase 2.2.
                repository.findManga(10, "/m")?.title shouldBe "M"
            }
        }

    private class Harness(
        val repository: SqlDelightLibraryRepository,
        val syncRepository: SqlDelightSyncLocalRepository,
        val deviceId: String,
    ) {
        suspend fun export(): Changeset = Changeset(
            deviceId = deviceId,
            cursor = 1L,
            producedAt = 1L,
            upserts = syncRepository.exportDelta(sinceCursor = 0L),
        )

        suspend fun apply(changeset: Changeset) {
            syncRepository.applyChangeset(changeset)
        }
    }

    private suspend fun open(name: String, deviceId: String): Harness {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve(name))
        // Persist the identity first: the export reads it straight from the metadata table and
        // stamps it into each row's memo, which is what the tie break compares.
        val storedDeviceId = SqlDelightSyncStateStore(repository.database, defaultDeviceId = { deviceId }).getDeviceId()
        return Harness(repository, SqlDelightSyncLocalRepository(repository.database), storedDeviceId)
    }

    private fun snapshot(repository: SqlDelightLibraryRepository) =
        repository.librarySnapshot().map { it.copy(id = 0) }

    private fun changeset(
        mangas: List<AndroidBackupManga>,
        tombstones: List<Tombstone> = emptyList(),
    ): Changeset = Changeset(
        deviceId = "remote",
        cursor = 2L,
        producedAt = 2L,
        upserts = EntityDelta(mangas = mangas),
        tombstones = tombstones,
    )
}
