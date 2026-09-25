package mihon.desktop.library.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.backup.BackupMergePolicy
import mihon.desktop.library.backup.toRecord
import mihon.desktop.library.db.Chapter
import mihon.desktop.library.db.DesktopLibraryDatabase
import mihon.desktop.library.db.Manga
import mihon.desktop.library.db.Tracking
import mihon.desktop.library.db.toRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
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

class SqlDelightSyncStateStore(
    private val database: DesktopLibraryDatabase,
    private val defaultDeviceId: () -> String = { UUID.randomUUID().toString() },
) : SyncStateStore {
    private val queries = database.libraryQueries

    override suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        val existing = queries.selectMetadata("sync_device_id").executeAsOneOrNull()
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = defaultDeviceId()
            queries.upsertMetadata("sync_device_id", generated)
            generated
        }
    }

    override suspend fun getLastPushWatermark(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_push_watermark").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPushWatermark(watermark: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_push_watermark", watermark.toString())
        }
    }

    override suspend fun getLastPushCursor(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_push_cursor").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPushCursor(cursor: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_push_cursor", cursor.toString())
        }
    }

    override suspend fun getLastPullCursor(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_pull_cursor").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPullCursor(cursor: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_pull_cursor", cursor.toString())
        }
    }

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_peer_pull_$peerDeviceId").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_peer_pull_$peerDeviceId", watermark.toString())
        }
    }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> = withContext(Dispatchers.IO) {
        queries.selectAllMetadataWithPrefix("sync_peer_pull_%").executeAsList().associate {
            it.name.removePrefix("sync_peer_pull_") to (it.value_.toLongOrNull() ?: 0L)
        }
    }

    override suspend fun nextCursor(): Long = withContext(Dispatchers.IO) {
        database.transactionWithResult {
            val current = queries.selectMetadata("sync_clock").executeAsOneOrNull()?.toLongOrNull() ?: 0L
            val next = current + 1L
            queries.upsertMetadata("sync_clock", next.toString())
            next
        }
    }
}

