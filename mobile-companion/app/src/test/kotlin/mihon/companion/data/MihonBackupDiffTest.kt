package mihon.companion.data

import io.kotest.matchers.shouldBe
import mihon.sync.core.merge.ReadIntent
import mihon.sync.core.model.MangaKey
import org.junit.jupiter.api.Test

class MihonBackupDiffTest {
    private val a = MangaKey(42, "/a")
    private val b = MangaKey(42, "/b")

    private fun backup(vararg mangas: mihon.sync.core.model.AndroidBackupManga) = MihonBackup(mangas.toList())

    @Test
    fun `detects chapters marked unread and mangas removed from the shelf`() {
        val before = backup(
            manga("/a", chapters = listOf(chapter("c1", read = true), chapter("c2", read = true))),
            manga("/b"),
        )
        val after = backup(manga("/a", chapters = listOf(chapter("c1", read = true), chapter("c2", read = false))))

        val changes = MihonBackupDiff.changes(MihonBackupDiff.snapshot(before), after)

        changes.markedUnread shouldBe mapOf(a to setOf("c2"))
        changes.removedFromShelf shouldBe setOf(b)
    }

    @Test
    fun `an unchanged backup changes nothing`() {
        val same = backup(manga("/a", chapters = listOf(chapter("c1", read = true))))
        MihonBackupDiff.changes(MihonBackupDiff.snapshot(same), same).isEmpty shouldBe true
    }

    @Test
    fun `apply marks the chapter unread and records when`() {
        val library = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(
                listOf(manga("/a", chapters = listOf(chapter("c2", read = true, modified = 100)))),
                emptyList(),
                deviceId = "x",
            ),
            markDirty = false,
        ).first

        val updated = MihonBackupDiff.apply(library, MihonChanges(markedUnread = mapOf(a to setOf("c2"))), at = 500)
        val c2 = updated.mangas.single().manga.chapters.single()

        c2.read shouldBe false
        ReadIntent.timeOf(c2.memo.decodeToString(), ReadIntent.UNREAD_AT) shouldBe 500
        updated.pendingChanges shouldBe 1
    }

    @Test
    fun `a read newer than the Mihon backup is not undone`() {
        // Read on the desktop at 900, after Mihon's backup at 500 that still had it unread.
        val library = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(
                listOf(manga("/a", chapters = listOf(chapter("c2", read = true, modified = 900)))),
                emptyList(),
                deviceId = "x",
            ),
            markDirty = false,
        ).first

        MihonBackupDiff.apply(library, MihonChanges(markedUnread = mapOf(a to setOf("c2"))), at = 500)
            .mangas.single().manga.chapters.single().read shouldBe true
    }

    @Test
    fun `removal only wins over an older shelf addition`() {
        val library = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(listOf(manga("/b").copy(favoriteModifiedAt = 700)), emptyList(), deviceId = "x"),
            markDirty = false,
        ).first

        MihonBackupDiff.apply(library, MihonChanges(removedFromShelf = setOf(b)), at = 500)
            .mangas.single().manga.favorite shouldBe true
        MihonBackupDiff.apply(library, MihonChanges(removedFromShelf = setOf(b)), at = 800)
            .mangas.single().manga.favorite shouldBe false
    }
}
