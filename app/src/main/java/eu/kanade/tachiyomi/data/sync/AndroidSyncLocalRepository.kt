package eu.kanade.tachiyomi.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import tachiyomi.data.Database
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import java.util.Date
import java.util.Locale

class AndroidSyncLocalRepository(
    private val database: Database,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : SyncLocalRepository {

    override suspend fun exportDelta(sinceCursor: Long): EntityDelta = withContext(Dispatchers.IO) {
        val modifiedMangas = database.mangasQueries.selectMangaModifiedSince(sinceCursor).awaitAsList()
        val modifiedChapters = database.chaptersQueries.selectChaptersModifiedSince(sinceCursor).awaitAsList()
        val modifiedMangaIds = modifiedMangas.map { it._id }.toMutableSet()
        val extraMangas = mutableListOf<tachiyomi.data.Mangas>()

        for (chap in modifiedChapters) {
            if (modifiedMangaIds.add(chap.manga_id)) {
                database.mangasQueries.getMangaById(chap.manga_id).awaitAsOneOrNull()?.let {
                    extraMangas.add(it)
                }
            }
        }

        val allTargetMangas = modifiedMangas + extraMangas
        val allCategories = database.categoriesQueries.getCategories().awaitAsList().map {
            AndroidBackupCategory(
                name = it.name,
                order = it.order,
                flags = it.flags,
            )
        }

        val localDeviceId = database.sync_stateQueries.getFirstSyncState().awaitAsOneOrNull()?.device_id ?: ""

        val exportedMangas = allTargetMangas.filter { it.source != 0L }.map { manga ->
            val chapters = database.chaptersQueries.getChaptersByMangaId(manga._id, 0).awaitAsList()
            val backupChapters = chapters.map { ch ->
                val chapMemoJson = ch.memo.toString()
                val effectiveChapMemoJson = if (localDeviceId.isNotBlank() &&
                    SyncMergePolicy.extractDeviceIdFromMemoJson(chapMemoJson).isBlank()
                ) {
                    SyncMergePolicy.injectDeviceIdIntoMemoJson(chapMemoJson, localDeviceId)
                } else {
                    chapMemoJson
                }
                val memoBytes = try {
                    effectiveChapMemoJson.encodeToByteArray()
                } catch (_: Throwable) {
                    byteArrayOf(123, 125)
                }
                AndroidBackupChapter(
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.last_page_read,
                    dateFetch = ch.date_fetch,
                    dateUpload = ch.date_upload,
                    chapterNumber = ch.chapter_number.toFloat(),
                    sourceOrder = ch.source_order,
                    lastModifiedAt = ch.last_modified_at,
                    version = ch.version,
                    memo = memoBytes,
                )
            }

            val history = chapters.mapNotNull { ch ->
                database.historyQueries.getHistoryByChapterUrlAndMangaId(
                    chapterUrl = ch.url,
                    mangaId = manga._id,
                ).awaitAsOneOrNull()?.let { h ->
                    AndroidBackupHistory(
                        url = ch.url,
                        lastRead = h.last_read?.time ?: 0L,
                        readDuration = h.time_read,
                    )
                }
            }

            val tracking = database.manga_syncQueries.getTracksByMangaId(manga._id).awaitAsList().map { t ->
                AndroidBackupTracking(
                    syncId = t.sync_id.toInt(),
                    libraryId = t.library_id ?: 0L,
                    mediaId = t.remote_id,
                    trackingUrl = t.remote_url,
                    title = t.title,
                    lastChapterRead = t.last_chapter_read.toFloat(),
                    totalChapters = t.total_chapters.toInt(),
                    score = t.score.toFloat(),
                    status = t.status.toInt(),
                    startedReadingDate = t.start_date,
                    finishedReadingDate = t.finish_date,
                    private = t.private_,
                )
            }

            val mangaCategories = database.categoriesQueries.getCategoriesByMangaId(manga._id).awaitAsList()
            val categoryOrders = mangaCategories.map { it.order }

            val excludedScanlators = database.excluded_scanlatorsQueries
                .getExcludedScanlatorsByMangaId(manga._id)
                .awaitAsList()

            val mangaMemoJson = manga.memo.toString()
            val effectiveMangaMemoJson = if (localDeviceId.isNotBlank() &&
                SyncMergePolicy.extractDeviceIdFromMemoJson(mangaMemoJson).isBlank()
            ) {
                SyncMergePolicy.injectDeviceIdIntoMemoJson(mangaMemoJson, localDeviceId)
            } else {
                mangaMemoJson
            }
            val memoBytes = try {
                effectiveMangaMemoJson.encodeToByteArray()
            } catch (_: Throwable) {
                byteArrayOf(123, 125)
            }

            AndroidBackupManga(
                source = manga.source,
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre ?: emptyList(),
                status = manga.status.toInt(),
                thumbnailUrl = manga.thumbnail_url,
                dateAdded = manga.date_added,
                viewer = manga.viewer.toInt(),
                chapters = backupChapters,
                categories = categoryOrders,
                tracking = tracking,
                favorite = manga.favorite,
                chapterFlags = manga.chapter_flags.toInt(),
                viewerFlags = manga.viewer.toInt(),
                history = history,
                updateStrategy = try {
                    AndroidUpdateStrategy.valueOf(manga.update_strategy.name)
                } catch (_: Exception) {
                    AndroidUpdateStrategy.ALWAYS_UPDATE
                },
                lastModifiedAt = manga.last_modified_at,
                favoriteModifiedAt = manga.favorite_modified_at,
                excludedScanlators = excludedScanlators,
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

    // Deletion sync / tombstone export is not yet supported in this round
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
            val databaseCategoryByName = mutableMapOf<String, Long>()
            for (c in database.categoriesQueries.getCategories().awaitAsList()) {
                databaseCategoryByName[c.name.lowercase(Locale.ROOT)] = c.id
            }
            for (cat in changeset.upserts.categories) {
                val key = cat.name.lowercase(Locale.ROOT)
                val existingId = databaseCategoryByName[key]
                if (existingId == null) {
                    val nextOrder = (databaseCategoryByName.values.maxOrNull() ?: 0L) + 1L
                    database.categoriesQueries.insert(
                        name = cat.name,
                        order = if (cat.order > 0) cat.order else nextOrder,
                        flags = cat.flags,
                    )
                    val created = database.categoriesQueries.getCategoryByName(cat.name).awaitAsOneOrNull()
                    if (created != null) {
                        databaseCategoryByName[key] = created._id
                    }
                }
            }

            // Map incoming category order to database category id
            val incomingCategoryByOrder = changeset.upserts.categories.associateBy { it.order }

            // 2. Mangas
            for (incomingManga in changeset.upserts.mangas) {
                if (incomingManga.source == 0L) continue // skip LocalSource

                val existing = database.mangasQueries.getMangaByUrlAndSource(
                    url = incomingManga.url,
                    source = incomingManga.source,
                ).awaitAsOneOrNull()

                val mangaId: Long
                val incomingDeviceId = SyncMergePolicy.extractDeviceIdFromMemoBytes(incomingManga.memo).ifBlank {
                    changeset.deviceId
                }
                if (existing == null) {
                    val incomingMemoWithOrigin = if (incomingDeviceId.isNotBlank()) {
                        SyncMergePolicy.injectDeviceIdIntoMemoBytes(incomingManga.memo, incomingDeviceId)
                    } else {
                        incomingManga.memo
                    }
                    val memoObj = try {
                        json.decodeFromString<JsonObject>(incomingMemoWithOrigin.decodeToString())
                    } catch (_: Throwable) {
                        JsonObject(emptyMap())
                    }
                    val updateStrategy = try {
                        UpdateStrategy.valueOf(incomingManga.updateStrategy.name)
                    } catch (_: Throwable) {
                        UpdateStrategy.ALWAYS_UPDATE
                    }
                    mangaId = database.mangasQueries.insertReturningId(
                        source = incomingManga.source,
                        url = incomingManga.url,
                        artist = incomingManga.artist,
                        author = incomingManga.author,
                        description = incomingManga.description,
                        genre = incomingManga.genre,
                        title = incomingManga.title,
                        status = incomingManga.status.toLong(),
                        thumbnailUrl = incomingManga.thumbnailUrl,
                        favorite = incomingManga.favorite,
                        lastUpdate = 0L,
                        nextUpdate = 0L,
                        initialized = incomingManga.initialized,
                        viewerFlags = (incomingManga.viewerFlags ?: incomingManga.viewer).toLong(),
                        chapterFlags = incomingManga.chapterFlags.toLong(),
                        coverLastModified = 0L,
                        dateAdded = incomingManga.dateAdded,
                        updateStrategy = updateStrategy,
                        calculateInterval = 0L,
                        version = incomingManga.version,
                        notes = incomingManga.notes,
                        memo = memoObj,
                    ).awaitAsOne()

                    database.mangasQueries.update(
                        source = null,
                        url = null,
                        artist = null,
                        author = null,
                        description = null,
                        genre = null,
                        title = null,
                        status = null,
                        thumbnailUrl = null,
                        favorite = null,
                        lastUpdate = null,
                        nextUpdate = null,
                        initialized = null,
                        viewer = null,
                        chapterFlags = null,
                        coverLastModified = null,
                        dateAdded = null,
                        updateStrategy = null,
                        calculateInterval = null,
                        version = incomingManga.version,
                        isSyncing = 1L,
                        notes = null,
                        memo = null,
                        mangaId = mangaId,
                    )
                    database.mangasQueries.touchMangaLastModified(incomingManga.lastModifiedAt, mangaId)
                    if (incomingManga.favoriteModifiedAt != null) {
                        database.mangasQueries.setFavoriteModifiedAt(incomingManga.favoriteModifiedAt, mangaId)
                    }
                    mangaInserted++
                } else {
                    val existingMemoBytes = try {
                        existing.memo.toString().encodeToByteArray()
                    } catch (_: Throwable) {
                        byteArrayOf(123, 125)
                    }
                    val existingDeviceId = SyncMergePolicy.extractDeviceIdFromMemoBytes(existingMemoBytes)

                    val incomingWins = SyncMergePolicy.isIncomingWinner(
                        existingWatermark = existing.last_modified_at,
                        existingDeviceId = existingDeviceId,
                        incomingWatermark = incomingManga.lastModifiedAt,
                        incomingDeviceId = incomingDeviceId,
                    )
                    if (incomingWins && existing.last_modified_at > 0) {
                        overriddenItems.add(
                            SyncOverriddenItem(
                                entityType = EntityType.MANGA,
                                entityKey = "${incomingManga.source}:${incomingManga.url}",
                                localWatermark = existing.last_modified_at,
                                remoteWatermark = incomingManga.lastModifiedAt,
                                reason = "Remote manga metadata is newer",
                            ),
                        )
                    }

                    val existingBackupManga = AndroidBackupManga(
                        source = existing.source,
                        url = existing.url,
                        title = existing.title,
                        artist = existing.artist,
                        author = existing.author,
                        description = existing.description,
                        genre = existing.genre ?: emptyList(),
                        status = existing.status.toInt(),
                        thumbnailUrl = existing.thumbnail_url,
                        dateAdded = existing.date_added,
                        viewer = existing.viewer.toInt(),
                        favorite = existing.favorite,
                        chapterFlags = existing.chapter_flags.toInt(),
                        viewerFlags = existing.viewer.toInt(),
                        updateStrategy = try {
                            AndroidUpdateStrategy.valueOf(existing.update_strategy.name)
                        } catch (_: Exception) {
                            AndroidUpdateStrategy.ALWAYS_UPDATE
                        },
                        lastModifiedAt = existing.last_modified_at,
                        favoriteModifiedAt = existing.favorite_modified_at,
                        version = existing.version,
                        notes = existing.notes,
                        initialized = existing.initialized,
                        memo = existingMemoBytes,
                    )
                    val merged = SyncMergePolicy.mergeManga(
                        existing = existingBackupManga,
                        existingDeviceId = existingDeviceId,
                        incoming = incomingManga,
                        incomingDeviceId = incomingDeviceId,
                    )
                    val winnerDeviceId = if (incomingWins) incomingDeviceId else existingDeviceId
                    val finalMemoBytes = if (winnerDeviceId.isNotBlank()) {
                        SyncMergePolicy.injectDeviceIdIntoMemoBytes(merged.memo, winnerDeviceId)
                    } else {
                        merged.memo
                    }
                    val mergedMemo = try {
                        json.decodeFromString<JsonObject>(finalMemoBytes.decodeToString())
                    } catch (_: Throwable) {
                        existing.memo
                    }
                    val mergedStrategy = try {
                        UpdateStrategy.valueOf(merged.updateStrategy.name)
                    } catch (_: Throwable) {
                        existing.update_strategy
                    }

                    database.mangasQueries.update(
                        source = merged.source,
                        url = merged.url,
                        artist = merged.artist,
                        author = merged.author,
                        description = merged.description,
                        genre = merged.genre.takeUnless { it.isEmpty() }?.let(StringListColumnAdapter::encode),
                        title = merged.title,
                        status = merged.status.toLong(),
                        thumbnailUrl = merged.thumbnailUrl,
                        favorite = merged.favorite,
                        lastUpdate = null,
                        nextUpdate = null,
                        initialized = merged.initialized,
                        viewer = (merged.viewerFlags ?: merged.viewer).toLong(),
                        chapterFlags = merged.chapterFlags.toLong(),
                        coverLastModified = null,
                        dateAdded = merged.dateAdded,
                        updateStrategy = mergedStrategy.let(UpdateStrategyColumnAdapter::encode),
                        calculateInterval = null,
                        version = merged.version,
                        isSyncing = 1L,
                        notes = merged.notes,
                        memo = mergedMemo.let(MemoColumnAdapter::encode),
                        mangaId = existing._id,
                    )
                    database.mangasQueries.touchMangaLastModified(merged.lastModifiedAt, existing._id)
                    if (merged.favoriteModifiedAt != null) {
                        database.mangasQueries.setFavoriteModifiedAt(merged.favoriteModifiedAt, existing._id)
                    }
                    mangaId = existing._id
                    mangaMerged++
                }

                // Excluded scanlators
                if (incomingManga.excludedScanlators.isNotEmpty()) {
                    val existingExcluded = database.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(
                        mangaId,
                    ).awaitAsList().toSet()
                    for (scanlator in incomingManga.excludedScanlators) {
                        if (scanlator !in existingExcluded) {
                            database.excluded_scanlatorsQueries.insert(mangaId, scanlator)
                        }
                    }
                }

                // Categories linking
                if (incomingManga.categories.isNotEmpty()) {
                    val currentCategoryIds = database.categoriesQueries.getCategoriesByMangaId(
                        mangaId,
                    ).awaitAsList().map {
                        it.id
                    }.toSet()
                    for (catOrder in incomingManga.categories) {
                        val catName = incomingCategoryByOrder[catOrder]?.name
                        if (catName != null) {
                            val dbCatId = databaseCategoryByName[catName.lowercase(Locale.ROOT)]
                            if (dbCatId != null && dbCatId !in currentCategoryIds) {
                                database.mangas_categoriesQueries.insert(mangaId, dbCatId)
                                categoriesLinked++
                            }
                        }
                    }
                }

                // Chapters
                for (incomingChapter in incomingManga.chapters) {
                    val existingChap = database.chaptersQueries.getChapterByUrlAndMangaId(
                        chapterUrl = incomingChapter.url,
                        mangaId = mangaId,
                    ).awaitAsOneOrNull()

                    val chapterId: Long
                    val incomingChapDeviceId = SyncMergePolicy.extractDeviceIdFromMemoBytes(
                        incomingChapter.memo,
                    ).ifBlank {
                        changeset.deviceId
                    }

                    if (existingChap == null) {
                        val incomingChapMemoWithOrigin = if (incomingChapDeviceId.isNotBlank()) {
                            SyncMergePolicy.injectDeviceIdIntoMemoBytes(incomingChapter.memo, incomingChapDeviceId)
                        } else {
                            incomingChapter.memo
                        }
                        val chapMemoObj = try {
                            json.decodeFromString<JsonObject>(incomingChapMemoWithOrigin.decodeToString())
                        } catch (_: Throwable) {
                            JsonObject(emptyMap())
                        }

                        chapterId = database.chaptersQueries.insertReturningId(
                            mangaId = mangaId,
                            url = incomingChapter.url,
                            name = incomingChapter.name,
                            scanlator = incomingChapter.scanlator,
                            read = incomingChapter.read,
                            bookmark = incomingChapter.bookmark,
                            lastPageRead = incomingChapter.lastPageRead,
                            chapterNumber = incomingChapter.chapterNumber.toDouble(),
                            sourceOrder = incomingChapter.sourceOrder,
                            dateFetch = incomingChapter.dateFetch,
                            dateUpload = incomingChapter.dateUpload,
                            version = incomingChapter.version,
                            memo = chapMemoObj,
                        ).awaitAsOne()

                        database.chaptersQueries.update(
                            mangaId = null,
                            url = null,
                            name = null,
                            scanlator = null,
                            read = null,
                            bookmark = null,
                            lastPageRead = null,
                            chapterNumber = null,
                            sourceOrder = null,
                            dateFetch = null,
                            dateUpload = null,
                            version = incomingChapter.version,
                            isSyncing = 1L,
                            memo = null,
                            chapterId = chapterId,
                        )
                        database.chaptersQueries.touchChapterLastModified(incomingChapter.lastModifiedAt, chapterId)
                        chapterInserted++
                    } else {
                        val existingChapMemoBytes = try {
                            existingChap.memo.toString().encodeToByteArray()
                        } catch (_: Throwable) {
                            byteArrayOf(123, 125)
                        }
                        val existingChapDeviceId = SyncMergePolicy.extractDeviceIdFromMemoBytes(existingChapMemoBytes)

                        val incomingWins = SyncMergePolicy.isIncomingWinner(
                            existingWatermark = existingChap.last_modified_at,
                            existingDeviceId = existingChapDeviceId,
                            incomingWatermark = incomingChapter.lastModifiedAt,
                            incomingDeviceId = incomingChapDeviceId,
                        )
                        if (incomingWins && existingChap.last_modified_at > 0) {
                            overriddenItems.add(
                                SyncOverriddenItem(
                                    entityType = EntityType.CHAPTER,
                                    entityKey = "${incomingManga.source}:${incomingManga.url}::${incomingChapter.url}",
                                    localWatermark = existingChap.last_modified_at,
                                    remoteWatermark = incomingChapter.lastModifiedAt,
                                    reason = "Remote chapter status/progress is newer",
                                ),
                            )
                        }

                        val existingBackupChap = AndroidBackupChapter(
                            url = existingChap.url,
                            name = existingChap.name,
                            scanlator = existingChap.scanlator,
                            read = existingChap.read,
                            bookmark = existingChap.bookmark,
                            lastPageRead = existingChap.last_page_read,
                            dateFetch = existingChap.date_fetch,
                            dateUpload = existingChap.date_upload,
                            chapterNumber = existingChap.chapter_number.toFloat(),
                            sourceOrder = existingChap.source_order,
                            lastModifiedAt = existingChap.last_modified_at,
                            version = existingChap.version,
                            memo = existingChapMemoBytes,
                        )
                        val mergedChap = SyncMergePolicy.mergeChapter(
                            existing = existingBackupChap,
                            existingDeviceId = existingChapDeviceId,
                            incoming = incomingChapter,
                            incomingDeviceId = incomingChapDeviceId,
                        )
                        val winnerChapDeviceId = if (incomingWins) incomingChapDeviceId else existingChapDeviceId
                        val finalChapMemoBytes = if (winnerChapDeviceId.isNotBlank()) {
                            SyncMergePolicy.injectDeviceIdIntoMemoBytes(mergedChap.memo, winnerChapDeviceId)
                        } else {
                            mergedChap.memo
                        }
                        val mergedChapMemo = try {
                            json.decodeFromString<JsonObject>(finalChapMemoBytes.decodeToString())
                        } catch (_: Throwable) {
                            existingChap.memo
                        }

                        database.chaptersQueries.update(
                            mangaId = null,
                            url = mergedChap.url,
                            name = mergedChap.name,
                            scanlator = mergedChap.scanlator,
                            read = mergedChap.read,
                            bookmark = mergedChap.bookmark,
                            lastPageRead = mergedChap.lastPageRead,
                            chapterNumber = mergedChap.chapterNumber.toDouble(),
                            sourceOrder = mergedChap.sourceOrder,
                            dateFetch = mergedChap.dateFetch,
                            dateUpload = mergedChap.dateUpload,
                            version = mergedChap.version,
                            isSyncing = 1L,
                            memo = mergedChapMemo.let(MemoColumnAdapter::encode),
                            chapterId = existingChap._id,
                        )
                        database.chaptersQueries.touchChapterLastModified(mergedChap.lastModifiedAt, existingChap._id)
                        chapterId = existingChap._id
                        chapterMerged++
                    }

                    // History for chapter
                    val hist = incomingManga.history.firstOrNull { it.url == incomingChapter.url }
                    if (hist != null) {
                        database.historyQueries.upsertSyncHistory(
                            chapterId = chapterId,
                            readAt = Date(hist.lastRead),
                            time_read = hist.readDuration,
                        )
                        if (hist.lastRead > 0) {
                            database.chaptersQueries.touchChapterLastModified(hist.lastRead, chapterId)
                        }
                    }
                }

                // Tracking
                for (incomingTrack in incomingManga.tracking) {
                    val existingTracks = database.manga_syncQueries.getTracksByMangaId(mangaId).awaitAsList()
                    val existingTrack = existingTracks.firstOrNull { it.sync_id == incomingTrack.syncId.toLong() }
                    if (existingTrack == null) {
                        database.manga_syncQueries.insert(
                            mangaId = mangaId,
                            syncId = incomingTrack.syncId.toLong(),
                            remoteId = incomingTrack.mediaId,
                            libraryId = incomingTrack.libraryId,
                            title = incomingTrack.title,
                            lastChapterRead = incomingTrack.lastChapterRead.toDouble(),
                            totalChapters = incomingTrack.totalChapters.toLong(),
                            status = incomingTrack.status.toLong(),
                            score = incomingTrack.score.toDouble(),
                            remoteUrl = incomingTrack.trackingUrl,
                            startDate = incomingTrack.startedReadingDate,
                            finishDate = incomingTrack.finishedReadingDate,
                            `private` = incomingTrack.private,
                        )
                    } else {
                        val existingBackupTrack = AndroidBackupTracking(
                            syncId = existingTrack.sync_id.toInt(),
                            libraryId = existingTrack.library_id ?: 0L,
                            mediaId = existingTrack.remote_id,
                            trackingUrl = existingTrack.remote_url,
                            title = existingTrack.title,
                            lastChapterRead = existingTrack.last_chapter_read.toFloat(),
                            totalChapters = existingTrack.total_chapters.toInt(),
                            score = existingTrack.score.toFloat(),
                            status = existingTrack.status.toInt(),
                            startedReadingDate = existingTrack.start_date,
                            finishedReadingDate = existingTrack.finish_date,
                            private = existingTrack.private_,
                        )
                        val mergedTrack = SyncMergePolicy.mergeTracking(
                            existing = existingBackupTrack,
                            existingDeviceId = "",
                            incoming = incomingTrack,
                            incomingDeviceId = changeset.deviceId,
                        )
                        database.manga_syncQueries.update(
                            mangaId = mangaId,
                            syncId = mergedTrack.syncId.toLong(),
                            mediaId = mergedTrack.mediaId,
                            libraryId = mergedTrack.libraryId,
                            title = mergedTrack.title,
                            lastChapterRead = mergedTrack.lastChapterRead.toDouble(),
                            totalChapter = mergedTrack.totalChapters.toLong(),
                            status = mergedTrack.status.toLong(),
                            score = mergedTrack.score.toDouble(),
                            trackingUrl = mergedTrack.trackingUrl,
                            startDate = mergedTrack.startedReadingDate,
                            finishDate = mergedTrack.finishedReadingDate,
                            `private` = mergedTrack.private,
                            id = existingTrack._id,
                        )
                    }
                }
            }

            // Reset is_syncing flags after batch apply
            database.mangasQueries.resetIsSyncing()
            database.chaptersQueries.resetIsSyncing()

            // Tombstone deletion application is not yet supported in this round

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
}
