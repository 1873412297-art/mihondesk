package mihon.desktop.library.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import mihon.reader.session.ReaderProgressUpdate
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SqlDelightSyncLocalRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reading progress update and upsertHistory maintain chapter last_modified_at`(): Unit = runBlocking {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("sync_progress.db")).use { repo ->
            val mangaId = repo.transaction {
                val mId = insertManga(MangaRecord(sourceId = 1, url = "/test/1", title = "Manga"))
                insertChapter(ChapterRecord(mangaId = mId, url = "/ch/1", name = "Ch 1", lastModifiedAt = 10L))
                mId
            }

            val chBefore = repo.findChapter(mangaId, "/ch/1")!!
            chBefore.lastModifiedAt shouldBe 10L

            // Record reader progress
            repo.record(
                ReaderProgressUpdate(
                    chapterId = chBefore.id,
                    pageIndex = 5,
                    completed = false,
                    lastReadEpochMillis = 2000L,
                    readDurationDeltaMillis = 100L,
                    generation = 1L,
                    sequence = 1L,
                ),
            )

            val chAfterProgress = repo.findChapter(mangaId, "/ch/1")!!
            chAfterProgress.lastPageRead shouldBe 5L
            (chAfterProgress.lastModifiedAt >= 2000L) shouldBe true

            // Upsert history
            repo.upsertHistory(HistoryRecord(chBefore.id, lastRead = 5000L, readDuration = 200L))
            val chAfterHistory = repo.findChapter(mangaId, "/ch/1")!!
            (chAfterHistory.lastModifiedAt >= 5000L) shouldBe true
        }
    }

    @Test
    fun `exportDelta and applyChangeset work correctly with SqlDelight`(): Unit = runBlocking {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("sync_repo.db")).use { repo ->
            val syncRepo = SqlDelightSyncLocalRepository(repo.database)
            val stateStore = SqlDelightSyncStateStore(repo.database)

            // 1. State store tests
            val deviceId = stateStore.getDeviceId()
            (deviceId.isNotEmpty()) shouldBe true
            stateStore.getDeviceId() shouldBe deviceId

            stateStore.getLastPushCursor() shouldBe 0L
            stateStore.setLastPushCursor(10L)
            stateStore.getLastPushCursor() shouldBe 10L

            val clock1 = stateStore.nextCursor()
            val clock2 = stateStore.nextCursor()
            clock2 shouldBe clock1 + 1L

            // 2. Insert local manga
            repo.transaction {
                val mId =
                    insertManga(
                        MangaRecord(sourceId = 10, url = "/manga/a", title = "Local Manga", lastModifiedAt = 50L),
                    )
                insertChapter(ChapterRecord(mangaId = mId, url = "/ch/1", name = "Chapter 1", lastModifiedAt = 60L))
            }

            // Export delta since 0
            val delta0 = syncRepo.exportDelta(sinceCursor = 0L)
            delta0.mangas.size shouldBe 1
            delta0.mangas.first().url shouldBe "/manga/a"
            delta0.mangas.first().chapters.size shouldBe 1

            // Export delta since 100 -> nothing
            val delta100 = syncRepo.exportDelta(sinceCursor = 100L)
            delta100.mangas.size shouldBe 0

            // 3. Apply incoming remote changeset
            val remoteCs = Changeset(
                deviceId = "remote-pc",
                cursor = 1L,
                producedAt = 1000L,
                upserts = EntityDelta(
                    mangas = listOf(
                        AndroidBackupManga(
                            source = 10L,
                            url = "/manga/a",
                            title = "Remote Manga Title",
                            lastModifiedAt = 200L,
                            chapters = listOf(
                                AndroidBackupChapter(
                                    url = "/ch/1",
                                    name = "Chapter 1 (Remote)",
                                    read = true,
                                    lastModifiedAt = 200L,
                                ),
                                AndroidBackupChapter(
                                    url = "/ch/2",
                                    name = "Chapter 2 (New)",
                                    read = false,
                                    lastModifiedAt = 200L,
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val result = syncRepo.applyChangeset(remoteCs)
            result.mangaMerged shouldBe 1
            result.chapterMerged shouldBe 1
            result.chapterInserted shouldBe 1
            result.overriddenItems.size shouldBe 2 // manga and chapter overridden

            val updatedManga = repo.findManga(10L, "/manga/a")!!
            updatedManga.title shouldBe "Remote Manga Title"
            val updatedCh1 = repo.findChapter(updatedManga.id, "/ch/1")!!
            updatedCh1.read shouldBe true
            val newCh2 = repo.findChapter(updatedManga.id, "/ch/2")
            newCh2 shouldNotBe null
        }
    }
}
