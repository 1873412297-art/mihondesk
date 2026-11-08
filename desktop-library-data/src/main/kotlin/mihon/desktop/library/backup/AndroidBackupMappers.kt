package mihon.desktop.library.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking

/**
 * Android backup entities to storage records. Restore and the sync apply path both write through
 * these, so a new column only has to be mapped once instead of drifting between the two paths.
 */
internal fun AndroidBackupManga.toMangaRecord(memoJson: String) = MangaRecord(
    sourceId = source,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genreJson = canonicalStringSetJson(genre),
    status = status.toLong(),
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    dateAdded = dateAdded,
    viewerFlags = (viewerFlags ?: viewer).toLong(),
    chapterFlags = chapterFlags.toLong(),
    updateStrategy = updateStrategy.name,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    excludedScanlatorsJson = canonicalStringSetJson(excludedScanlators),
    version = version,
    notes = notes,
    initialized = initialized,
    memoJson = memoJson,
)

internal fun AndroidBackupChapter.toChapterRecord(mangaId: Long, memoJson: String) = ChapterRecord(
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
    memoJson = memoJson,
)

@Suppress("DEPRECATION")
internal fun AndroidBackupTracking.toTrackingRecord(mangaId: Long) = TrackingRecord(
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

/** Android stores memos as bytes; malformed UTF-8 decodes to replacement characters, never fails. */
internal fun memoJson(memo: ByteArray): String = memo.decodeToString()

internal fun canonicalStringSetJson(values: Iterable<String>) = JsonArray(
    values.toSet().sortedWith(SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR).map(::JsonPrimitive),
).toString()
