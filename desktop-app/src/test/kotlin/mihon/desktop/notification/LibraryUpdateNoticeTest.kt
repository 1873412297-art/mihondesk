package mihon.desktop.notification

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.update.LibraryUpdateReport
import mihon.desktop.library.update.MangaUpdateItemResult
import org.junit.jupiter.api.Test

class LibraryUpdateNoticeTest {
    private fun chapter(id: Long, manga: Long, number: Double) = LibraryChapter(
        id = id, mangaId = manga, url = "/$id", name = "第${number.toInt()}话", scanlator = null, read = false,
        bookmark = false, lastPageRead = 0, dateFetch = 0, dateUpload = 0, chapterNumber = number,
        sourceOrder = 0, lastModifiedAt = 0, version = 0, memoJson = "{}",
    )

    private fun report(vararg results: MangaUpdateItemResult) = LibraryUpdateReport(
        totalMangaChecked = results.size,
        updatedMangaCount = results.count { it.newChapters.isNotEmpty() },
        newChaptersTotal = results.sumOf { it.newChapters.size },
        results = results.toList(),
        errors = emptyList(),
    )

    @Test
    fun `no new chapters means no notice`() {
        libraryUpdateNotice(report(MangaUpdateItemResult(1, "A", emptyList())), SimplifiedChineseStrings, false)
            .shouldBeNull()
    }

    @Test
    fun `a single updated manga jumps to its earliest new chapter with a localized message`() {
        val notice = libraryUpdateNotice(
            report(MangaUpdateItemResult(1, "葬送的芙莉莲", listOf(chapter(12, 1, 12.0), chapter(11, 1, 11.0)))),
            SimplifiedChineseStrings,
            hideContent = false,
        )!!
        notice.target shouldBe LibraryUpdateTarget.Chapter(chapterId = 11, mangaId = 1)
        notice.newChapterCount shouldBe 2
        notice.title shouldContain "书架"
        notice.message shouldContain "葬送的芙莉莲"
        notice.message shouldContain "第11话"
    }

    @Test
    fun `several updated manga open the updates feed and hidden content keeps titles out`() {
        val notice = libraryUpdateNotice(
            report(
                MangaUpdateItemResult(1, "Alpha", listOf(chapter(1, 1, 1.0))),
                MangaUpdateItemResult(2, "Beta", listOf(chapter(2, 2, 1.0))),
            ),
            SimplifiedChineseStrings,
            hideContent = true,
        )!!
        notice.target shouldBe LibraryUpdateTarget.Updates
        notice.message shouldNotContain "Alpha"
        notice.message shouldContain "2"
    }

    @Test
    fun `alerts accumulate the badge, route one click to the pending target and clear when seen`() {
        val shown = mutableListOf<String>()
        val badges = mutableListOf<Int>()
        val alerts = LibraryUpdateAlerts(
            display = { title, _ -> shown += title },
            badge = { badges += it },
            strings = { SimplifiedChineseStrings },
            hideContent = { false },
            enabled = { true },
        )
        alerts.onReport(report(MangaUpdateItemResult(1, "A", listOf(chapter(5, 1, 5.0)))))
        alerts.onReport(report(MangaUpdateItemResult(2, "B", listOf(chapter(6, 2, 1.0), chapter(7, 2, 2.0)))))
        shown.size shouldBe 2
        badges shouldContainExactly listOf(1, 3)
        alerts.unseenCount.value shouldBe 3
        alerts.consumeClick() shouldBe LibraryUpdateTarget.Chapter(chapterId = 6, mangaId = 2)
        alerts.consumeClick().shouldBeNull()
        alerts.markSeen()
        alerts.unseenCount.value shouldBe 0
        badges.last() shouldBe 0
    }

    @Test
    fun `disabled notifications still badge but never pop a balloon`() {
        val shown = mutableListOf<String>()
        val alerts = LibraryUpdateAlerts(
            display = { title, _ -> shown += title },
            badge = {},
            strings = { SimplifiedChineseStrings },
            hideContent = { false },
            enabled = { false },
        )
        alerts.onReport(report(MangaUpdateItemResult(1, "A", listOf(chapter(5, 1, 5.0)))))
        shown shouldBe emptyList()
        alerts.unseenCount.value shouldBe 1
    }
}
