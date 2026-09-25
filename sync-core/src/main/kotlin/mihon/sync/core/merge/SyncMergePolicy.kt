package mihon.sync.core.merge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.CategoryKey
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.MangaKey
import mihon.sync.core.model.Tombstone
import mihon.sync.core.model.categoryKey
import mihon.sync.core.model.mangaKey

object SyncMergePolicy {

    val UNICODE_CODE_POINT_COMPARATOR = Comparator<String> { left, right ->
        val leftPoints = left.codePoints().iterator()
        val rightPoints = right.codePoints().iterator()
        while (leftPoints.hasNext() && rightPoints.hasNext()) {
            val comparison = leftPoints.nextInt().compareTo(rightPoints.nextInt())
            if (comparison != 0) return@Comparator comparison
        }
        when {
            leftPoints.hasNext() -> 1
            rightPoints.hasNext() -> -1
            else -> 0
        }
    }

    /**
     * Determines whether incoming entity wins over existing entity using Last-Write-Wins (LWW)
     * with deviceId lexicographical tie-breaking for convergence.
     */
    fun isIncomingWinner(
        existingWatermark: Long,
        existingDeviceId: String,
        incomingWatermark: Long,
        incomingDeviceId: String,
    ): Boolean {
        if (incomingWatermark != existingWatermark) {
            return incomingWatermark > existingWatermark
        }
        return incomingDeviceId > existingDeviceId
    }

    fun mergeManga(
        existing: AndroidBackupManga,
        existingDeviceId: String = "",
        incoming: AndroidBackupManga,
        incomingDeviceId: String = "",
    ): AndroidBackupManga {
        val effExistingDeviceId = existingDeviceId.ifBlank { extractDeviceIdFromMemoBytes(existing.memo) }
        val effIncomingDeviceId = incomingDeviceId.ifBlank { extractDeviceIdFromMemoBytes(incoming.memo) }
        val incomingWins = isIncomingWinner(
            existingWatermark = existing.lastModifiedAt,
            existingDeviceId = effExistingDeviceId,
            incomingWatermark = incoming.lastModifiedAt,
            incomingDeviceId = effIncomingDeviceId,
        )
        val selected = if (incomingWins) incoming else existing

        // LWW for favorite based on favoriteModifiedAt with deviceId tie-breaking
        val existingFavTime = existing.favoriteModifiedAt ?: 0L
        val incomingFavTime = incoming.favoriteModifiedAt ?: 0L
        val incomingFavWins = if (existingFavTime != incomingFavTime) {
            isIncomingWinner(
                existingWatermark = existingFavTime,
                existingDeviceId = effExistingDeviceId,
                incomingWatermark = incomingFavTime,
                incomingDeviceId = effIncomingDeviceId,
            )
        } else if (existingFavTime > 0L) {
            effIncomingDeviceId > effExistingDeviceId
        } else {
            incomingWins
        }
        val mergedFavorite = if (incomingFavWins) incoming.favorite else existing.favorite
        val mergedFavoriteModifiedAt = maxNullable(existing.favoriteModifiedAt, incoming.favoriteModifiedAt)

        // Merge nested chapters by URL
        val existingChaptersByUrl = existing.chapters.associateBy { it.url }
        val incomingChaptersByUrl = incoming.chapters.associateBy { it.url }
        val allChapterUrls = (existingChaptersByUrl.keys + incomingChaptersByUrl.keys).sortedWith(
            UNICODE_CODE_POINT_COMPARATOR,
        )
        val mergedChapters = allChapterUrls.map { url ->
            val exChapter = existingChaptersByUrl[url]
            val inChapter = incomingChaptersByUrl[url]
            when {
                exChapter != null && inChapter != null -> mergeChapter(
                    existing = exChapter,
                    existingDeviceId = existingDeviceId,
                    incoming = inChapter,
                    incomingDeviceId = incomingDeviceId,
                )
                exChapter != null -> exChapter
                else -> checkNotNull(inChapter)
            }
        }.sortedWith(compareBy<AndroidBackupChapter> { it.sourceOrder }.thenBy { it.url })

        // Merge nested history by URL
        val existingHistoryByUrl = existing.history.associateBy { it.url }
        val incomingHistoryByUrl = incoming.history.associateBy { it.url }
        val allHistoryUrls = (existingHistoryByUrl.keys + incomingHistoryByUrl.keys).sortedWith(
            UNICODE_CODE_POINT_COMPARATOR,
        )
        val mergedHistory = allHistoryUrls.map { url ->
            val exHist = existingHistoryByUrl[url]
            val inHist = incomingHistoryByUrl[url]
            when {
                exHist != null && inHist != null -> mergeHistory(exHist, inHist)
                exHist != null -> exHist
                else -> checkNotNull(inHist)
            }
        }

        // Merge nested tracking by syncId
        val existingTrackingById = existing.tracking.associateBy { it.syncId }
        val incomingTrackingById = incoming.tracking.associateBy { it.syncId }
        val allTrackingIds = (existingTrackingById.keys + incomingTrackingById.keys).sorted()
        val mergedTracking = allTrackingIds.map { syncId ->
            val exTrack = existingTrackingById[syncId]
            val inTrack = incomingTrackingById[syncId]
            when {
                exTrack != null && inTrack != null -> mergeTracking(
                    existing = exTrack,
                    existingDeviceId = existingDeviceId,
                    incoming = inTrack,
                    incomingDeviceId = incomingDeviceId,
                )
                exTrack != null -> exTrack
                else -> checkNotNull(inTrack)
            }
        }

        val mergedCategories = (existing.categories + incoming.categories).distinct().sorted()

        return existing.copy(
            title = selectedString(existing.title, incoming.title, incomingWins),
            artist = selectedNullableString(existing.artist, incoming.artist, incomingWins),
            author = selectedNullableString(existing.author, incoming.author, incomingWins),
            description = selectedNullableString(existing.description, incoming.description, incomingWins),
            genre = unionStringLists(existing.genre, incoming.genre),
            status = selected.status,
            thumbnailUrl = selectedNullableString(existing.thumbnailUrl, incoming.thumbnailUrl, incomingWins),
            dateAdded = selected.dateAdded,
            viewer = selected.viewer,
            chapters = mergedChapters,
            categories = mergedCategories,
            tracking = mergedTracking,
            favorite = mergedFavorite,
            chapterFlags = selected.chapterFlags,
            viewerFlags = selected.viewerFlags,
            history = mergedHistory,
            updateStrategy = if (incomingWins) incoming.updateStrategy else existing.updateStrategy,
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            favoriteModifiedAt = mergedFavoriteModifiedAt,
            excludedScanlators = unionStringLists(existing.excludedScanlators, incoming.excludedScanlators),
            version = maxOf(existing.version, incoming.version),
            notes = selectedString(existing.notes, incoming.notes, incomingWins),
            initialized = selected.initialized,
            memo = selectedByteArray(existing.memo, incoming.memo, incomingWins),
        )
    }