class SqlDelightSyncLocalRepository(
    private val database: DesktopLibraryDatabase,
    private val json: Json = Json,
) : SyncLocalRepository {
    private val queries = database.libraryQueries

    override suspend fun exportDelta(sinceCursor: Long): EntityDelta = withContext(Dispatchers.IO) {
        val modifiedMangas = queries.selectMangaModifiedSince(sinceCursor).executeAsList().map { it.toRecord() }
        val modifiedChapters = queries.selectChaptersModifiedSince(sinceCursor).executeAsList()
        val modifiedMangaIds = modifiedMangas.map { it.id }.toMutableSet()
        val extraMangas = mutableListOf<MangaRecord>()

        for (chap in modifiedChapters) {
            if (modifiedMangaIds.add(chap.manga_id)) {
                queries.selectMangaById(chap.manga_id).executeAsOneOrNull()?.toRecord()?.let {
                    extraMangas.add(it)
                }
            }
        }

        val allTargetMangas = modifiedMangas + extraMangas
        val allCategories = queries.selectAllCategoriesForExport().executeAsList().map {
            AndroidBackupCategory(name = it.name, order = it.sort_order, flags = it.flags)
        }

        val localDeviceId = queries.selectMetadata("sync_device_id").executeAsOneOrNull() ?: ""

        val exportedMangas = allTargetMangas.map { manga ->
            val chapters = queries.selectChaptersForManga(manga.id).executeAsList().map { it.toRecord() }
            val backupChapters = chapters.map { ch ->
                val chapMemoJson = if (localDeviceId.isNotBlank() &&
                    SyncMergePolicy.extractDeviceIdFromMemoJson(ch.memoJson).isBlank()
                ) {
                    SyncMergePolicy.injectDeviceIdIntoMemoJson(ch.memoJson, localDeviceId)
                } else {
                    ch.memoJson
                }
                val memoBytes = try {
                    chapMemoJson.encodeToByteArray()
                } catch (_: Throwable) {
                    byteArrayOf(123, 125)
                }
                AndroidBackupChapter(
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.lastPageRead,
                    dateFetch = ch.dateFetch,
                    dateUpload = ch.dateUpload,
                    chapterNumber = ch.chapterNumber.toFloat(),
                    sourceOrder = ch.sourceOrder,
                    lastModifiedAt = ch.lastModifiedAt,
                    version = ch.version,
                    memo = memoBytes,
                )
            }

            val history = chapters.mapNotNull { ch ->
                queries.selectHistoryByChapter(ch.id).executeAsOneOrNull()?.let { h ->
                    AndroidBackupHistory(
                        url = ch.url,
                        lastRead = h.last_read,
                        readDuration = h.read_duration,
                    )
                }
            }

            val tracking = queries.selectTrackingForManga(manga.id).executeAsList().map { it.toRecord() }.map { t ->
                AndroidBackupTracking(
                    syncId = t.trackerId.toInt(),
                    libraryId = t.libraryId,
                    mediaId = t.remoteId,
                    trackingUrl = t.trackingUrl,
                    title = t.title,
                    lastChapterRead = t.lastChapterRead.toFloat(),
                    totalChapters = t.totalChapters.toInt(),
                    score = t.score.toFloat(),
                    status = t.status.toInt(),
                    startedReadingDate = t.startedReadingDate,
                    finishedReadingDate = t.finishedReadingDate,
                    private = t.private,
                )
            }

            val mangaCategories = queries.selectCategoriesForManga(manga.id).executeAsList()
            val categoryOrders = mangaCategories.map { it.sort_order }

            val genreList = try {
                json.decodeFromString<List<String>>(manga.genreJson)
            } catch (_: Exception) {
                emptyList()
            }
            val excludedList = try {
                json.decodeFromString<List<String>>(manga.excludedScanlatorsJson)
            } catch (
                _: Exception,
            ) {
                emptyList()
            }
            val mangaMemoJson = if (localDeviceId.isNotBlank() &&
                SyncMergePolicy.extractDeviceIdFromMemoJson(manga.memoJson).isBlank()
            ) {
                SyncMergePolicy.injectDeviceIdIntoMemoJson(manga.memoJson, localDeviceId)
            } else {
                manga.memoJson
            }
            val memoBytes = try {
                mangaMemoJson.encodeToByteArray()
            } catch (_: Throwable) {
                byteArrayOf(123, 125)
            }

            AndroidBackupManga(
                source = manga.sourceId,
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = genreList,
                status = manga.status.toInt(),
                thumbnailUrl = manga.thumbnailUrl,
                dateAdded = manga.dateAdded,
                viewer = manga.viewerFlags.toInt(),
                chapters = backupChapters,
                categories = categoryOrders,
                tracking = tracking,
                favorite = manga.favorite,
                chapterFlags = manga.chapterFlags.toInt(),
                viewerFlags = manga.viewerFlags.toInt(),
                history = history,
                updateStrategy = try {
                    AndroidUpdateStrategy.valueOf(manga.updateStrategy)
                } catch (
                    _: Exception,
                ) {
                    AndroidUpdateStrategy.ALWAYS_UPDATE
                },
                lastModifiedAt = manga.lastModifiedAt,
                favoriteModifiedAt = manga.favoriteModifiedAt,
                excludedScanlators = excludedList,
                version = manga.version,
                notes = manga.notes,
                initialized = manga.initialized,
                memo = memoBytes,
            )
        }

        EntityDelta(
            mangas = exportedMangas,
            categories = allCategories,
        )
    }

    override suspend fun exportTombstones(sinceCursor: Long): List<Tombstone> = emptyList()

    override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult = withContext(Dispatchers.IO) {
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
                    queries.insertCategory(cat.name, cat.order, cat.flags)
                    val newId = queries.selectCategoryByName(cat.name).executeAsOne().id
                    databaseCategoryIdByName[key] = newId
                }
            }

            val incomingCategoryByOrder = changeset.upserts.categories.associateBy { it.order }

            // 2. Mangas
            for (incomingManga in changeset.upserts.mangas) {
                if (incomingManga.source == 0L) continue // skip LocalSource

                val existing = queries.selectMangaByIdentity(
                    incomingManga.source,
                    incomingManga.url,
                ).executeAsOneOrNull()?.toRecord()
                val mangaId: Long
                val incomingRecord = incomingManga.toRecord()
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

                // Categories linking
                if (incomingManga.categories.isNotEmpty()) {
                    val currentCategoryIds = queries.selectCategoriesForManga(mangaId).executeAsList().map {
                        it.id
                    }.toSet()
                    for (catOrder in incomingManga.categories) {
                        val catName = incomingCategoryByOrder[catOrder]?.name
                        if (catName != null) {
                            val dbCatId = databaseCategoryIdByName[catName.lowercase(Locale.ROOT)]
                            if (dbCatId != null && dbCatId !in currentCategoryIds) {
                                queries.linkMangaCategory(manga_id = mangaId, category_id = dbCatId)
                                categoriesLinked++
                            }
                        }
                    }
                }

                // Chapters
                for (incomingChapter in incomingManga.chapters) {
                    val existingChap = queries.selectChapterByIdentity(
                        mangaId,
                        incomingChapter.url,
                    ).executeAsOneOrNull()?.toRecord()
                    val incomingChapRecord = incomingChapter.toRecord(mangaId)
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
                    ).executeAsOneOrNull()?.toRecord()
                    val incomingTrackRecord = incomingTrack.toRecord(mangaId)
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
                        val merged = BackupMergePolicy.mergeTracking(existingTrack, incomingTrackRecord)
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

    private fun AndroidBackupManga.toRecord(): MangaRecord {
        val memoStr = try {
            memo.decodeToString()
        } catch (_: Throwable) {
            "{}"
        }
        val genreJsonStr = JsonArray(
            genre.sortedWith(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR).map(::JsonPrimitive),
        ).toString()
        val excludedJsonStr = JsonArray(
            excludedScanlators.sortedWith(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR).map(::JsonPrimitive),
        ).toString()
        return MangaRecord(
            sourceId = source,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genreJson = genreJsonStr,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            favorite = favorite,
            dateAdded = dateAdded,
            viewerFlags = (viewerFlags ?: viewer).toLong(),
            chapterFlags = chapterFlags.toLong(),
            updateStrategy = updateStrategy.name,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            excludedScanlatorsJson = excludedJsonStr,
            version = version,
            notes = notes,
            initialized = initialized,
            memoJson = memoStr,
        )
    }

    private fun AndroidBackupChapter.toRecord(mangaId: Long): ChapterRecord {
        val memoStr = try {
            memo.decodeToString()
        } catch (_: Throwable) {
            "{}"
        }
        return ChapterRecord(
            mangaId = mangaId,
            url = url,
            name = name,
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber.toDouble(),
            sourceOrder = sourceOrder,
            lastModifiedAt = lastModifiedAt,
            version = version,
            memoJson = memoStr,
        )
    }

    @Suppress("DEPRECATION")
    private fun AndroidBackupTracking.toRecord(mangaId: Long): TrackingRecord = TrackingRecord(
        mangaId = mangaId,
        trackerId = syncId.toLong(),
        remoteId = if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead.toDouble(),
        totalChapters = totalChapters.toLong(),
        score = score.toDouble(),
        status = status.toLong(),
        startedReadingDate = startedReadingDate,
        finishedReadingDate = finishedReadingDate,
        private = private,
        trackingUrl = trackingUrl,
    )

    private fun Manga.toRecord() = MangaRecord(
        id = id,
        sourceId = source_id,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genreJson = genre_json,
        status = status,
        thumbnailUrl = thumbnail_url,
        favorite = favorite,
        dateAdded = date_added,
        viewerFlags = viewer_flags,
        chapterFlags = chapter_flags,
        updateStrategy = update_strategy,
        lastModifiedAt = last_modified_at,
        favoriteModifiedAt = favorite_modified_at,
        excludedScanlatorsJson = excluded_scanlators_json,
        version = version,
        notes = notes,
        initialized = initialized,
        memoJson = memo_json,
    )

    private fun Chapter.toRecord() = ChapterRecord(
        id = id,
        mangaId = manga_id,
        url = url,
        name = name,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = last_page_read,
        dateFetch = date_fetch,
        dateUpload = date_upload,
        chapterNumber = chapter_number,
        sourceOrder = source_order,
        lastModifiedAt = last_modified_at,
        version = version,
        memoJson = memo_json,
    )

    private fun Tracking.toRecord() = TrackingRecord(
        id = id,
        mangaId = manga_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read,
        totalChapters = total_chapters,
        score = score,
        status = status,
        startedReadingDate = started_reading_date,
        finishedReadingDate = finished_reading_date,
        private = private_,
        trackingUrl = tracking_url,
    )
}
