package mihon.desktop.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.nio.file.Path

class OnlineMangaSyncServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `add is idempotent and remove then re-add preserves identity and chapters`() = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db"))
        try {
            val manager = DesktopSourceManager().apply { registerBuiltinSource(FakeSource()) }
            val service = OnlineMangaSyncService(repository, manager)
            val manga = SManga(url = "/series/one", title = "One", initialized = true)

            val firstId = service.addOrUpdateOnlineManga(FakeSource.ID, manga)
            val duplicateId = service.addOrUpdateOnlineManga(FakeSource.ID, manga)
            assertEquals(firstId, duplicateId)
            assertEquals(1, repository.allMangaSnapshot().count { it.url == manga.url })
            assertEquals(1, repository.chapterSnapshot(firstId).size)

            assertTrue(service.removeFromLibrary(FakeSource.ID, manga.url))
            assertFalse(service.isMangaInLibrary(FakeSource.ID, manga.url))
            assertEquals(1, repository.chapterSnapshot(firstId).size)

            val readdedId = service.addOrUpdateOnlineManga(FakeSource.ID, manga)
            assertEquals(firstId, readdedId)
            assertTrue(service.isMangaInLibrary(FakeSource.ID, manga.url))
        } finally {
            repository.close()
        }
    }

    @Test
    fun `chapter network failure does not create a favorite row`() {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("failed.db"))
        try {
            val manager = DesktopSourceManager().apply { registerBuiltinSource(FakeSource(failChapters = true)) }
            val service = OnlineMangaSyncService(repository, manager)
            assertThrows(IOException::class.java) {
                runBlocking {
                    service.addOrUpdateOnlineManga(
                        FakeSource.ID,
                        SManga(url = "/series/fail", title = "Fail", initialized = true),
                    )
                }
            }
            assertEquals(0, repository.allMangaSnapshot().size)
        } finally {
            repository.close()
        }
    }

    @Test
    fun `preparing an online manga for reading does not add it to library or categories`() = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("read-without-library.db"))
        try {
            val manager = DesktopSourceManager().apply { registerBuiltinSource(FakeSource()) }
            val service = OnlineMangaSyncService(repository, manager)
            val manga = SManga(url = "/series/read-only", title = "Read only", initialized = true)

            val mangaId = service.prepareOnlineMangaForReading(FakeSource.ID, manga)

            assertFalse(repository.findManga(FakeSource.ID, manga.url)!!.favorite)
            assertEquals(0L, repository.findManga(FakeSource.ID, manga.url)!!.dateAdded)
            assertEquals(1, repository.chapterSnapshot(mangaId).size)
            assertTrue(repository.mangaCategoryLinksSnapshot()[mangaId].isNullOrEmpty())
        } finally {
            repository.close()
        }
    }

    @ParameterizedTest
    @CsvSource("details,false", "chapters,false", "details,true", "chapters,true")
    fun `cancelled source result cannot start database persistence`(
        stage: String,
        adding: Boolean,
    ): Unit = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("cancelled.db"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val manager = DesktopSourceManager().apply {
            registerBuiltinSource(
                FakeSource(beforeResponse = { responseStage ->
                    if (responseStage == stage) {
                        started.complete(Unit)
                        withContext(NonCancellable) { release.await() }
                    }
                }),
            )
        }
        val service = OnlineMangaSyncService(repository, manager)
        val request = async {
            val manga = SManga(url = "/cancelled", title = "Cancelled")
            if (adding) {
                service.addOrUpdateOnlineManga(FakeSource.ID, manga)
            } else {
                service.prepareOnlineMangaForReading(FakeSource.ID, manga)
            }
        }
        try {
            withTimeout(5000) { started.await() }
            request.cancel()
            release.complete(Unit)
            withTimeout(5000) { request.join() }
            assertTrue(request.isCancelled)
            assertTrue(repository.allMangaSnapshot().isEmpty())
            assertTrue(repository.allChaptersSnapshot().isEmpty())
        } finally {
            release.complete(Unit)
            request.cancelAndJoin()
            repository.close()
        }
    }

    private class FakeSource(
        private val failChapters: Boolean = false,
        private val beforeResponse: suspend (String) -> Unit = {},
    ) : WindowsCatalogueSource {
        override val id = ID
        override val name = "Fake"
        override val lang = "en"
        override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga): SManga {
            beforeResponse("details")
            return manga.copy(initialized = true)
        }
        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            beforeResponse("chapters")
            if (failChapters) throw IOException("network unavailable")
            return listOf(SChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1f))
        }
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        companion object {
            const val ID = 9001L
        }
    }
}
