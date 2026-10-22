package mihon.desktop.ui.home

import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.updates.UpdatedChapterItem
import java.time.Instant
import java.time.ZoneId

data class TodayMangaUpdates(
    val mangaId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val chapters: List<UpdatedChapterItem>,
) {
    val unreadCount: Int get() = chapters.count { !it.read }
}

/** Chapters fetched today, grouped per manga (most recently updated manga first, newest chapter first). */
fun todayUpdates(
    items: List<UpdatedChapterItem>,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<TodayMangaUpdates> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return items
        .filter { Instant.ofEpochMilli(it.dateFetch).atZone(zone).toLocalDate() == today }
        .groupBy { it.mangaId }
        .map { (mangaId, chapters) ->
            val first = chapters.first()
            TodayMangaUpdates(
                mangaId = mangaId,
                title = first.mangaTitle,
                thumbnailUrl = chapters.firstNotNullOfOrNull { it.mangaThumbnailUrl },
                chapters = chapters.sortedWith(
                    compareByDescending<UpdatedChapterItem> { it.chapterNumber }.thenByDescending { it.dateFetch },
                ),
            )
        }
        .sortedByDescending { group -> group.chapters.maxOf { it.dateFetch } }
}

/** Grace period so chapters fetched while a manga was first added do not count as "new today". */
private const val ADDED_GRACE_MILLIS = 60_000L

/**
 * Chapters fetched today for library manga, read back from the database so the home section
 * survives a restart. Manga added today are skipped: their whole chapter list was "fetched" then.
 */
fun todayLibraryChapters(
    library: List<LibraryManga>,
    chaptersFor: (mangaId: Long) -> List<LibraryChapter>,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<UpdatedChapterItem> {
    val startOfDay = Instant.ofEpochMilli(
        nowMillis,
    ).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return library
        .filter { it.lastUpdate >= startOfDay }
        .flatMap { manga ->
            chaptersFor(manga.id)
                .filter { it.dateFetch >= startOfDay && it.dateFetch > manga.dateAdded + ADDED_GRACE_MILLIS }
                .map { chapter ->
                    UpdatedChapterItem(
                        mangaId = manga.id,
                        chapterId = chapter.id,
                        mangaTitle = manga.title,
                        chapterName = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        dateFetch = chapter.dateFetch,
                        mangaThumbnailUrl = manga.thumbnailUrl,
                        read = chapter.read,
                    )
                }
        }
}

/** Session rows (with live read/download flags) win over the same chapter read back from the database. */
fun mergeTodayChapters(session: List<UpdatedChapterItem>, stored: List<UpdatedChapterItem>): List<UpdatedChapterItem> {
    val seen = session.mapTo(HashSet()) { it.chapterId }
    // Live update rows carry no cover; borrow the library cover of the same manga so cards show it.
    val covers = stored.filter { it.mangaThumbnailUrl != null }.associate { it.mangaId to it.mangaThumbnailUrl }
    val withCovers = session.map { row ->
        if (row.mangaThumbnailUrl == null) row.copy(mangaThumbnailUrl = covers[row.mangaId]) else row
    }
    return withCovers + stored.filter { it.chapterId !in seen }
}
