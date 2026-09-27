package mihon.desktop.ui.browse.migration

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.ui.browse.BrowsePresenter
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MigrationCategoryAndTrackingTest {

    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private val targetSourceDescriptor = SourceDescriptor(
        id = 200L,
        name = "Target Source",
        lang = "en",
        className = "TargetSource",
        baseUrl = "https://target.example.com",
    )

    private val dummyTargetSource = object : WindowsCatalogueSource {
        override val id: Long = 200L
        override val name: String = "Target Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage =
            MangasPage(emptyList(), false)

        override suspend fun getMangaDetails(manga: SManga): SManga = manga.copy(
            author = "Oda",
            artist = "Oda",
            description = "Pirate adventure",
        )

        override suspend fun getChapterList(manga: SManga): List<SChapter> = listOf(
            SChapter(
                name = "Chapter 1",
                url = "/target/c1",
                chapterNumber = 1.0f,
            ),
            SChapter(
                name = "Chapter 2",
                url = "/target/c2",
                chapterNumber = 2.0f,
            ),
        )

        override suspend fun getPageList(chapter: SChapter) = emptyList<mihon.extension.source.model.Page>()
    }

    @Test
    fun `migration transfers chapters categories and tracking idempotently`(@TempDir tempDir: Path) = runBlocking {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("exts").toFile(), prefStore)
        val sourceManager =
            DesktopSourceManager(installer = installer, processManager = null, preferenceStore = prefStore)
        sourceManager.registerBuiltinSource(dummyTargetSource)

        val presenter = BrowsePresenter(
            sourceManager = sourceManager,
            installer = installer,
            storeService = ExtensionStoreService(preferenceStore = prefStore),
            libraryRepository = db,
            preferenceStore = prefStore,
            scope = scope,
        )

        // 1. Seed source manga (sourceId = 100)
        val now = System.currentTimeMillis()
        val sourceMangaId = db.insertManga(
            MangaRecord(
                id = 0L,
                sourceId = 100L,
                url = "/source/one-piece",
                title = "One Piece",
                favorite = true,
                dateAdded = now - 10000,
                lastModifiedAt = now,
                favoriteModifiedAt = now,
                viewerFlags = 2L,
                memoJson = "{\"showMissingChapters\":false}",
            ),
        )

        // Seed chapters for source manga
        db.insertChapter(
            ChapterRecord(
                id = 0L,
                mangaId = sourceMangaId,
                url = "/source/c1",
                name = "Chapter 1",
                read = true,
                bookmark = true,
                lastPageRead = 15L,
                chapterNumber = 1.0,
                sourceOrder = 0L,
                dateFetch = now,
                dateUpload = now,
                lastModifiedAt = now,
            ),
        )

        // Seed categories (Category 1: Favorites, Category 2: Shonen)
        val cat1Id = db.upsertCategory(CategoryRecord(id = 0L, name = "Favorites", sortOrder = 0L))
        val cat2Id = db.upsertCategory(CategoryRecord(id = 0L, name = "Shonen", sortOrder = 1L))
        db.linkCategory(sourceMangaId, cat1Id)
        db.linkCategory(sourceMangaId, cat2Id)

        // Seed tracking record for source manga (trackerId = 10, remoteId = 12345)
        db.insertTracking(
            TrackingRecord(
                id = 0L,
                mangaId = sourceMangaId,
                trackerId = 10L,
                remoteId = 12345L,
                title = "One Piece",
                lastChapterRead = 1.0,
                score = 9.5,
                status = 1L,
            ),
        )

        val oldManga = LibraryManga(
            id = sourceMangaId,
            sourceId = 100L,
            url = "/source/one-piece",
            title = "One Piece",
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 0L,
        )

        val targetManga = SManga(
            title = "One Piece",
            url = "/target/one-piece",
        )

        // 2. Perform Migration
        val targetMangaId = presenter.performMigrationInternal(
            oldManga = oldManga,
            targetSource = targetSourceDescriptor,
            targetManga = targetManga,
        )

        // 3. Verify target manga
        val targetRecord = db.findManga(200L, "/target/one-piece")
        targetRecord.shouldNotBeNull()
        targetRecord.favorite shouldBe true
        targetRecord.viewerFlags shouldBe 2L
        targetRecord.memoJson shouldBe "{\"showMissingChapters\":false}"

        // Source manga should be unfavorited
        val updatedSource = db.findManga(100L, "/source/one-piece")
        updatedSource.shouldNotBeNull()
        updatedSource.favorite shouldBe false

        // Chapters: target should have chapter 1 with read=true, bookmark=true, lastPageRead=15
        val targetChapters = db.chapterSnapshot(targetMangaId).associateBy { it.chapterNumber }
        targetChapters[1.0].shouldNotBeNull()
        targetChapters[1.0]!!.read shouldBe true
        targetChapters[1.0]!!.bookmark shouldBe true
        targetChapters[1.0]!!.lastPageRead shouldBe 15L
        targetChapters[2.0].shouldNotBeNull()
        targetChapters[2.0]!!.read shouldBe false

        // Categories: target should have been linked to cat1 and cat2
        val targetCategories = db.mangaSnapshot(targetMangaId)?.categories?.map { it.id }.orEmpty()
        targetCategories shouldContain cat1Id
        targetCategories shouldContain cat2Id

        // Tracking: target should have the tracking record, source should no longer have it
        val targetTracking = db.findTracking(targetMangaId, 10L)
        targetTracking.shouldNotBeNull()
        targetTracking.remoteId shouldBe 12345L
        targetTracking.score shouldBe 9.5
        targetTracking.lastChapterRead shouldBe 1.0

        val sourceTracking = db.findTracking(sourceMangaId, 10L)
        sourceTracking.shouldBeNull()

        // 4. Test Idempotency: run migration again
        val secondRunId = presenter.performMigrationInternal(
            oldManga = oldManga,
            targetSource = targetSourceDescriptor,
            targetManga = targetManga,
        )
        secondRunId shouldBe targetMangaId

        // Ensure no duplicate categories or tracking records
        val categoriesAfterSecondRun = db.mangaSnapshot(targetMangaId)?.categories.orEmpty()
        categoriesAfterSecondRun shouldHaveSize 2

        val trackingAfterSecondRun = db.trackingSnapshot(targetMangaId)
        trackingAfterSecondRun shouldHaveSize 1

        db.close()
    }
}
