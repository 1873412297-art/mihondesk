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
    fun `groups today yesterday and specific dates newest first`() {
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
            UpdatesGroupKind.Date,
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
}
