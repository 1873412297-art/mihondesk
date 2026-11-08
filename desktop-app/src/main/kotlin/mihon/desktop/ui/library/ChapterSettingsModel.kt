package mihon.desktop.ui.library

import androidx.compose.foundation.lazy.items
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import kotlin.math.floor

data class ChapterSettings(
    val displayMode: ChapterDisplayMode = ChapterDisplayMode.Name,
    val sortMode: ChapterSortMode = ChapterSortMode.SourceOrder,
    val sortAscending: Boolean = false,
    val unreadFilter: TriStateFilter = TriStateFilter.Disabled,
    val downloadedFilter: TriStateFilter = TriStateFilter.Disabled,
    val bookmarkedFilter: TriStateFilter = TriStateFilter.Disabled,
    val excludedScanlators: Set<String> = emptySet(),
    val showMissingChapters: Boolean = true,
) {
    fun toFilterState(): ChapterFilterState = ChapterFilterState(
        unread = unreadFilter,
        downloaded = downloadedFilter,
        bookmarked = bookmarkedFilter,
    )

    fun toSortState(): ChapterSortState = ChapterSortState(
        mode = sortMode,
        ascending = sortAscending,
    )
}

sealed interface ChapterListItem {
    val key: String

    data class Chapter(
        val chapter: LibraryChapter,
        val label: String,
    ) : ChapterListItem {
        override val key: String get() = "chapter-${chapter.id}"
    }

    data class MissingCount(
        val afterChapterId: Long?,
        val beforeChapterId: Long?,
        val count: Int,
    ) : ChapterListItem {
        override val key: String get() = "missing-${afterChapterId ?: -1}-${beforeChapterId ?: -1}"
    }

    /** Starts a run of chapters from one volume (null = chapters without a volume). */
    data class VolumeHeader(
        val volume: Int?,
        val chapterCount: Int,
        override val key: String,
    ) : ChapterListItem
}

