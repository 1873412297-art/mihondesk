package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.update.LibraryUpdateReport
import mihon.desktop.ui.home.TodayMangaUpdates
import mihon.desktop.ui.home.mergeTodayChapters
import mihon.desktop.ui.home.todayLibraryChapters
import mihon.desktop.ui.home.todayUpdates
import mihon.desktop.updates.LibraryUpdateResult
import mihon.desktop.updates.UpdatedChapterItem

/** Live row state for the Updates feed; the update report itself is a point-in-time snapshot. */
internal data class UpdatesRowFlags(
    val read: Boolean = false,
    val downloaded: Boolean = false,
)

/** Everything the Updates screen and the Home "today" strip render. */
internal data class UpdatesFeed(
    val lastUpdateResult: LibraryUpdateResult?,
    val updatedChapters: List<UpdatedChapterItem>,
    val homeToday: List<TodayMangaUpdates>,
) {
    /** Unread-count badge for the Updates rail item. */
    val unreadCount: Int = updatedChapters.count { !it.read }
}

/**
 * Per-chapter row state for the Updates feed: read/download changes made from the feed itself are
 * kept as in-memory overrides until the next update run replaces them.
 */
@Stable
internal class UpdatesRowOverrides {
    var flags by mutableStateOf<Map<Long, UpdatesRowFlags>>(emptyMap())
        private set

    fun reset(value: Map<Long, UpdatesRowFlags>) {
        flags = value
        touched.clear()
    }

    /** Applies a delayed initial pass without rolling back rows the user changed while it computed. */
    fun resetPreservingTouched(value: Map<Long, UpdatesRowFlags>) {
        if (touched.isEmpty()) {
            flags = value
            return
        }
        flags = value + touched.mapNotNull { id -> flags[id]?.let { id to it } }.toMap()
        touched.removeAll(flags.keys)
    }

    fun markRead(chapterId: Long) = update(chapterId) { it.copy(read = true) }

    fun markDownloaded(chapterId: Long) = update(chapterId) { it.copy(downloaded = true) }

    private val touched = mutableSetOf<Long>()

    private fun update(chapterId: Long, change: (UpdatesRowFlags) -> UpdatesRowFlags) {
        touched += chapterId
        flags = flags + (chapterId to change(flags[chapterId] ?: UpdatesRowFlags()))
    }
}

@Composable
internal fun rememberUpdatesFeed(
    runtime: DesktopRuntime,
    lastReport: LibraryUpdateReport?,
    library: List<LibraryManga>,
    overrides: UpdatesRowOverrides,
): UpdatesFeed {
    val downloader = runtime.downloader
    // Only a new report (or the library becoming available) may reset row overrides: library
    // emissions caused by the user's own mark-read/download action must not roll the row back.
    val currentLibrary by rememberUpdatedState(library)
    val libraryReady = library.isNotEmpty()
    LaunchedEffect(lastReport, downloader, libraryReady) {
        val flags = withContext(Dispatchers.IO) {
            initialRowFlags(lastReport, currentLibrary, downloader)
        }
        overrides.resetPreservingTouched(flags)
    }
    val lastUpdateResult = remember(lastReport) { lastReport?.toResult() }
    val updatedChapters = remember(lastReport, library, overrides.flags) {
        updatedChapterItems(lastReport, library, overrides.flags)
    }
    // Home "today" strip: this session's report plus chapters fetched earlier today (read back
    // from the database so it survives a restart), grouped per manga, and where to resume.
    val storedTodayChapters by produceState(emptyList<UpdatedChapterItem>(), lastReport, library) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                todayLibraryChapters(
                    library = runtime.library.librarySnapshot(null),
                    chaptersFor = { runtime.library.chapterSnapshot(it) },
                )
            }.onFailure {
                mihon.desktop.logging.DesktopLogger.warn("Updates", "Cannot read today's chapters", it)
            }.getOrDefault(emptyList())
        }
    }
    val homeToday = remember(updatedChapters, storedTodayChapters) {
        todayUpdates(mergeTodayChapters(updatedChapters, storedTodayChapters))
    }
    return UpdatesFeed(lastUpdateResult, updatedChapters, homeToday)
}

private fun LibraryUpdateReport.toResult() = LibraryUpdateResult(
    totalMangaChecked = totalMangaChecked,
    mangaWithNewChapters = updatedMangaCount,
    newChaptersFound = newChaptersTotal,
    updatedMangaTitles = results.filter { it.newChapters.isNotEmpty() }.map { it.title },
    errors = errors,
)

private fun initialRowFlags(
    report: LibraryUpdateReport?,
    library: List<LibraryManga>,
    downloader: DesktopDownloader?,
): Map<Long, UpdatesRowFlags> = report?.results?.flatMap { res ->
    val manga = library.firstOrNull { it.id == res.mangaId }
    res.newChapters.map { ch ->
        val downloaded = manga != null &&
            downloader?.isChapterDownloaded(manga.sourceId, manga.title, ch.id, ch.name, manga.id) == true
        ch.id to UpdatesRowFlags(read = ch.read, downloaded = downloaded)
    }
}.orEmpty().toMap()

private fun updatedChapterItems(
    report: LibraryUpdateReport?,
    library: List<LibraryManga>,
    overrides: Map<Long, UpdatesRowFlags>,
): List<UpdatedChapterItem> = report?.results?.flatMap { res ->
    val mangaCover = library.firstOrNull { it.id == res.mangaId }?.thumbnailUrl
    res.newChapters.map { ch ->
        val flags = overrides[ch.id] ?: UpdatesRowFlags(read = ch.read, downloaded = false)
        UpdatedChapterItem(
            mangaId = res.mangaId,
            chapterId = ch.id,
            mangaTitle = res.title,
            chapterName = ch.name,
            chapterNumber = ch.chapterNumber,
            dateFetch = ch.dateFetch,
            mangaThumbnailUrl = mangaCover,
            read = flags.read,
            downloaded = flags.downloaded,
        )
    }
} ?: emptyList()
