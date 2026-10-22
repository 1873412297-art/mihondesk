package mihon.desktop.ui.home

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.desktop.updates.UpdatedChapterItem
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HomeTodayTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(
        day: Int,
        hour: Int,
    ) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(21, 22)

    private fun update(manga: Long, chapter: Long, number: Double, fetched: Long, read: Boolean = false) =
        UpdatedChapterItem(
            mangaId = manga,
            chapterId = chapter,
            mangaTitle = "M$manga",
            chapterName = "Ch $number",
            chapterNumber = number,
            dateFetch = fetched,
            read = read,
        )

    @Test
    fun `today's updates keep only chapters fetched today and group them by manga, newest manga first`() {
        val groups = todayUpdates(
            listOf(
                update(manga = 1, chapter = 11, number = 1.0, fetched = at(21, 8)),
                update(manga = 2, chapter = 21, number = 5.0, fetched = at(21, 20)),
                update(manga = 1, chapter = 12, number = 2.0, fetched = at(21, 9)),
                update(manga = 3, chapter = 31, number = 1.0, fetched = at(20, 23)),
            ),
            nowMillis = now,
            zone = zone,
        )
        groups.map { it.mangaId } shouldContainExactly listOf(2L, 1L)
        groups[1].chapters.map { it.chapterId } shouldContainExactly listOf(12L, 11L)
        groups[1].unreadCount shouldBe 2
    }

    @Test
    fun `today's chapters are rebuilt from the library after a restart, skipping manga added today`() {
        fun manga(id: Long, added: Long, lastUpdate: Long) = mihon.desktop.library.model.LibraryManga(
            id = id, sourceId = 1, url = "/$id", title = "M$id", thumbnailUrl = "t$id", chapterCount = 3,
            unreadCount = 1, dateAdded = added, lastUpdate = lastUpdate,
        )
        fun chapter(id: Long, manga: Long, fetched: Long) = mihon.desktop.library.model.LibraryChapter(
            id = id, mangaId = manga, url = "/$id", name = "Ch $id", scanlator = null, read = false, bookmark = false,
            lastPageRead = 0, dateFetch = fetched, dateUpload = 0, chapterNumber = id.toDouble(), sourceOrder = 0,
            lastModifiedAt = 0, version = 0, memoJson = "{}",
        )
        val chapters = mapOf(
            1L to listOf(chapter(1, 1, at(1, 9)), chapter(2, 1, at(21, 7))),
            2L to listOf(chapter(3, 2, at(21, 10))),
        )
        val asked = mutableListOf<Long>()
        val items = todayLibraryChapters(
            library = listOf(
                manga(id = 1, added = at(1, 8), lastUpdate = at(21, 7)),
                manga(id = 2, added = at(21, 10), lastUpdate = at(21, 10)),
                manga(id = 3, added = at(1, 8), lastUpdate = at(19, 7)),
            ),
            chaptersFor = {
                asked += it
                chapters[it].orEmpty()
            },
            nowMillis = now,
            zone = zone,
        )
        items.map { it.chapterId } shouldContainExactly listOf(2L)
        items.single().mangaThumbnailUrl shouldBe "t1"
        asked shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `session rows without covers borrow the stored cover of the same manga`() {
        val session = listOf(update(1, 10, 5.0, now).copy(mangaThumbnailUrl = null))
        val stored = listOf(update(1, 9, 4.0, now).copy(mangaThumbnailUrl = "cover://1"))
        val merged = mergeTodayChapters(session, stored)
        merged.map { it.mangaThumbnailUrl } shouldContainExactly listOf("cover://1", "cover://1")
        todayUpdates(merged, now, zone).single().thumbnailUrl shouldBe "cover://1"
    }
}
