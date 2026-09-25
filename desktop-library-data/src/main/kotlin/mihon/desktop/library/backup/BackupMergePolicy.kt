package mihon.desktop.library.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.sync.core.merge.SyncMergePolicy

object BackupMergePolicy {
    fun mergeManga(
        existing: MangaRecord,
        incoming: MangaRecord,
        existingDeviceId: String = "",
        incomingDeviceId: String = "",
    ): MangaRecord {
        val effExistingDeviceId = existingDeviceId.ifBlank {
            SyncMergePolicy.extractDeviceIdFromMemoJson(existing.memoJson)
        }
        val effIncomingDeviceId = incomingDeviceId.ifBlank {
            SyncMergePolicy.extractDeviceIdFromMemoJson(incoming.memoJson)
        }
        val incomingWins = SyncMergePolicy.isIncomingWinner(
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
            SyncMergePolicy.isIncomingWinner(
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
        val mergedFavoriteModifiedAt = SyncMergePolicy.maxNullable(
            existing.favoriteModifiedAt,
            incoming.favoriteModifiedAt,
        )

        return existing.copy(
            title = SyncMergePolicy.selectedString(existing.title, incoming.title, incomingWins),
            artist = SyncMergePolicy.selectedNullableString(existing.artist, incoming.artist, incomingWins),
            author = SyncMergePolicy.selectedNullableString(existing.author, incoming.author, incomingWins),
            description = SyncMergePolicy.selectedNullableString(
                existing.description,
                incoming.description,
                incomingWins,
            ),
            genreJson = unionJsonStringSets(existing.genreJson, incoming.genreJson),
            status = selected.status,
            thumbnailUrl = SyncMergePolicy.selectedNullableString(
                existing.thumbnailUrl,
                incoming.thumbnailUrl,
                incomingWins,
            ),
            favorite = mergedFavorite,
            dateAdded = selected.dateAdded,
            viewerFlags = selected.viewerFlags,
            chapterFlags = selected.chapterFlags,
            updateStrategy = SyncMergePolicy.selectedString(
                existing.updateStrategy,
                incoming.updateStrategy,
                incomingWins,
            ),
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            favoriteModifiedAt = mergedFavoriteModifiedAt,
            excludedScanlatorsJson = unionJsonStringSets(
                existing.excludedScanlatorsJson,
                incoming.excludedScanlatorsJson,
            ),
            version = maxOf(existing.version, incoming.version),
            notes = SyncMergePolicy.selectedString(existing.notes, incoming.notes, incomingWins),
            initialized = selected.initialized,
            memoJson = SyncMergePolicy.selectedString(existing.memoJson, incoming.memoJson, incomingWins),
        )
    }

    fun mergeChapter(
        existing: ChapterRecord,
        incoming: ChapterRecord,
        existingDeviceId: String = "",
        incomingDeviceId: String = "",
    ): ChapterRecord {
        val effExistingDeviceId = existingDeviceId.ifBlank {
            SyncMergePolicy.extractDeviceIdFromMemoJson(existing.memoJson)
        }
        val effIncomingDeviceId = incomingDeviceId.ifBlank {
            SyncMergePolicy.extractDeviceIdFromMemoJson(incoming.memoJson)
        }
        val incomingWins = SyncMergePolicy.isIncomingWinner(
            existingWatermark = existing.lastModifiedAt,
            existingDeviceId = effExistingDeviceId,
            incomingWatermark = incoming.lastModifiedAt,
            incomingDeviceId = effIncomingDeviceId,
        )
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            name = SyncMergePolicy.selectedString(existing.name, incoming.name, incomingWins),
            scanlator = SyncMergePolicy.selectedNullableString(existing.scanlator, incoming.scanlator, incomingWins),
            // Note: Chapter read and bookmark retain OR semantics for now because individual chapter
            // read/bookmark change timestamps are not tracked in the current schema (only lastPageRead / lastModifiedAt).
            read = existing.read || incoming.read,
            bookmark = existing.bookmark || incoming.bookmark,
            lastPageRead = maxOf(existing.lastPageRead, incoming.lastPageRead),
            dateFetch = maxOf(existing.dateFetch, incoming.dateFetch),
            dateUpload = maxOf(existing.dateUpload, incoming.dateUpload),
            chapterNumber = selected.chapterNumber,
            sourceOrder = selected.sourceOrder,
            lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
            version = maxOf(existing.version, incoming.version),
            memoJson = SyncMergePolicy.selectedString(existing.memoJson, incoming.memoJson, incomingWins),
        )
    }

    fun mergeHistory(existing: HistoryRecord, incoming: HistoryRecord): HistoryRecord = existing.copy(
        lastRead = maxOf(existing.lastRead, incoming.lastRead),
        readDuration = maxOf(existing.readDuration, incoming.readDuration),
    )

    fun mergeTracking(existing: TrackingRecord, incoming: TrackingRecord): TrackingRecord {
        val incomingWins = incoming.lastChapterRead > existing.lastChapterRead
        val selected = if (incomingWins) incoming else existing
        return existing.copy(
            remoteId = selected.remoteId,
            libraryId = selected.libraryId,
            title = SyncMergePolicy.selectedString(existing.title, incoming.title, incomingWins),
            lastChapterRead = maxOf(existing.lastChapterRead, incoming.lastChapterRead),
            totalChapters = maxOf(existing.totalChapters, incoming.totalChapters),
            score = selected.score,
            status = selected.status,
            startedReadingDate = SyncMergePolicy.earliestNonzero(
                existing.startedReadingDate,
                incoming.startedReadingDate,
            ),
            finishedReadingDate = maxOf(existing.finishedReadingDate, incoming.finishedReadingDate),
            private = selected.private,
            trackingUrl = SyncMergePolicy.selectedString(existing.trackingUrl, incoming.trackingUrl, incomingWins),
        )
    }

    private fun unionJsonStringSets(existing: String, incoming: String): String {
        val values = buildSet {
            addAll(Json.decodeFromString<List<String>>(existing))
            addAll(Json.decodeFromString<List<String>>(incoming))
        }.sortedWith(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR)
        return JsonArray(values.map(::JsonPrimitive)).toString()
    }
}
