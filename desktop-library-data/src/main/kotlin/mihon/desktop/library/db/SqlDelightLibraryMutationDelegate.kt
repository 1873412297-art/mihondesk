package mihon.desktop.library.db

import kotlinx.coroutines.flow.map
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort

internal class SqlDelightLibraryMutationDelegate(
    private val database: DesktopLibraryDatabase,
) : LibraryMutationPort {
    private val queries = database.libraryQueries

    override fun <T> transaction(block: LibraryMutationPort.() -> T): T {
        val port = this
        return database.transactionWithResult { block.invoke(port) }
    }

    override fun findManga(sourceId: Long, url: String): MangaRecord? =
        queries.selectMangaByIdentity(sourceId, url).executeAsOneOrNull()?.toMangaRecord()

    override fun insertManga(value: MangaRecord): Long = database.transactionWithResult {
        queries.insertManga(
            source_id = value.sourceId,
            url = value.url,
            title = value.title,
            artist = value.artist,
            author = value.author,
            description = value.description,
            genre_json = value.genreJson,
            status = value.status,
            thumbnail_url = value.thumbnailUrl,
            favorite = value.favorite,
            date_added = value.dateAdded,
            viewer_flags = value.viewerFlags,
            chapter_flags = value.chapterFlags,
            update_strategy = value.updateStrategy,
            last_modified_at = value.lastModifiedAt,
            favorite_modified_at = value.favoriteModifiedAt,
            excluded_scanlators_json = value.excludedScanlatorsJson,
            version = value.version,
            notes = value.notes,
            initialized = value.initialized,
            memo_json = value.memoJson,
        )
        lastInsertRowId()
    }

    override fun updateManga(value: MangaRecord) {
        queries.updateManga(
            source_id = value.sourceId,
            url = value.url,
            title = value.title,
            artist = value.artist,
            author = value.author,
            description = value.description,
            genre_json = value.genreJson,
            status = value.status,
            thumbnail_url = value.thumbnailUrl,
            favorite = value.favorite,
            date_added = value.dateAdded,
            viewer_flags = value.viewerFlags,
            chapter_flags = value.chapterFlags,
            update_strategy = value.updateStrategy,
            last_modified_at = value.lastModifiedAt,
            favorite_modified_at = value.favoriteModifiedAt,
            excluded_scanlators_json = value.excludedScanlatorsJson,
            version = value.version,
            notes = value.notes,
            initialized = value.initialized,
            memo_json = value.memoJson,
            id = value.id,
        )
    }

    override fun findChapter(mangaId: Long, url: String): ChapterRecord? =
        queries.selectChapterByIdentity(mangaId, url).executeAsOneOrNull()?.toChapterRecord()

    override fun chaptersForManga(mangaId: Long): List<ChapterRecord> =
        queries.selectChaptersForManga(mangaId).executeAsList().map { it.toChapterRecord() }

    override fun insertChapter(value: ChapterRecord): Long = database.transactionWithResult {
        queries.insertChapter(
            manga_id = value.mangaId,
            url = value.url,
            name = value.name,
            scanlator = value.scanlator,
            read = value.read,
            bookmark = value.bookmark,
            last_page_read = value.lastPageRead,
            date_fetch = value.dateFetch,
            date_upload = value.dateUpload,
            chapter_number = value.chapterNumber,
            source_order = value.sourceOrder,
            last_modified_at = value.lastModifiedAt,
            version = value.version,
            memo_json = value.memoJson,
        )
        lastInsertRowId()
    }

    override fun updateChapter(value: ChapterRecord) {
        queries.updateChapter(
            manga_id = value.mangaId,
            url = value.url,
            name = value.name,
            scanlator = value.scanlator,
            read = value.read,
            bookmark = value.bookmark,
            last_page_read = value.lastPageRead,
            date_fetch = value.dateFetch,
            date_upload = value.dateUpload,
            chapter_number = value.chapterNumber,
            source_order = value.sourceOrder,
            last_modified_at = value.lastModifiedAt,
            version = value.version,
            memo_json = value.memoJson,
            id = value.id,
        )
    }

    override fun upsertCategory(value: CategoryRecord): Long {
        queries.insertCategory(value.name, value.flags)
        val id = checkNotNull(queries.selectCategoryByName(value.name).executeAsOneOrNull()).id
        queries.touchCategoryTimestamp(id, System.currentTimeMillis())
        return id
    }

    override fun deleteCategory(categoryId: Long) {
        queries.deleteCategory(categoryId)
    }

    override fun updateCategoryName(categoryId: Long, name: String) {
        queries.updateCategoryName(name, categoryId)
        queries.touchCategoryTimestamp(categoryId, System.currentTimeMillis())
    }

    override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) {
        queries.updateCategoryOrder(sortOrder, categoryId)
        queries.touchCategoryTimestamp(categoryId, System.currentTimeMillis())
    }

    override fun linkCategory(mangaId: Long, categoryId: Long) {
        queries.linkMangaCategory(mangaId, categoryId)
    }

    override fun unlinkCategory(mangaId: Long, categoryId: Long) {
        queries.unlinkCategory(mangaId, categoryId)
    }

    override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        database.transaction {
            queries.clearCategoriesForManga(mangaId)
            categoryIds.forEach { categoryId ->
                queries.linkMangaCategory(mangaId, categoryId)
            }
        }
    }

    override fun upsertHistory(value: HistoryRecord) {
        database.transaction {
            queries.upsertHistory(value.chapterId, value.lastRead, value.readDuration)
            queries.touchChapterLastModified(value.chapterId, value.lastRead)
        }
    }

    override fun deleteHistory(chapterId: Long) {
        queries.deleteHistoryByChapterId(chapterId)
    }

    override fun clearAllHistory() {
        queries.clearAllHistory()
    }

    override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? =
        queries.selectTrackingByIdentity(mangaId, trackerId).executeAsOneOrNull()?.toTrackingRecord()

    override fun insertTracking(value: TrackingRecord) {
        queries.insertTracking(
            manga_id = value.mangaId,
            tracker_id = value.trackerId,
            remote_id = value.remoteId,
            library_id = value.libraryId,
            title = value.title,
            last_chapter_read = value.lastChapterRead,
            total_chapters = value.totalChapters,
            score = value.score,
            status = value.status,
            started_reading_date = value.startedReadingDate,
            finished_reading_date = value.finishedReadingDate,
            private = value.private,
            tracking_url = value.trackingUrl,
        )
        queries.touchMangaLastModified(value.mangaId, System.currentTimeMillis())
    }

    override fun updateTracking(value: TrackingRecord) {
        queries.updateTracking(
            remote_id = value.remoteId,
            library_id = value.libraryId,
            title = value.title,
            last_chapter_read = value.lastChapterRead,
            total_chapters = value.totalChapters,
            score = value.score,
            status = value.status,
            started_reading_date = value.startedReadingDate,
            finished_reading_date = value.finishedReadingDate,
            private = value.private,
            tracking_url = value.trackingUrl,
            id = value.id,
        )
        queries.touchMangaLastModified(value.mangaId, System.currentTimeMillis())
    }

    override fun deleteTracking(mangaId: Long, trackerId: Long) {
        queries.deleteTracking(mangaId, trackerId)
        queries.touchMangaLastModified(mangaId, System.currentTimeMillis())
    }

    override fun upsertSource(value: SourceRecord) {
        queries.upsertSourceMetadata(value.sourceId, value.name, value.importedAt)
    }

    override fun upsertPreference(value: PreferenceSnapshotRecord) {
        queries.upsertPreferenceSnapshot(value.key, value.valueType, value.valueJson, value.importedAt)
    }

    override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) {
        queries.upsertSourcePreferenceSnapshot(
            value.sourceKey,
            value.key,
            value.valueType,
            value.valueJson,
            value.importedAt,
        )
    }

    override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? =
        queries.selectLocalMangaByManifest(manifestSha256).executeAsOneOrNull()?.let { local ->
            LocalMangaRecord(
                mangaId = local.manga_id,
                storagePath = local.storage_path,
                manifestSha256 = local.manifest_sha256,
                importedAt = local.imported_at,
            )
        }

    override fun localMangaStoragePaths(): Set<String> =
        queries.selectLocalMangaStoragePaths().executeAsList().toSet()

    override fun importedMangaStoragePaths(): Set<String> =
        queries.selectLocalMangaStoragePathsBySource(mihon.desktop.library.local.LOCAL_SOURCE_ID)
            .executeAsList().toSet()

    override fun insertLocalManga(value: LocalMangaRecord) {
        database.transaction {
            queries.preserveDownloadedChapterStorage(value.mangaId)
            queries.insertLocalMangaEntry(value.mangaId, value.storagePath, value.manifestSha256, value.importedAt)
        }
    }

    override fun insertLocalChapter(value: LocalChapterRecord) {
        database.transaction {
            queries.insertLocalChapterAsset(
                value.chapterId,
                value.relativePath,
                value.assetKind,
                value.sizeBytes,
                value.modifiedAt,
            )
            queries.registerDownloadedChapterStorage(value.chapterId)
        }
    }

    override fun isLocalChapterAssetRegistered(
        mangaId: Long,
        storagePath: String,
        chapterId: Long,
        relativePath: String,
        sizeBytes: Long,
        assetKind: String,
    ): Boolean = queries.isLocalChapterAssetRegistered(
        chapter_id = chapterId,
        manga_id = mangaId,
        storage_path = storagePath,
        relative_path = relativePath,
        asset_kind = assetKind,
        size_bytes = sizeBytes,
    ).executeAsOne()

    override fun deleteLocalChapterAsset(mangaId: Long, chapterId: Long) {
        database.transaction {
            queries.deleteLocalChapterAsset(chapterId)
            queries.deleteLocalMangaEntryIfUnused(mangaId)
        }
    }

    override fun insertReport(value: ImportReportRecord): Long = database.transactionWithResult {
        queries.insertImportReport(
            import_type = value.importType.name,
            source_path = value.sourcePath,
            status = value.status.name,
            started_at = value.startedAt,
            finished_at = value.finishedAt,
            manga_inserted = value.counts.mangaInserted,
            manga_merged = value.counts.mangaMerged,
            chapters_inserted = value.counts.chaptersInserted,
            chapters_merged = value.counts.chaptersMerged,
            categories_linked = value.counts.categoriesLinked,
            preferences_imported = value.counts.preferencesImported,
            preferences_skipped = value.counts.preferencesSkipped,
        )
        lastInsertRowId()
    }

    override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) {
        queries.insertImportReportItem(
            report_id = reportId,
            item_type = value.itemType,
            item_key = value.itemKey,
            outcome = value.outcome,
            reason = value.reason,
            message = value.message,
        )
    }

    private fun lastInsertRowId(): Long = queries.lastInsertRowId().executeAsOne()
}
