package mihon.desktop.ui.updates

import mihon.desktop.updates.UpdatedChapterItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class UpdatesGroupKind {
    Today,
    Yesterday,
    ThisWeek,
    Earlier,
}

/** All new chapters of one manga inside a group, shown as a single entry. */
internal data class UpdatesMangaEntry(
    val mangaId: Long,
    val latest: UpdatedChapterItem,
    val chapters: List<UpdatedChapterItem>,
)

internal data class UpdatesDayGroup(
    val kind: UpdatesGroupKind,
    val date: LocalDate,
    val items: List<UpdatedChapterItem>,
) {
    /** One entry per manga, ordered by that manga's newest chapter. */
    val entries: List<UpdatesMangaEntry>
        get() = items.groupBy { it.mangaId }.map { (mangaId, chapters) ->
            UpdatesMangaEntry(mangaId, chapters.first(), chapters)
        }
}

/**
 * Groups update rows into 今天 / 昨天 / 本周 / 更早 (device timezone), newest first; within a group rows
 * are newest first and [UpdatesDayGroup.entries] merges each manga. Rows without a fetch timestamp fold
 * into today so they are never dropped.
 */
internal fun groupUpdatesByDay(
    items: List<UpdatedChapterItem>,
    zone: ZoneId,
    today: LocalDate,
): List<UpdatesDayGroup> {
    fun dayOf(item: UpdatedChapterItem): LocalDate = if (item.dateFetch > 0L) {
        Instant.ofEpochMilli(item.dateFetch).atZone(zone).toLocalDate()
    } else {
        today
    }
    fun kindOf(date: LocalDate): UpdatesGroupKind = when {
        !date.isBefore(today) -> UpdatesGroupKind.Today
        date == today.minusDays(1) -> UpdatesGroupKind.Yesterday
        !date.isBefore(today.minusDays(6)) -> UpdatesGroupKind.ThisWeek
        else -> UpdatesGroupKind.Earlier
    }
    return items
        .groupBy { kindOf(dayOf(it)) }
        .map { (kind, groupItems) ->
            val sorted = groupItems.sortedByDescending { it.dateFetch }
            UpdatesDayGroup(kind, sorted.maxOf { dayOf(it) }, sorted)
        }
        .sortedBy { it.kind.ordinal }
}
