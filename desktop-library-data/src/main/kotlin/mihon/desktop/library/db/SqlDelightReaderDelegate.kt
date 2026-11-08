package mihon.desktop.library.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.reader.ReaderOnlineChapter
import mihon.reader.session.ProgressWriteResult
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import java.nio.file.Path

internal class SqlDelightReaderDelegate(
    private val database: DesktopLibraryDatabase,
) : ReaderLibraryPort {
    private val queries = database.libraryQueries
    private val readerProgressMutex = Mutex()
    private val acceptedReaderProgress = java.util.concurrent.ConcurrentHashMap<Long, ReaderProgressVersion>()

    override fun chapterAsset(chapterId: Long): ReaderChapterAsset? =
        queries.selectReaderChapterAsset(chapterId).executeAsOneOrNull()?.toReaderChapterAsset()

    override fun adjacentReadableChapter(
        chapterId: Long,
        direction: ChapterDirection,
    ): ReaderChapterAsset? = database.transactionWithResult {
        if (chapterAsset(chapterId) == null) return@transactionWithResult null
        when (direction) {
            ChapterDirection.PREVIOUS ->
                queries.selectPreviousReaderChapterAsset(chapterId).executeAsList()
                    .firstNotNullOfOrNull(SelectPreviousReaderChapterAsset::toReaderChapterAsset)
            ChapterDirection.NEXT ->
                queries.selectNextReaderChapterAsset(chapterId).executeAsList()
                    .firstNotNullOfOrNull(SelectNextReaderChapterAsset::toReaderChapterAsset)
        }
    }

    override fun onlineChapter(chapterId: Long): ReaderOnlineChapter? =
        queries.selectReaderOnlineChapter(chapterId).executeAsOneOrNull()?.toReaderOnlineChapter()

    override fun adjacentOnlineChapter(
        chapterId: Long,
        direction: ChapterDirection,
    ): ReaderOnlineChapter? = database.transactionWithResult {
        if (onlineChapter(chapterId) == null) return@transactionWithResult null
        when (direction) {
            ChapterDirection.PREVIOUS ->
                queries.selectPreviousReaderOnlineChapter(chapterId).executeAsOneOrNull()
                    ?.toReaderOnlineChapter()
            ChapterDirection.NEXT ->
                queries.selectNextReaderOnlineChapter(chapterId).executeAsOneOrNull()
                    ?.toReaderOnlineChapter()
        }
    }

    override suspend fun record(update: ReaderProgressUpdate): ProgressWriteResult {
        return readerProgressMutex.withLock {
            validateReaderProgress(update)
            val incoming = ReaderProgressVersion(update.generation, update.sequence)
            val accepted = acceptedReaderProgress[update.chapterId]
            if (accepted != null && incoming <= accepted) return@withLock ProgressWriteResult.STALE

            database.transaction {
                val now = maxOf(update.lastReadEpochMillis, System.currentTimeMillis())
                queries.updateReaderChapterProgress(
                    last_page_read = update.pageIndex,
                    completed = update.completed,
                    chapter_id = update.chapterId,
                    last_modified_at = now,
                )
                queries.accumulateReaderHistory(
                    chapter_id = update.chapterId,
                    last_read = update.lastReadEpochMillis,
                    read_duration_delta = update.readDurationDeltaMillis,
                )
                queries.touchChapterLastModified(
                    chapter_id = update.chapterId,
                    last_modified_at = now,
                )
            }
            acceptedReaderProgress[update.chapterId] = incoming
            ProgressWriteResult.APPLIED
        }
    }
}

private data class ReaderProgressVersion(
    val generation: Long,
    val sequence: Long,
) : Comparable<ReaderProgressVersion> {
    override fun compareTo(other: ReaderProgressVersion): Int =
        compareValuesBy(this, other, ReaderProgressVersion::generation, ReaderProgressVersion::sequence)
}

