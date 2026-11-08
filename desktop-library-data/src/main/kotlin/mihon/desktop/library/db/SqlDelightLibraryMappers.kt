package mihon.desktop.library.db

import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.ImportReportItem
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails

internal fun SelectLibrary.toModel() = LibraryManga(
    id = id,
    sourceId = source_id,
    url = url,
    title = title,
    thumbnailUrl = thumbnail_url,
    chapterCount = chapter_count,
    unreadCount = unread_count,
    author = author,
    bookmarkCount = bookmark_count,
    downloadedCount = downloaded_count,
    dateAdded = date_added,
    lastRead = last_read,
    lastUpdate = last_update,
)

internal fun SelectLibraryByCategory.toModel() = LibraryManga(
    id = id,
    sourceId = source_id,
    url = url,
    title = title,
    thumbnailUrl = thumbnail_url,
    chapterCount = chapter_count,
    unreadCount = unread_count,
    author = author,
    bookmarkCount = bookmark_count,
    downloadedCount = downloaded_count,
    dateAdded = date_added,
    lastRead = last_read,
    lastUpdate = last_update,
)

internal fun Manga.toDetails(categories: List<CategoryRecord>) = MangaDetails(
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
    categories = categories,
)

internal fun Chapter.toModel() = LibraryChapter(
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

internal fun Category.toRecord() = CategoryRecord(id, name, sort_order, flags)

internal fun Import_report.toModel(items: List<ImportReportItem>) = ImportReport(
    id = id,
    importType = ImportType.valueOf(import_type),
    sourcePath = source_path,
    status = ImportStatus.valueOf(status),
    startedAt = started_at,
    finishedAt = finished_at,
    counts = ImportCounts(
        mangaInserted = manga_inserted,
        mangaMerged = manga_merged,
        chaptersInserted = chapters_inserted,
        chaptersMerged = chapters_merged,
        categoriesLinked = categories_linked,
        preferencesImported = preferences_imported,
        preferencesSkipped = preferences_skipped,
    ),
    items = items,
)

internal fun Import_report_item.toModel() = ImportReportItem(
    itemType = item_type,
    itemKey = item_key,
    outcome = outcome,
    reason = reason,
    message = message,
    id = id,
)

internal fun SelectHistoryWithDetails.toModel() = HistoryWithDetails(
    chapterId = chapter_id,
    mangaId = manga_id,
    mangaTitle = manga_title,
    mangaThumbnailUrl = manga_thumbnail_url,
    mangaSourceId = manga_source_id,
    chapterName = chapter_name,
    chapterNumber = chapter_number,
    lastPageRead = last_page_read,
    read = read,
    lastRead = last_read,
    readDuration = read_duration,
)
