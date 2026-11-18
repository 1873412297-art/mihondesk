package mihon.desktop.library.sync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.sync.core.merge.ReadIntent
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** "Keep the furthest progress unless someone deliberately marked it unread later", on the desktop DB. */
class UnreadSyncTest {
    @TempDir
    lateinit var tempDir: Path

    private fun withRepository(block: suspend (SqlDelightLibraryRepository) -> Unit) = runBlocking {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("unread.db")).use { block(it) }
    }

    private fun SqlDelightLibraryRepository.readChapter(): ChapterRecord {
        val mangaId = insertManga(MangaRecord(sourceId = 10, url = "/m", title = "M", favorite = true))
        insertChapter(ChapterRecord(mangaId = mangaId, url = "/c1", name = "C1", read = true, lastModifiedAt = 100))
        return checkNotNull(findChapter(mangaId, "/c1"))
    }

    private fun remote(chapter: AndroidBackupChapter) = Changeset(
        deviceId = "phone",
        cursor = 1L,
        producedAt = 1L,
        upserts = EntityDelta(mangas = listOf(AndroidBackupManga(source = 10, url = "/m", chapters = listOf(chapter)))),
    )

    private fun phoneChapter(read: Boolean, modified: Long, memo: String) = AndroidBackupChapter(
        url = "/c1",
        name = "C1",
        read = read,
        lastModifiedAt = modified,
        memo = memo.encodeToByteArray(),
    )

    @Test
    fun `marking unread on the desktop records it and makes the chapter sync`() = withRepository { repository ->
        val chapter = repository.readChapter()

        repository.updateChapter(chapter.copy(read = false)) // what the batch "mark unread" does

        val stored = checkNotNull(repository.findChapter(chapter.mangaId, "/c1"))
        stored.read shouldBe false
        (ReadIntent.timeOf(stored.memoJson, ReadIntent.UNREAD_AT) > 100) shouldBe true
        (stored.lastModifiedAt > 100) shouldBe true // so exportDelta picks it up
        val exported = SqlDelightSyncLocalRepository(repository.database).exportDelta(sinceCursor = 100)
        exported.mangas.single().chapters.single().read shouldBe false
    }

    @Test
    fun `an unread made on the phone after the desktop read wins`() = withRepository { repository ->
        val chapter = repository.readChapter()
        val unreadOnPhone =
            phoneChapter(read = false, modified = 200, memo = ReadIntent.stamp("{}", read = false, at = 200))

        SqlDelightSyncLocalRepository(repository.database).applyChangeset(remote(unreadOnPhone))

        repository.findChapter(chapter.mangaId, "/c1")!!.read shouldBe false
    }

    @Test
    fun `a phone that simply has not read the chapter yet does not undo the read`() = withRepository { repository ->
        val chapter = repository.readChapter()

        SqlDelightSyncLocalRepository(repository.database).applyChangeset(remote(phoneChapter(false, 300, "{}")))

        repository.findChapter(chapter.mangaId, "/c1")!!.read shouldBe true
    }

    @Test
    fun `reading again on the desktop after the phone's unread wins`() = withRepository { repository ->
        val chapter = repository.readChapter()
        val unreadOnPhone =
            phoneChapter(read = false, modified = 200, memo = ReadIntent.stamp("{}", read = false, at = 200))
        val sync = SqlDelightSyncLocalRepository(repository.database)
        sync.applyChangeset(remote(unreadOnPhone))

        val unread = repository.findChapter(chapter.mangaId, "/c1")!!
        repository.updateChapter(unread.copy(read = true)) // stamped with the real clock, far after 200
        sync.applyChangeset(remote(unreadOnPhone)) // the phone's old unread arrives again

        repository.findChapter(chapter.mangaId, "/c1")!!.read shouldBe true
    }
}
