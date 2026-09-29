package mihon.desktop.library.update

import mihon.desktop.library.model.LibraryChapter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Uses observed chapter dates to avoid checking titles before their next likely release window. */
internal object ReleasePeriodFilter {
    fun shouldSkip(
        chapters: List<LibraryChapter>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val latestFetch = chapters.maxOfOrNull { it.dateFetch }?.takeIf { it > 0L } ?: return false
        val windowSize = if (chapters.size <= 8) 3 else 10
        val uploadDates = chapters.asSequence().map { it.dateUpload }.filter { it > 0L }
            .map { it.toLocalDate(zone) }.distinct().sortedDescending().take(windowSize).toList()
        val fetchDates = chapters.asSequence().map { it.dateFetch }.filter { it > 0L }
            .map { it.toLocalDate(zone) }.distinct().sortedDescending().take(windowSize).toList()
        val interval = when {
            uploadDates.size >= 3 -> medianInterval(uploadDates)
            fetchDates.size >= 3 -> medianInterval(fetchDates)
            else -> 7
        }.coerceIn(1, 28)
        val nextReleaseDate = latestFetch.toLocalDate(zone).plusDays(interval.toLong())
        val upperWindowDate = nowMillis.toLocalDate(zone).plusDays(1)
        return nextReleaseDate.isAfter(upperWindowDate)
    }

    private fun medianInterval(dates: List<LocalDate>): Int {
        val gaps = dates.windowed(2).map { (newer, older) ->
            ChronoUnit.DAYS.between(older, newer).toInt()
        }.sorted()
        return gaps[(gaps.size - 1) / 2]
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