    fun mergeChapter(
        existing: AndroidBackupChapter,
        existingDeviceId: String = "",
        incoming: AndroidBackupChapter,
        incomingDeviceId: String = "",
    ): AndroidBackupChapter {
        val effExistingDeviceId = existingDeviceId.ifBlank { extractDeviceIdFromMemoBytes(existing.memo) }
        val effIncomingDeviceId = incomingDeviceId.ifBlank { extractDeviceIdFromMemoBytes(incoming.memo) }
        val incomingWins = isIncomingWinner(
            existingWatermark = existing.lastModifiedAt,
            existingDeviceId = effExistingDeviceId,
            incomingWatermark = incoming.lastModifiedAt,
            incomingDeviceId = effIncomingDeviceId,
        )
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            name = selectedString(existing.name, incoming.name, incomingWins),
            scanlator = selectedNullableString(existing.scanlator, incoming.scanlator, incomingWins),
            // Note: Chapter read and bookmark retain OR semantics for now because individual chapter
            // read/bookmark change timestamps are not tracked in the current schema (only lastPageRead / lastModifiedAt).
            // Un-reading or un-bookmarking across sync is not yet supported without dedicated per-field timestamps.
            read = existing.read || incoming.read,
            bookmark = existing.bookmark || incoming.bookmark,
            lastPageRead = maxOf(existing.lastPageRead, incoming.lastPageRead),
            dateFetch = maxOf(existing.dateFetch, incoming.dateFetch),
            dateUpload = maxOf(existing.dateUpload, incoming.dateUpload),
            chapterNumber = selected.chapterNumber,
            sourceOrder = selected.sourceOrder,
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            version = maxOf(existing.version, incoming.version),
            memo = selectedByteArray(existing.memo, incoming.memo, incomingWins),
        )
    }

    fun mergeHistory(
        existing: AndroidBackupHistory,
        incoming: AndroidBackupHistory,
    ): AndroidBackupHistory = existing.copy(
        lastRead = maxOf(existing.lastRead, incoming.lastRead),
        readDuration = maxOf(existing.readDuration, incoming.readDuration),
    )

    fun mergeTracking(
        existing: AndroidBackupTracking,
        existingDeviceId: String = "",
        incoming: AndroidBackupTracking,
        incomingDeviceId: String = "",
    ): AndroidBackupTracking {
        val incomingWins = if (incoming.lastChapterRead != existing.lastChapterRead) {
            incoming.lastChapterRead > existing.lastChapterRead
        } else {
            incomingDeviceId > existingDeviceId
        }
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            libraryId = selected.libraryId,
            mediaIdInt = selected.mediaIdInt,
            trackingUrl = selectedString(existing.trackingUrl, incoming.trackingUrl, incomingWins),
            title = selectedString(existing.title, incoming.title, incomingWins),
            lastChapterRead = maxOf(existing.lastChapterRead, incoming.lastChapterRead),
            totalChapters = maxOf(existing.totalChapters, incoming.totalChapters),
            score = selected.score,
            status = selected.status,
            startedReadingDate = earliestNonzero(existing.startedReadingDate, incoming.startedReadingDate),
            finishedReadingDate = maxOf(existing.finishedReadingDate, incoming.finishedReadingDate),
            private = selected.private,
            mediaId = selected.mediaId,
        )
    }

    fun mergeCategory(
        existing: AndroidBackupCategory,
        existingDeviceId: String = "",
        incoming: AndroidBackupCategory,
        incomingDeviceId: String = "",
    ): AndroidBackupCategory {
        val incomingWins = if (incoming.flags != existing.flags) {
            incoming.flags > existing.flags
        } else {
            incomingDeviceId > existingDeviceId
        }
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            name = selectedString(existing.name, incoming.name, incomingWins),
            order = maxOf(existing.order, incoming.order),
            flags = selected.flags,
        )
    }

    fun mergeChangeset(existing: Changeset, incoming: Changeset): Changeset {
        val winnerDeviceId = if (incoming.cursor > existing.cursor) {
            incoming.deviceId
        } else if (incoming.cursor < existing.cursor) {
            existing.deviceId
        } else {
            if (incoming.deviceId > existing.deviceId) incoming.deviceId else existing.deviceId
        }

        // 1. Merge tombstones by (entityType, entityKey)
        val allTombstonesMap = mutableMapOf<Pair<EntityType, String>, Tombstone>()
        for (tb in existing.tombstones) {
            val key = tb.entityType to tb.entityKey
            allTombstonesMap[key] = tb
        }
        for (tb in incoming.tombstones) {
            val key = tb.entityType to tb.entityKey
            val ex = allTombstonesMap[key]
            if (ex == null || tb.deletedAt > ex.deletedAt) {
                allTombstonesMap[key] = tb
            }
        }

        // 2. Merge mangas
        val mangasByKey = mutableMapOf<MangaKey, AndroidBackupManga>()
        for (m in existing.upserts.mangas) {
            mangasByKey[m.mangaKey] = m
        }
        for (m in incoming.upserts.mangas) {
            val key = m.mangaKey
            val ex = mangasByKey[key]
            if (ex == null) {
                mangasByKey[key] = m
            } else {
                mangasByKey[key] = mergeManga(
                    existing = ex,
                    existingDeviceId = existing.deviceId,
                    incoming = m,
                    incomingDeviceId = incoming.deviceId,
                )
            }
        }

        // 3. Merge categories
        val categoriesByKey = mutableMapOf<CategoryKey, AndroidBackupCategory>()
        for (c in existing.upserts.categories) {
            val key = c.categoryKey
            val ex = categoriesByKey[key]
            if (ex == null) {
                categoriesByKey[key] = c
            } else {
                categoriesByKey[key] = mergeCategory(
                    existing = ex,
                    existingDeviceId = existing.deviceId,
                    incoming = c,
                    incomingDeviceId = existing.deviceId,
                )
            }
        }
        for (c in incoming.upserts.categories) {
            val key = c.categoryKey
            val ex = categoriesByKey[key]
            if (ex == null) {
                categoriesByKey[key] = c
            } else {
                categoriesByKey[key] = mergeCategory(
                    existing = ex,
                    existingDeviceId = existing.deviceId,
                    incoming = c,
                    incomingDeviceId = incoming.deviceId,
                )
            }
        }

        // 4. Resolve Tombstone vs Upsert
        // For mangas:
        val resolvedMangas = mutableListOf<AndroidBackupManga>()
        for ((key, manga) in mangasByKey) {
            val tombstone = allTombstonesMap[EntityType.MANGA to key.toKeyString()]
            if (tombstone != null) {
                if (tombstone.deletedAt >= manga.lastModifiedAt) {
                    // Tombstone wins
                    continue
                } else {
                    // Manga wins (revived)
                    allTombstonesMap.remove(EntityType.MANGA to key.toKeyString())
                    resolvedMangas.add(manga)
                }
            } else {
                resolvedMangas.add(manga)
            }
        }

        // For categories:
        val resolvedCategories = mutableListOf<AndroidBackupCategory>()
        for ((key, cat) in categoriesByKey) {
            val tombstone = allTombstonesMap[EntityType.CATEGORY to key.toKeyString()]
            if (tombstone != null) {
                // Category tombstone
                continue
            } else {
                resolvedCategories.add(cat)
            }
        }

        val sortedMangas = resolvedMangas.sortedWith(
            compareBy<AndroidBackupManga> { it.source }.thenBy(UNICODE_CODE_POINT_COMPARATOR) { it.url },
        )
        val sortedCategories = resolvedCategories.sortedWith(
            compareBy(UNICODE_CODE_POINT_COMPARATOR) { it.categoryKey.toKeyString() },
        )
        val sortedTombstones = allTombstonesMap.values.sortedWith(
            compareBy<Tombstone> { it.entityType.ordinal }
                .thenBy(UNICODE_CODE_POINT_COMPARATOR) { it.entityKey },
        )

        return Changeset(
            deviceId = winnerDeviceId,
            baseSchema = maxOf(existing.baseSchema, incoming.baseSchema),
            cursor = maxOf(existing.cursor, incoming.cursor),
            producedAt = maxOf(existing.producedAt, incoming.producedAt),
            upserts = EntityDelta(
                mangas = sortedMangas,
                categories = sortedCategories,
            ),
            tombstones = sortedTombstones,
        )
    }

    fun selectedString(existing: String, incoming: String, incomingWins: Boolean): String {
        val winner = if (incomingWins) incoming else existing
        val loser = if (incomingWins) existing else incoming
        return if (winner.isNotBlank()) winner else loser
    }

    fun selectedNullableString(existing: String?, incoming: String?, incomingWins: Boolean): String? {
        val winner = if (incomingWins) incoming else existing
        val loser = if (incomingWins) existing else incoming
        return if (!winner.isNullOrBlank()) winner else loser
    }

    fun selectedByteArray(existing: ByteArray, incoming: ByteArray, incomingWins: Boolean): ByteArray {
        val winner = if (incomingWins) incoming else existing
        val loser = if (incomingWins) existing else incoming
        return if (winner.isNotEmpty()) winner else loser
    }

    fun earliestNonzero(existing: Long, incoming: Long): Long = when {
        existing == 0L -> incoming
        incoming == 0L -> existing
        else -> minOf(existing, incoming)
    }

    fun maxNullable(existing: Long?, incoming: Long?): Long? = when {
        existing == null -> incoming
        incoming == null -> existing
        else -> maxOf(existing, incoming)
    }

    fun unionStringLists(existing: List<String>, incoming: List<String>): List<String> {
        return (existing + incoming).distinct().sortedWith(UNICODE_CODE_POINT_COMPARATOR)
    }

    fun extractDeviceIdFromMemoJson(memoJson: String?): String {
        if (memoJson.isNullOrBlank()) return ""
        return try {
            val root = Json.decodeFromString<JsonObject>(memoJson)
            root["sync_device_id"]?.jsonPrimitive?.content ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    fun injectDeviceIdIntoMemoJson(memoJson: String?, deviceId: String): String {
        return try {
            val existingMap = if (!memoJson.isNullOrBlank()) {
                Json.decodeFromString<JsonObject>(memoJson).toMutableMap()
            } else {
                mutableMapOf()
            }
            existingMap["sync_device_id"] = JsonPrimitive(deviceId)
            JsonObject(existingMap).toString()
        } catch (_: Throwable) {
            """{"sync_device_id":"$deviceId"}"""
        }
    }

    fun extractDeviceIdFromMemoBytes(memoBytes: ByteArray): String {
        if (memoBytes.isEmpty()) return ""
        return extractDeviceIdFromMemoJson(memoBytes.decodeToString())
    }

    fun injectDeviceIdIntoMemoBytes(memoBytes: ByteArray, deviceId: String): ByteArray {
        val jsonStr = if (memoBytes.isEmpty()) "{}" else memoBytes.decodeToString()
        return injectDeviceIdIntoMemoJson(jsonStr, deviceId).encodeToByteArray()
    }
}