private fun validateReaderProgress(update: ReaderProgressUpdate) {
    require(update.chapterId >= 0) { "chapterId must not be negative" }
    require(update.pageIndex in 0..Int.MAX_VALUE.toLong()) { "pageIndex must be between 0 and Int.MAX_VALUE" }
    require(update.lastReadEpochMillis >= 0) { "lastReadEpochMillis must not be negative" }
    require(update.readDurationDeltaMillis in 0..86_400_000L) {
        "readDurationDeltaMillis must be between 0 and 86400000"
    }
    require(update.generation >= 0) { "generation must not be negative" }
    require(update.sequence >= 0) { "sequence must not be negative" }
}

private fun SelectReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun SelectPreviousReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun SelectNextReaderChapterAsset.toReaderChapterAsset(): ReaderChapterAsset? = readerChapterAsset(
    manga_id,
    chapter_id,
    manga_title,
    chapter_name,
    storage_path,
    relative_path,
    asset_kind,
    size_bytes,
    modified_at,
    last_page_read,
    read,
)

private fun SelectReaderOnlineChapter.toReaderOnlineChapter(): ReaderOnlineChapter = readerOnlineChapter(
    mangaId = manga_id,
    chapterId = chapter_id,
    mangaTitle = manga_title,
    chapterName = chapter_name,
    chapterUrl = chapter_url,
    sourceId = source_id,
    chapterNumber = chapter_number,
    sourceOrder = source_order,
    dateUpload = date_upload,
    scanlator = scanlator,
    lastPageRead = last_page_read,
    read = read,
)

private fun SelectPreviousReaderOnlineChapter.toReaderOnlineChapter(): ReaderOnlineChapter = readerOnlineChapter(
    mangaId = manga_id,
    chapterId = chapter_id,
    mangaTitle = manga_title,
    chapterName = chapter_name,
    chapterUrl = chapter_url,
    sourceId = source_id,
    chapterNumber = chapter_number,
    sourceOrder = source_order,
    dateUpload = date_upload,
    scanlator = scanlator,
    lastPageRead = last_page_read,
    read = read,
)

private fun SelectNextReaderOnlineChapter.toReaderOnlineChapter(): ReaderOnlineChapter = readerOnlineChapter(
    mangaId = manga_id,
    chapterId = chapter_id,
    mangaTitle = manga_title,
    chapterName = chapter_name,
    chapterUrl = chapter_url,
    sourceId = source_id,
    chapterNumber = chapter_number,
    sourceOrder = source_order,
    dateUpload = date_upload,
    scanlator = scanlator,
    lastPageRead = last_page_read,
    read = read,
)

private fun readerOnlineChapter(
    mangaId: Long,
    chapterId: Long,
    mangaTitle: String,
    chapterName: String,
    chapterUrl: String,
    sourceId: Long,
    chapterNumber: Double,
    sourceOrder: Long,
    dateUpload: Long,
    scanlator: String?,
    lastPageRead: Long,
    read: Boolean,
): ReaderOnlineChapter = ReaderOnlineChapter(
    mangaId = mangaId,
    chapterId = chapterId,
    mangaTitle = mangaTitle,
    chapterName = chapterName,
    chapterUrl = chapterUrl,
    sourceId = sourceId,
    chapterNumber = chapterNumber,
    sourceOrder = sourceOrder,
    dateUpload = dateUpload,
    scanlator = scanlator,
    lastPageRead = lastPageRead,
    read = read,
)

private fun readerChapterAsset(
    mangaId: Long,
    chapterId: Long,
    mangaTitle: String,
    chapterName: String,
    storagePath: String,
    relativePath: String,
    assetKind: String,
    sizeBytes: Long,
    modifiedAt: Long,
    lastPageRead: Long,
    read: Boolean,
): ReaderChapterAsset? = runCatching {
    val storageRoot = Path.of(storagePath).toAbsolutePath().normalize()
    val storedRelativePath = Path.of(relativePath)
    if (storedRelativePath.isAbsolute) return null
    val resolvedPath = storageRoot.resolve(storedRelativePath).normalize()
    if (!resolvedPath.startsWith(storageRoot)) return null
    ReaderChapterAsset(
        mangaId = mangaId,
        chapterId = chapterId,
        mangaTitle = mangaTitle,
        chapterName = chapterName,
        storageRoot = storageRoot,
        relativePath = storageRoot.relativize(resolvedPath),
        assetKind = assetKind,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        lastPageRead = lastPageRead,
        read = read,
    )
}.getOrNull()
