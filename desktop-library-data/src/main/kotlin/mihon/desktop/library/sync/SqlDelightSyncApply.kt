package mihon.desktop.library.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.desktop.library.backup.BackupMergePolicy
import mihon.desktop.library.backup.memoJson
import mihon.desktop.library.backup.mergeWith
import mihon.desktop.library.backup.toChapterRecord
import mihon.desktop.library.backup.toMangaRecord
import mihon.desktop.library.backup.toTrackingRecord
import mihon.desktop.library.db.DesktopLibraryDatabase
import mihon.desktop.library.db.Tracking
import mihon.desktop.library.db.toChapterRecord
import mihon.desktop.library.db.toMangaRecord
import mihon.desktop.library.db.toTrackingRecord
import mihon.desktop.library.model.MangaRecord
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.AndroidUpdateStrategy
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.engine.SyncOverriddenItem
import mihon.sync.engine.SyncStateStore
import java.util.Locale
import java.util.UUID

internal suspend fun SqlDelightSyncLocalRepository.applyChangesetInternal(
    changeset: Changeset,
): SyncApplyResult = withContext(Dispatchers.IO) {
    val localDeviceId = localDeviceId()
    database.transactionWithResult {
        var mangaInserted = 0
        var mangaMerged = 0
        var chapterInserted = 0
        var chapterMerged = 0
        var categoriesLinked = 0
        val overriddenItems = mutableListOf<SyncOverriddenItem>()

        // 1. Categories
        val databaseCategoryIdByName = mutableMapOf<String, Long>()
        for (c in queries.selectAllCategories().executeAsList()) {
            databaseCategoryIdByName[c.name.lowercase(Locale.ROOT)] = c.id
        }
        for (cat in changeset.upserts.categories) {
            val key = cat.name.lowercase(Locale.ROOT)
            val existingId = databaseCategoryIdByName[key]
            if (existingId == null) {
                // A category new to this device takes the next free position here, not the peer's.
                queries.insertCategory(cat.name, cat.flags)
                val newId = queries.selectCategoryByName(cat.name).executeAsOne().id
                queries.touchCategoryTimestamp(newId, cat.lastModifiedAt)
                databaseCategoryIdByName[key] = newId
            } else {
                val currentFlags = queries.selectAllCategories().executeAsList()
                    .firstOrNull { it.id == existingId }?.flags
                if (currentFlags != null && currentFlags != cat.flags) {
                    queries.updateCategoryFlags(cat.flags, existingId)
                }
                queries.touchCategoryTimestamp(existingId, cat.lastModifiedAt)
            }
        }

        val incomingCategoryByOrder = changeset.upserts.categories.associateBy { it.order }

        // 2. Mangas
        for (incomingManga in changeset.upserts.mangas) {
            if (incomingManga.source == 0L) continue // skip LocalSource

            val existing = queries.selectMangaByIdentity(
                incomingManga.source,
                incomingManga.url,
            ).executeAsOneOrNull()?.toMangaRecord()
            val mangaId: Long
            val incomingRecord = incomingManga.toMangaRecord(memoJson(incomingManga.memo))
            val incomingDeviceId = SyncMergePolicy.extractDeviceIdFromMemoJson(incomingRecord.memoJson).ifBlank {
                changeset.deviceId
            }

            if (existing == null) {
                val memoWithOrigin = SyncMergePolicy.injectDeviceIdIntoMemoJson(
                    incomingRecord.memoJson,
                    incomingDeviceId,
                )
                queries.insertManga(
                    source_id = incomingRecord.sourceId,
                    url = incomingRecord.url,
                    title = incomingRecord.title,
                    artist = incomingRecord.artist,
                    author = incomingRecord.author,
                    description = incomingRecord.description,
                    genre_json = incomingRecord.genreJson,
                    status = incomingRecord.status,
                    thumbnail_url = incomingRecord.thumbnailUrl,
                    favorite = incomingRecord.favorite,
                    date_added = incomingRecord.dateAdded,
                    viewer_flags = incomingRecord.viewerFlags,
                    chapter_flags = incomingRecord.chapterFlags,
                    update_strategy = incomingRecord.updateStrategy,
                    last_modified_at = incomingRecord.lastModifiedAt,
                    favorite_modified_at = incomingRecord.favoriteModifiedAt,
                    excluded_scanlators_json = incomingRecord.excludedScanlatorsJson,
                    version = incomingRecord.version,
                    notes = incomingRecord.notes,
                    initialized = incomingRecord.initialized,
                    memo_json = memoWithOrigin,
                )
                mangaId = queries.lastInsertRowId().executeAsOne()
                mangaInserted++
            } else {
                val existingDeviceId = SyncMergePolicy.extractDeviceIdFromMemoJson(existing.memoJson)
                val incomingWins = SyncMergePolicy.isIncomingWinner(
                    existingWatermark = existing.lastModifiedAt,
                    existingDeviceId = existingDeviceId,
                    incomingWatermark = incomingRecord.lastModifiedAt,
                    incomingDeviceId = incomingDeviceId,
                )
                if (incomingWins && existing.lastModifiedAt > 0) {
                    overriddenItems.add(
                        SyncOverriddenItem(
                            entityType = EntityType.MANGA,
                            entityKey = "${incomingManga.source}:${incomingManga.url}",
                            localWatermark = existing.lastModifiedAt,
                            remoteWatermark = incomingRecord.lastModifiedAt,
                            reason = "Remote manga metadata is newer",
                        ),
                    )
                }
                val merged = BackupMergePolicy.mergeManga(
                    existing = existing,
                    existingDeviceId = existingDeviceId,
                    incoming = incomingRecord,
                    incomingDeviceId = incomingDeviceId,
                )
                val winnerDeviceId = if (incomingWins) incomingDeviceId else existingDeviceId
                val finalMemoJson = if (winnerDeviceId.isNotBlank()) {
                    SyncMergePolicy.injectDeviceIdIntoMemoJson(merged.memoJson, winnerDeviceId)
                } else {
                    merged.memoJson
                }
                queries.updateManga(
                    source_id = merged.sourceId,
                    url = merged.url,
                    title = merged.title,
                    artist = merged.artist,
                    author = merged.author,
                    description = merged.description,
                    genre_json = merged.genreJson,
                    status = merged.status,
                    thumbnail_url = merged.thumbnailUrl,
                    favorite = merged.favorite,
                    date_added = merged.dateAdded,
                    viewer_flags = merged.viewerFlags,
                    chapter_flags = merged.chapterFlags,
                    update_strategy = merged.updateStrategy,
                    last_modified_at = merged.lastModifiedAt,
                    favorite_modified_at = merged.favoriteModifiedAt,
                    excluded_scanlators_json = merged.excludedScanlatorsJson,
                    version = merged.version,
                    notes = merged.notes,
                    initialized = merged.initialized,
                    memo_json = finalMemoJson,
                    id = existing.id,
                )
                mangaId = existing.id
                mangaMerged++
            }

            // Categories linking: the incoming list is the full membership, so stale links are removed.
            val targetCategoryIds = incomingManga.categories.mapNotNull { catOrder ->
                incomingCategoryByOrder[catOrder]?.let { cat ->
                    databaseCategoryIdByName[cat.name.lowercase(Locale.ROOT)]
                }
            }.toSet()
            val currentCategoryIds = queries.selectCategoriesForManga(mangaId).executeAsList().map { it.id }.toSet()
            if (targetCategoryIds != currentCategoryIds) {
                queries.clearCategoriesForManga(mangaId)
                targetCategoryIds.forEach { catId ->
                    queries.linkMangaCategory(manga_id = mangaId, category_id = catId)
                }
                categoriesLinked += (targetCategoryIds - currentCategoryIds).size
            }

            // Chapters
            for (incomingChapter in incomingManga.chapters) {
                val existingChap = queries.selectChapterByIdentity(
                    mangaId,
                    incomingChapter.url,
                ).executeAsOneOrNull()?.toChapterRecord()
                val incomingChapRecord = incomingChapter.toChapterRecord(mangaId, memoJson(incomingChapter.memo))
                val incomingChapDeviceId = SyncMergePolicy.extractDeviceIdFromMemoJson(
                    incomingChapRecord.memoJson,
                ).ifBlank {
                    changeset.deviceId
                }
                val chapterId: Long

                if (existingChap == null) {
                    val memoWithOrigin = SyncMergePolicy.injectDeviceIdIntoMemoJson(
                        incomingChapRecord.memoJson,
                        incomingChapDeviceId,
                    )
                    queries.insertChapter(
                        manga_id = mangaId,
                        url = incomingChapRecord.url,
                        name = incomingChapRecord.name,
                        scanlator = incomingChapRecord.scanlator,
                        read = incomingChapRecord.read,
                        bookmark = incomingChapRecord.bookmark,
                        last_page_read = incomingChapRecord.lastPageRead,
                        date_fetch = incomingChapRecord.dateFetch,
                        date_upload = incomingChapRecord.dateUpload,
                        chapter_number = incomingChapRecord.chapterNumber,
                        source_order = incomingChapRecord.sourceOrder,
                        last_modified_at = incomingChapRecord.lastModifiedAt,
                        version = incomingChapRecord.version,
                        memo_json = memoWithOrigin,
                    )
                    chapterId = queries.lastInsertRowId().executeAsOne()
                    chapterInserted++
                } else {
                    val existingChapDeviceId = SyncMergePolicy.extractDeviceIdFromMemoJson(existingChap.memoJson)
                    val incomingWins = SyncMergePolicy.isIncomingWinner(
                        existingWatermark = existingChap.lastModifiedAt,
                        existingDeviceId = existingChapDeviceId,
                        incomingWatermark = incomingChapRecord.lastModifiedAt,
                        incomingDeviceId = incomingChapDeviceId,
                    )
                    if (incomingWins && existingChap.lastModifiedAt > 0) {
                        overriddenItems.add(
                            SyncOverriddenItem(
                                entityType = EntityType.CHAPTER,
                                entityKey = "${incomingManga.source}:${incomingManga.url}::${incomingChapter.url}",
                                localWatermark = existingChap.lastModifiedAt,
                                remoteWatermark = incomingChapRecord.lastModifiedAt,
                                reason = "Remote chapter status/progress is newer",
                            ),
                        )
                    }
                    val mergedChap = BackupMergePolicy.mergeChapter(
                        existing = existingChap,
                        existingDeviceId = existingChapDeviceId,
                        incoming = incomingChapRecord,
                        incomingDeviceId = incomingChapDeviceId,
                    )
                    val winnerChapDeviceId = if (incomingWins) incomingChapDeviceId else existingChapDeviceId
                    val finalChapMemoJson = if (winnerChapDeviceId.isNotBlank()) {
                        SyncMergePolicy.injectDeviceIdIntoMemoJson(mergedChap.memoJson, winnerChapDeviceId)
                    } else {
                        mergedChap.memoJson
                    }
                    queries.updateChapter(
                        manga_id = mangaId,
                        url = mergedChap.url,
                        name = mergedChap.name,
                        scanlator = mergedChap.scanlator,
                        read = mergedChap.read,
                        bookmark = mergedChap.bookmark,
                        last_page_read = mergedChap.lastPageRead,
                        date_fetch = mergedChap.dateFetch,
                        date_upload = mergedChap.dateUpload,
                        chapter_number = mergedChap.chapterNumber,
                        source_order = mergedChap.sourceOrder,
                        last_modified_at = mergedChap.lastModifiedAt,
                        version = mergedChap.version,
                        memo_json = finalChapMemoJson,
                        id = existingChap.id,
                    )
                    chapterId = existingChap.id
                    chapterMerged++
                }

                // History
                val hist = incomingManga.history.firstOrNull { it.url == incomingChapter.url }
                if (hist != null) {
                    queries.upsertHistory(chapterId, hist.lastRead, hist.readDuration)
                    queries.touchChapterLastModified(chapterId, hist.lastRead)
                }
            }

            // Tracking
            for (incomingTrack in incomingManga.tracking) {
                val existingTrack = queries.selectTrackingByIdentity(
                    mangaId,
                    incomingTrack.syncId.toLong(),
                ).executeAsOneOrNull()?.toTrackingRecord()
                val incomingTrackRecord = incomingTrack.toTrackingRecord(mangaId)
                if (existingTrack == null) {
                    queries.insertTracking(
                        manga_id = mangaId,
                        tracker_id = incomingTrackRecord.trackerId,
                        remote_id = incomingTrackRecord.remoteId,
                        library_id = incomingTrackRecord.libraryId,
                        title = incomingTrackRecord.title,
                        last_chapter_read = incomingTrackRecord.lastChapterRead,
                        total_chapters = incomingTrackRecord.totalChapters,
                        score = incomingTrackRecord.score,
                        status = incomingTrackRecord.status,
                        started_reading_date = incomingTrackRecord.startedReadingDate,
                        finished_reading_date = incomingTrackRecord.finishedReadingDate,
                        private = incomingTrackRecord.private,
                        tracking_url = incomingTrackRecord.trackingUrl,
                    )
                } else {
                    val merged = existingTrack.mergeWith(incomingTrackRecord, localDeviceId, incomingDeviceId)
                    queries.updateTracking(
                        remote_id = merged.remoteId,
                        library_id = merged.libraryId,
                        title = merged.title,
                        last_chapter_read = merged.lastChapterRead,
                        total_chapters = merged.totalChapters,
                        score = merged.score,
                        status = merged.status,
                        started_reading_date = merged.startedReadingDate,
                        finished_reading_date = merged.finishedReadingDate,
                        private = merged.private,
                        tracking_url = merged.trackingUrl,
                        id = existingTrack.id,
                    )
                }
            }
        }

        SyncApplyResult(
            mangaInserted = mangaInserted,
            mangaMerged = mangaMerged,
            chapterInserted = chapterInserted,
            chapterMerged = chapterMerged,
            categoriesLinked = categoriesLinked,
            overriddenItems = overriddenItems,
        )
    }
}
