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

class SqlDelightSyncLocalRepository(
    internal val database: DesktopLibraryDatabase,
    internal val json: Json = Json,
    private val deviceId: () -> String = {
        database.libraryQueries.selectMetadata("sync_device_id").executeAsOneOrNull().orEmpty()
    },
) : SyncLocalRepository {
    internal val queries = database.libraryQueries

    internal fun localDeviceId(): String = deviceId()

    override suspend fun categoriesFingerprint(): String? = withContext(Dispatchers.IO) {
        val payload = queries.selectAllCategoriesForExport().executeAsList()
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .joinToString("\n") { "${it.name.lowercase(Locale.ROOT)}|${it.sort_order}|${it.flags}" }
        if (payload.isEmpty()) {
            null
        } else {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun exportDelta(sinceCursor: Long): EntityDelta = withContext(Dispatchers.IO) {
        val modifiedMangas = queries.selectMangaModifiedSince(sinceCursor).executeAsList().map { it.toMangaRecord() }
        val modifiedChapters = queries.selectChaptersModifiedSince(sinceCursor).executeAsList()
        val modifiedMangaIds = modifiedMangas.map { it.id }.toMutableSet()
        val extraMangas = mutableListOf<MangaRecord>()

        for (chap in modifiedChapters) {
            if (modifiedMangaIds.add(chap.manga_id)) {
                queries.selectMangaById(chap.manga_id).executeAsOneOrNull()?.toMangaRecord()?.let {
                    extraMangas.add(it)
                }
            }
        }

        val allTargetMangas = modifiedMangas + extraMangas
        val categoryTimestamps = queries.selectCategoryTimestamps().executeAsList()
            .associate { it.category_id to it.last_modified_at }
        val allCategories = queries.selectAllCategoriesForExport().executeAsList().map {
            AndroidBackupCategory(
                name = it.name,
                order = it.sort_order,
                flags = it.flags,
                lastModifiedAt = categoryTimestamps[it.id] ?: 0L,
            )
        }

        val localDeviceId = localDeviceId()

        val exportedMangas = allTargetMangas.map { manga ->
            val chapters = queries.selectChaptersForManga(manga.id).executeAsList().map { it.toChapterRecord() }
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

            val historyByChapter = queries.selectHistoryForManga(manga.id).executeAsList().associateBy { it.chapter_id }
            val history = chapters.mapNotNull { ch ->
                historyByChapter[ch.id]?.let { AndroidBackupHistory(ch.url, it.last_read, it.read_duration) }
            }

            val tracking = queries.selectTrackingForManga(manga.id).executeAsList().map {
                it.toTrackingRecord()
            }.map { t ->
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

    override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult =
        applyChangesetInternal(changeset)
}
