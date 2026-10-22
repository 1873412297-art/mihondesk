package mihon.desktop.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryChapter
import org.junit.jupiter.api.Test

class ChapterVolumeAndScanlatorTest {
    @Test
    fun `volume numbers are parsed from common chapter name formats`() {
        parseChapterVolume("Vol.2 Ch.10") shouldBe 2
        parseChapterVolume("Volume 3 Chapter 1") shouldBe 3
        parseChapterVolume("vol 12 - Finale") shouldBe 12
        parseChapterVolume("第4卷 第30话") shouldBe 4
        parseChapterVolume("第 5 巻 第1話") shouldBe 5
        parseChapterVolume("Chapter 5") shouldBe null
        parseChapterVolume("Revolution 3") shouldBe null
    }

    @Test
    fun `chapter list gets a header whenever the volume changes`() {
        val chapters = listOf(
            chapter(1, "Vol.1 Ch.1", 1.0),
            chapter(2, "Vol.1 Ch.2", 2.0),
            chapter(3, "Vol.2 Ch.3", 3.0),
            chapter(4, "Ch.4", 4.0),
        )
        val items = buildChapterListItems(chapters, defaultSettings())
        items.map { it.key } shouldContainExactly listOf(
            "volume-1",
            "chapter-1",
            "chapter-2",
            "volume-2",
            "chapter-3",
            "volume-none",
            "chapter-4",
        )
        (items.first() as ChapterListItem.VolumeHeader).chapterCount shouldBe 2
    }

    @Test
    fun `no volume headers when the source provides no volumes or grouping is off`() {
        val plain = listOf(chapter(1, "Ch.1", 1.0), chapter(2, "Ch.2", 2.0))
        buildChapterListItems(plain, defaultSettings()).none { it is ChapterListItem.VolumeHeader } shouldBe true
        val withVolumes = listOf(chapter(1, "Vol.1 Ch.1", 1.0), chapter(2, "Vol.2 Ch.2", 2.0))
        buildChapterListItems(withVolumes, defaultSettings(), groupByVolume = false)
            .none { it is ChapterListItem.VolumeHeader } shouldBe true
    }

    @Test
    fun `preferred scanlators win when several groups release the same chapter`() {
        val chapters = listOf(
            chapter(1, "Ch.1", 1.0, "Group B"),
            chapter(2, "Ch.1", 1.0, "Group A"),
            chapter(3, "Ch.2", 2.0, "Group B"),
            chapter(4, "Ch.3", 3.0, "Unknown"),
            chapter(5, "Ch.3", 3.0, null),
            chapter(6, "Extra", -1.0, "Group B"),
            chapter(7, "Extra", -1.0, "Group A"),
        )
        applyScanlatorPriority(chapters, listOf("group a", " Group B ")).map { it.id } shouldContainExactly
            listOf(2L, 3L, 4L, 5L, 6L, 7L)
        applyScanlatorPriority(chapters, emptyList()) shouldBe chapters
    }

    @Test
    fun `preferred scanlator list parses one per line or comma separated`() {
        parsePreferredScanlators("Group A\n Group B ,Group C\n\n") shouldContainExactly
            listOf("Group A", "Group B", "Group C")
        parsePreferredScanlators(null) shouldBe emptyList()
    }

    private fun defaultSettings() = chapterSettingsFromFlags(0L, showMissingChapters = false)

    private fun chapter(id: Long, name: String, number: Double, scanlator: String? = null) = LibraryChapter(
        id = id,
        mangaId = 1,
        url = "/c/$id",
        name = name,
        scanlator = scanlator,
        read = false,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        dateUpload = 0,
        chapterNumber = number,
        sourceOrder = id,
        lastModifiedAt = 0,
        version = 0,
        memoJson = "{}",
    )
}
