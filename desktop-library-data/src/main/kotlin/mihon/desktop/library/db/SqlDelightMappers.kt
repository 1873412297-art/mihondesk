package mihon.desktop.library.db

import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.TrackingRecord

/**
 * SqlDelight rows to storage records, shared by the library and sync repositories so a schema
 * column is mapped once rather than copied per repository.
 */
internal fun Manga.toMangaRecord() = MangaRecord(
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

internal fun Chapter.toChapterRecord() = ChapterRecord(
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

internal fun Tracking.toTrackingRecord() = TrackingRecord(
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