fun chapterSettingsFromFlags(
    chapterFlags: Long,
    showMissingChapters: Boolean = true,
    excludedScanlators: Set<String> = emptySet(),
): ChapterSettings {
    val displayMode = when (chapterFlags and CHAPTER_DISPLAY_MASK) {
        CHAPTER_DISPLAY_NUMBER -> ChapterDisplayMode.Number
        CHAPTER_DISPLAY_SOURCE_ORDER -> ChapterDisplayMode.SourceOrder
        else -> ChapterDisplayMode.Name
    }
    val sortMode = when (chapterFlags and CHAPTER_SORTING_MASK) {
        CHAPTER_SORTING_NUMBER -> ChapterSortMode.ChapterNumber
        CHAPTER_SORTING_UPLOAD_DATE -> ChapterSortMode.UploadDate
        else -> ChapterSortMode.SourceOrder
    }
    val sortAscending = chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_ASC
    val unreadFilter = when (chapterFlags and CHAPTER_UNREAD_MASK) {
        CHAPTER_SHOW_UNREAD -> TriStateFilter.Include
        CHAPTER_SHOW_READ -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    val downloadedFilter = when (chapterFlags and CHAPTER_DOWNLOADED_MASK) {
        CHAPTER_SHOW_DOWNLOADED -> TriStateFilter.Include
        CHAPTER_SHOW_NOT_DOWNLOADED -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    val bookmarkedFilter = when (chapterFlags and CHAPTER_BOOKMARKED_MASK) {
        CHAPTER_SHOW_BOOKMARKED -> TriStateFilter.Include
        CHAPTER_SHOW_NOT_BOOKMARKED -> TriStateFilter.Exclude
        else -> TriStateFilter.Disabled
    }
    return ChapterSettings(
        displayMode = displayMode,
        sortMode = sortMode,
        sortAscending = sortAscending,
        unreadFilter = unreadFilter,
        downloadedFilter = downloadedFilter,
        bookmarkedFilter = bookmarkedFilter,
        excludedScanlators = excludedScanlators,
        showMissingChapters = showMissingChapters,
    )
}

fun encodeChapterFlags(existingFlags: Long, settings: ChapterSettings): Long {
    val displayFlag = when (settings.displayMode) {
        ChapterDisplayMode.Name -> CHAPTER_DISPLAY_NAME
        ChapterDisplayMode.Number -> CHAPTER_DISPLAY_NUMBER
        ChapterDisplayMode.SourceOrder -> CHAPTER_DISPLAY_SOURCE_ORDER
    }
    val sortFlag = when (settings.sortMode) {
        ChapterSortMode.SourceOrder -> CHAPTER_SORTING_SOURCE
        ChapterSortMode.ChapterNumber -> CHAPTER_SORTING_NUMBER
        ChapterSortMode.UploadDate -> CHAPTER_SORTING_UPLOAD_DATE
    }
    val directionFlag = if (settings.sortAscending) CHAPTER_SORT_ASC else CHAPTER_SORT_DESC
    val unreadFlag = when (settings.unreadFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_UNREAD
        TriStateFilter.Exclude -> CHAPTER_SHOW_READ
        TriStateFilter.Disabled -> 0L
    }
    val downloadedFlag = when (settings.downloadedFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_DOWNLOADED
        TriStateFilter.Exclude -> CHAPTER_SHOW_NOT_DOWNLOADED
        TriStateFilter.Disabled -> 0L
    }
    val bookmarkedFlag = when (settings.bookmarkedFilter) {
        TriStateFilter.Include -> CHAPTER_SHOW_BOOKMARKED
        TriStateFilter.Exclude -> CHAPTER_SHOW_NOT_BOOKMARKED
        TriStateFilter.Disabled -> 0L
    }
    return existingFlags
        .setFlag(displayFlag, CHAPTER_DISPLAY_MASK)
        .setFlag(sortFlag, CHAPTER_SORTING_MASK)
        .setFlag(directionFlag, CHAPTER_SORT_DIR_MASK)
        .setFlag(unreadFlag, CHAPTER_UNREAD_MASK)
        .setFlag(downloadedFlag, CHAPTER_DOWNLOADED_MASK)
        .setFlag(bookmarkedFlag, CHAPTER_BOOKMARKED_MASK)
}

private fun Long.setFlag(flag: Long, mask: Long): Long = (this and mask.inv()) or (flag and mask)

fun parseExcludedScanlators(raw: String): Set<String> = runCatching {
    Json.decodeFromString<List<String>>(raw)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}.getOrDefault(emptySet())

fun encodeExcludedScanlators(values: Set<String>): String = Json.encodeToString(values.sorted())

fun parseShowMissingChapters(memoJson: String): Boolean {
    val root = runCatching { Json.parseToJsonElement(memoJson) as? JsonObject }.getOrNull() ?: return true
    val chapterSettings = root["chapterSettings"] as? JsonObject ?: return true
    return chapterSettings["showMissingChapters"]?.jsonPrimitive?.booleanOrNull ?: true
}

fun encodeShowMissingChapters(memoJson: String, showMissingChapters: Boolean): String {
    val root = runCatching { Json.parseToJsonElement(memoJson) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    val existing = (root["chapterSettings"] as? JsonObject) ?: JsonObject(emptyMap())
    val updatedSettings = JsonObject(existing + ("showMissingChapters" to JsonPrimitive(showMissingChapters)))
    return Json.encodeToString(JsonObject(root + ("chapterSettings" to updatedSettings)))
}

fun MangaDetails.toChapterSettings(): ChapterSettings = chapterSettingsFromFlags(
    chapterFlags = chapterFlags,
    showMissingChapters = parseShowMissingChapters(memoJson),
    excludedScanlators = parseExcludedScanlators(excludedScanlatorsJson),
)

fun formatChapterNumber(number: Double): String? {
    if (!number.isFinite() || number < 0.0) return null
    val integral = floor(number)
    return if (number == integral) integral.toLong().toString() else number.toString()
}

fun chapterDisplayLabel(
    chapter: LibraryChapter,
    displayMode: ChapterDisplayMode,
    strings: mihon.desktop.i18n.DesktopStrings = mihon.desktop.i18n.EnglishStrings,
): String = when (displayMode) {
    ChapterDisplayMode.Name -> chapter.name
    ChapterDisplayMode.Number -> formatChapterNumber(chapter.chapterNumber) ?: chapter.name
    ChapterDisplayMode.SourceOrder -> strings.text(UiText.SourceOrderNumber, chapter.sourceOrder + 1)
}

fun calculateChapterGap(higherChapterNumber: Double, lowerChapterNumber: Double): Int {
    if (!higherChapterNumber.isFinite() || !lowerChapterNumber.isFinite()) return 0
    if (higherChapterNumber < 0.0 || lowerChapterNumber < 0.0) return 0
    return floor(higherChapterNumber).toInt() - floor(lowerChapterNumber).toInt() - 1
}

fun calculateMissingChapterCount(chapters: List<LibraryChapter>): Int {
    val numbers = chapters
        .map { it.chapterNumber }
        .filter { it.isFinite() && it >= 0.0 }
        .sorted()
    return numbers.zipWithNext().sumOf { (lower, higher) ->
        calculateChapterGap(higherChapterNumber = higher, lowerChapterNumber = lower).coerceAtLeast(0)
    }
}

fun buildChapterListItems(
    chapters: List<LibraryChapter>,
    settings: ChapterSettings,
    groupByVolume: Boolean = true,
): List<ChapterListItem> {
    if (chapters.isEmpty()) return emptyList()
    val volumes = chapters.map { parseChapterVolume(it.name) }
    val showVolumes = groupByVolume && volumes.any { it != null }
    val items = ArrayList<ChapterListItem>(chapters.size + 8)
    val usedVolumeKeys = HashSet<String>()
    chapters.forEachIndexed { index, chapter ->
        if (showVolumes && (index == 0 || volumes[index] != volumes[index - 1])) {
            val volume = volumes[index]
            var runLength = 0
            while (index + runLength < chapters.size && volumes[index + runLength] == volume) runLength++
            val baseKey = "volume-${volume ?: "none"}"
            // Interleaved volumes (unusual sort orders) must still yield unique lazy-list keys.
            val key = if (usedVolumeKeys.add(baseKey)) baseKey else "$baseKey-${chapter.id}"
            items += ChapterListItem.VolumeHeader(volume, runLength, key)
        }
        items += ChapterListItem.Chapter(
            chapter = chapter,
            label = chapterDisplayLabel(chapter, settings.displayMode),
        )
        if (!settings.showMissingChapters) return@forEachIndexed
        val next = chapters.getOrNull(index + 1) ?: return@forEachIndexed
        val higher = maxOf(chapter.chapterNumber, next.chapterNumber)
        val lower = minOf(chapter.chapterNumber, next.chapterNumber)
        val gap = calculateChapterGap(higher, lower)
        if (gap > 0) {
            items += ChapterListItem.MissingCount(
                afterChapterId = chapter.id,
                beforeChapterId = next.id,
                count = gap,
            )
        }
    }
    return items
}
