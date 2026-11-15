package mihon.companion.data

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.sync.core.model.MangaKey
import org.junit.jupiter.api.Test

class LibraryMergeTest {
    private val key = MangaKey(42, "/a")

    @Test
    fun `remote data is inserted without being marked dirty`() {
        val incoming = IncomingLibrary(
            mangas = listOf(manga("/a", categories = listOf(3))),
            categories = listOf(category("Reading", 3)),
            deviceId = "desktop",
        )
        val (library, stats) = LibraryMerge.merge(StoredLibrary(), incoming, markDirty = false)

        stats.mangaInserted shouldBe 1
        library.mangas.single().categoryNames shouldContainExactly listOf("Reading")
        library.categories.single().category.order shouldBe 0 // local position, not the peer's
        library.pendingChanges shouldBe 0
    }

    @Test
    fun `backup import marks changed entries dirty and keeps read progress from both sides`() {
        val base = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(
                listOf(manga("/a", chapters = listOf(chapter("c1"), chapter("c2", read = true)))),
                emptyList(),
                deviceId = "desktop",
            ),
            markDirty = false,
        ).first

        val backup =
            IncomingLibrary(
                listOf(manga("/a", modified = 500, chapters = listOf(chapter("c1", read = true, modified = 500)))),
                emptyList(),
                deviceId = "phone",
            )
        val (merged, stats) = LibraryMerge.merge(base, backup, markDirty = true)

        stats.mangaMerged shouldBe 1
        merged.mangas.single().manga.chapters.map { it.read } shouldContainExactly listOf(true, true)
        merged.dirtyMangas().map { it.key } shouldContainExactly listOf(key)
    }

    @Test
    fun `identical data is not a change`() {
        val incoming = IncomingLibrary(listOf(manga("/a")), emptyList(), deviceId = "desktop")
        val once = LibraryMerge.merge(StoredLibrary(), incoming, markDirty = true).first
        val (twice, stats) = LibraryMerge.merge(once, incoming, markDirty = true)

        stats.mangaMerged shouldBe 0
        twice.seq shouldBe once.seq
    }

    @Test
    fun `local source entries are ignored`() {
        val local = manga("/local").copy(source = 0)
        LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(listOf(local), emptyList(), deviceId = "x"),
            markDirty = true,
        )
            .first.mangas shouldBe emptyList()
    }

    @Test
    fun `marking chapters read stamps them and records history`() {
        val library = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(
                listOf(manga("/a", chapters = listOf(chapter("c1", number = 1f), chapter("c2", number = 2f)))),
                emptyList(),
                deviceId = "desktop",
            ),
            markDirty = false,
        ).first

        val edited = LibraryEdits.setChaptersRead(
            library,
            key,
            setOf("c1", "c2"),
            read = true,
            now = 9_000,
            deviceId = "phone",
        )
        val manga = edited.mangas.single().manga

        manga.chapters.all { it.read && it.lastModifiedAt == 9_000L } shouldBe true
        manga.history.single().url shouldBe "c2"
        manga.lastModifiedAt shouldBe 9_000
        edited.pendingChanges shouldBe 1
    }
}
