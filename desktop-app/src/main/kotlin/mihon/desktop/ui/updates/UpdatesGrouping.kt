package mihon.desktop.ui.updates

import mihon.desktop.updates.UpdatedChapterItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class UpdatesGroupKind {
    Today,
    Yesterday,
    Date,
}

internal data class UpdatesDayGroup(
    val kind: UpdatesGroupKind,
    val date: LocalDate,
    val items: List<UpdatedChapterItem>,
)

/**
 * Groups update rows by fetch day (device timezone), newest day first and newest chapter first
 * within a day. Rows without a fetch timestamp fold into [today] so they are never dropped.
 */
internal fun groupUpdatesByDay(
    items: List<UpdatedChapterItem>,
    zone: ZoneId,
    today: LocalDate,
): List<UpdatesDayGroup> = items
    .groupBy { item ->
        if (item.dateFetch > 0L) {
            Instant.ofEpochMilli(item.dateFetch).atZone(zone).toLocalDate()
        } else {
            today
        }
    }
    .map { (date, dayItems) ->
        val kind = when (date) {
            today -> UpdatesGroupKind.Today
            today.minusDays(1) -> UpdatesGroupKind.Yesterday
            else -> UpdatesGroupKind.Date
        }
        UpdatesDayGroup(kind, date, dayItems.sortedByDescending { it.dateFetch })
    }
    .sortedByDescending { it.date }
