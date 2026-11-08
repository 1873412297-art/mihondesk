package mihon.desktop.library.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryRepository

internal class SqlDelightLibraryReadDelegate(
    private val database: DesktopLibraryDatabase,
    private val driver: app.cash.sqldelight.db.SqlDriver,
) : LibraryRepository {
    private val queries = database.libraryQueries

    override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> =
        if (categoryId == null || categoryId == -1L) {
            queries.selectLibrary().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(SelectLibrary::toModel) }
        } else {
            queries.selectLibraryByCategory(categoryId).asFlow().mapToList(Dispatchers.IO)
                .map { rows -> rows.map(SelectLibraryByCategory::toModel) }
        }

    override fun observeManga(id: Long): Flow<MangaDetails?> = combine(
        queries.selectMangaById(id).asFlow().mapToOneOrNull(Dispatchers.IO),
        queries.selectCategoriesForManga(id).asFlow().mapToList(Dispatchers.IO),
    ) { manga, categories -> manga?.toDetails(categories.map(Category::toRecord)) }

    override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> =
        queries.selectChaptersForManga(mangaId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(Chapter::toModel) }

    override fun observeCategories(): Flow<List<CategoryRecord>> =
        queries.selectAllCategories().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(Category::toRecord) }

    override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> =
        queries.selectHistoryWithDetails(query.trim()).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(SelectHistoryWithDetails::toModel) }

    override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> =
        queries.selectTrackingForManga(mangaId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map(Tracking::toTrackingRecord) }

    override fun librarySnapshot(categoryId: Long?): List<LibraryManga> =
        if (categoryId == null || categoryId == -1L) {
            queries.selectLibrary().executeAsList().map(SelectLibrary::toModel)
        } else {
            queries.selectLibraryByCategory(categoryId).executeAsList().map(SelectLibraryByCategory::toModel)
        }

    override fun mangaSnapshot(id: Long): MangaDetails? {
        val manga = queries.selectMangaById(id).executeAsOneOrNull() ?: return null
        return manga.toDetails(queries.selectCategoriesForManga(id).executeAsList().map(Category::toRecord))
    }

    override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> =
        queries.selectChaptersForManga(mangaId).executeAsList().map(Chapter::toModel)

    override fun categoriesSnapshot(): List<CategoryRecord> =
        queries.selectAllCategories().executeAsList().map(Category::toRecord)

    override fun historySnapshot(query: String): List<HistoryWithDetails> =
        queries.selectHistoryWithDetails(query.trim()).executeAsList().map(SelectHistoryWithDetails::toModel)

    override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> =
        queries.selectTrackingForManga(mangaId).executeAsList().map(Tracking::toTrackingRecord)

    override fun allMangaSnapshot(): List<MangaRecord> =
        queries.selectAllMangaForExport().executeAsList().map(Manga::toMangaRecord)

    override fun allChaptersSnapshot(): List<ChapterRecord> =
        queries.selectAllChaptersForExport().executeAsList().map(Chapter::toChapterRecord)

    override fun allCategoriesSnapshot(): List<CategoryRecord> =
        queries.selectAllCategoriesForExport().executeAsList().map(Category::toRecord)

    override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> {
        val links = queries.selectAllMangaCategoriesForExport().executeAsList()
        val result = mutableMapOf<Long, MutableList<Long>>()
        for (link in links) {
            result.computeIfAbsent(link.manga_id) { mutableListOf() }.add(link.category_id)
        }
        return result
    }

    override fun allHistorySnapshot(): List<HistoryRecord> =
        queries.selectAllHistoryForExport().executeAsList().map {
            HistoryRecord(
                chapterId = it.chapter_id,
                lastRead = it.last_read,
                readDuration = it.read_duration,
            )
        }

    override fun allTrackingSnapshot(): List<TrackingRecord> =
        queries.selectAllTrackingForExport().executeAsList().map(Tracking::toTrackingRecord)

    override fun allSourcesSnapshot(): List<mihon.desktop.library.model.SourceRecord> =
        queries.selectAllSourcesForExport().executeAsList().map {
            mihon.desktop.library.model.SourceRecord(
                sourceId = it.source_id,
                name = it.name,
                importedAt = it.imported_at,
            )
        }

    override fun allPreferenceSnapshots(): List<mihon.desktop.library.model.PreferenceSnapshotRecord> =
        queries.selectAllPreferenceSnapshotsForExport().executeAsList().map {
            mihon.desktop.library.model.PreferenceSnapshotRecord(
                key = it.key,
                valueType = it.value_type,
                valueJson = it.value_json,
                importedAt = it.imported_at,
            )
        }

    override fun allSourcePreferenceSnapshots(): List<mihon.desktop.library.model.SourcePreferenceSnapshotRecord> =
        queries.selectAllSourcePreferenceSnapshotsForExport().executeAsList().map {
            mihon.desktop.library.model.SourcePreferenceSnapshotRecord(
                sourceKey = it.source_key,
                key = it.key,
                valueType = it.value_type,
                valueJson = it.value_json,
                importedAt = it.imported_at,
            )
        }

    override fun checkIntegrity(): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA integrity_check",
            mapper = { cursor ->
                val results = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let { results.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(results)
            },
            parameters = 0,
        ).value
    }

    override fun registeredChapterAssetPaths(): Set<java.nio.file.Path> =
        queries.selectRegisteredChapterAssetPaths().executeAsList().mapNotNullTo(mutableSetOf()) { row ->
            val root = row.storage_path.takeIf(String::isNotBlank) ?: return@mapNotNullTo null
            runCatching {
                java.nio.file.Path.of(root).resolve(row.relative_path).toAbsolutePath().normalize()
            }.getOrNull()
        }

    override fun latestImportReport(): ImportReport? {
        val report = queries.selectLatestImportReport().executeAsOneOrNull() ?: return null
        val items = queries.selectImportReportItems(report.id).executeAsList().map(Import_report_item::toModel)
        return report.toModel(items)
    }
}
