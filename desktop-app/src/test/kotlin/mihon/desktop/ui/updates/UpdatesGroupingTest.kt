package mihon.desktop.ui.updates

import io.kotest.matchers.shouldBe
import mihon.desktop.updates.UpdatedChapterItem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UpdatesGroupingTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 27)

    private fun item(id: Long, dateFetch: Long) = UpdatedChapterItem(
        mangaId = id,
        chapterId = id,
        mangaTitle = "Manga $id",
        chapterName = "Chapter $id",
        chapterNumber = id.toDouble(),
        dateFetch = dateFetch,
    )

    @Test
    fun `groups today yesterday and this week newest first`() {
        val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayMillis = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val olderMillis = today.minusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()

        val groups = groupUpdatesByDay(
            items = listOf(
                item(1, olderMillis),
                item(2, yesterdayMillis + 60_000),
                item(3, yesterdayMillis),
                item(4, todayMillis + 3_600_000),
                item(5, todayMillis),
            ),
            zone = zone,
            today = today,
        )

        groups.map { it.kind } shouldBe listOf(
            UpdatesGroupKind.Today,
            UpdatesGroupKind.Yesterday,
            UpdatesGroupKind.ThisWeek,
        )
        groups[0].items.map { it.chapterId } shouldBe listOf(4, 5)
        groups[1].items.map { it.chapterId } shouldBe listOf(2, 3)
        groups[2].items.map { it.chapterId } shouldBe listOf(1)
    }

    @Test
    fun `rows without fetch timestamp fold into today instead of being dropped`() {
        val groups = groupUpdatesByDay(
            items = listOf(item(1, 0L)),
            zone = zone,
            today = today,
        )

        groups.single().kind shouldBe UpdatesGroupKind.Today
        groups.single().items.single().chapterId shouldBe 1L
    }

    @Test
    fun `empty input produces no groups`() {
        groupUpdatesByDay(emptyList(), zone, today) shouldBe emptyList()
    }

    @Test
    fun `older than a week lands in earlier and chapters of one manga merge into one entry`() {
        val at = { daysAgo: Long, minute: Long ->
            today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli() + minute * 60_000
        }
        fun chapter(manga: Long, id: Long, fetch: Long) = item(id, fetch).copy(mangaId = manga, mangaTitle = "M$manga")
        val groups = groupUpdatesByDay(
            items = listOf(
                chapter(1, 11, at(0, 1)),
                chapter(1, 12, at(0, 5)),
                chapter(2, 21, at(0, 3)),
                chapter(3, 31, at(6, 0)),
                chapter(4, 41, at(7, 0)),
                chapter(4, 42, at(30, 0)),
            ),
            zone = zone,
            today = today,
        )
        groups.map { it.kind } shouldBe
            listOf(UpdatesGroupKind.Today, UpdatesGroupKind.ThisWeek, UpdatesGroupKind.Earlier)
        val todayEntries = groups[0].entries
        todayEntries.map { it.mangaId } shouldBe listOf(1L, 2L)
        todayEntries[0].latest.chapterId shouldBe 12L
        todayEntries[0].chapters.size shouldBe 2
        groups[2].entries.single().chapters.map { it.chapterId } shouldBe listOf(41L, 42L)
    }
}
