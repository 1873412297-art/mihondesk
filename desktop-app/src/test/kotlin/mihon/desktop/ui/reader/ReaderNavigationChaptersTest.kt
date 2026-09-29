package mihon.desktop.ui.reader

import io.kotest.matchers.collections.shouldContainExactly
import mihon.desktop.library.model.LibraryChapter
import org.junit.jupiter.api.Test

class ReaderNavigationChaptersTest {
    @Test
    fun `directly requested excluded chapter stays readable once`() {
        val allowed = chapter(1)
        val excluded = chapter(2)
        readerNavigationChapters(listOf(allowed), listOf(allowed, excluded), excluded.id)
            .map { it.id } shouldContainExactly listOf(1L, 2L)
        readerNavigationChapters(listOf(allowed), listOf(allowed, excluded), allowed.id)
            .map { it.id } shouldContainExactly listOf(1L)
    }

    @Test
    fun `unknown requested chapter is not invented`() {
        val allowed = chapter(1)
        readerNavigationChapters(listOf(allowed), listOf(allowed), 99L)
            .map { it.id } shouldContainExactly listOf(1L)
    }

    private fun chapter(id: Long) = LibraryChapter(
        id = id,
        mangaId = 10,
        url = "/$id",
        name = "Chapter $id",
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        dateUpload = 0,
        chapterNumber = id.toDouble(),
        sourceOrder = id,
        lastModifiedAt = 0,
        version = 0,
        memoJson = "{}",
    )
}
