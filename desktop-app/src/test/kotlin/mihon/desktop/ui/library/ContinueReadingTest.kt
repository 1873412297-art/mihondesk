package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Test

class ContinueReadingTest {

    @Test
    fun `next chapter is the first unread one in reading order`() {
        val chapters = listOf(
            chapter(id = 3, number = 3.0, read = false),
            chapter(id = 1, number = 1.0, read = true),
            chapter(id = 2, number = 2.0, read = false),
        )

        nextChapterToRead(chapters)?.id shouldBe 2L
    }

    @Test
    fun `source order breaks ties between equal chapter numbers`() {
        val chapters = listOf(
            chapter(id = 11, number = 5.0, read = false, sourceOrder = 2),
            chapter(id = 10, number = 5.0, read = false, sourceOrder = 1),
        )

        nextChapterToRead(chapters)?.id shouldBe 10L
    }

    @Test
    fun `fully read manga reopens the last chapter and empty manga has none`() {
        val chapters = listOf(chapter(id = 1, number = 1.0, read = true), chapter(id = 2, number = 2.0, read = true))

        nextChapterToRead(chapters)?.id shouldBe 2L
        nextChapterToRead(emptyList()) shouldBe null
    }

    @Test
    fun `button only shows outside selection mode for manga with unread chapters`() {
        showsContinueReading(manga(unread = 3), isSelectionMode = false) shouldBe true
        showsContinueReading(manga(unread = 3), isSelectionMode = true) shouldBe false
        showsContinueReading(manga(unread = 0), isSelectionMode = false) shouldBe false
    }

    private fun manga(unread: Long) = LibraryManga(
        id = 1,
        sourceId = 1,
        url = "/m",
        title = "Manga",
        thumbnailUrl = null,
        chapterCount = 5,
        unreadCount = unread,
    )

    private fun chapter(id: Long, number: Double, read: Boolean, sourceOrder: Long = id) = LibraryChapter(
        id = id,
        mangaId = 1,
        url = "/c/$id",
        name = "Chapter $id",
        scanlator = null,
        read = read,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        dateUpload = 0,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        lastModifiedAt = 0,
        version = 0,
        memoJson = "{}",
    )
}
