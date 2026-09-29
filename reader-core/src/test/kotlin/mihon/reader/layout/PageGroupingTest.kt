package mihon.reader.layout

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderLayoutPolicy
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import org.junit.jupiter.api.Test

class PageGroupingTest {
    @Test
    fun `dual grouping pairs pages from the first page when no cover is reserved`() {
        PageGrouping.dual(pageDescriptors(5), reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(0), pageId(1)),
                listOf(pageId(2), pageId(3)),
                listOf(pageId(4)),
            )
    }

    @Test
    fun `dual grouping keeps the cover alone before pairing remaining pages`() {
        PageGrouping.dual(pageDescriptors(5), reserveCover = true)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(0)),
                listOf(pageId(1), pageId(2)),
                listOf(pageId(3), pageId(4)),
            )
    }

    @Test
    fun `right to left visual placement reverses each spread without changing page identity`() {
        val original = pageDescriptors(4)

        PageGrouping.forMode(original, ReadingMode.DUAL_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(1), pageId(0)),
                listOf(pageId(3), pageId(2)),
            )

        original.map(PageDescriptor::id).shouldContainExactly(pageId(0), pageId(1), pageId(2), pageId(3))
    }

    @Test
    fun `spread snapshots stay immutable after a caller mutates its page list`() {
        val pages = pageDescriptors(4).toMutableList()
        val ltr = PageGrouping.dual(pages, reserveCover = false)
        val rtl = PageGrouping.forMode(pages, ReadingMode.DUAL_RTL, reserveCover = false)

        pages.clear()

        ltr.map { it.map(PageDescriptor::id) }.shouldContainExactly(
            listOf(pageId(0), pageId(1)),
            listOf(pageId(2), pageId(3)),
        )
        rtl.map { it.map(PageDescriptor::id) }.shouldContainExactly(
            listOf(pageId(1), pageId(0)),
            listOf(pageId(3), pageId(2)),
        )
    }

    @Test
    fun `all reader modes produce their concrete grouping and layout policies`() {
        val pages = pageDescriptors(4)

        PageGrouping.forMode(pages, ReadingMode.SINGLE_LTR, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.SINGLE_LTR) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = false,
            continuousGapPixels = 0,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.SINGLE_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.SINGLE_RTL).isRightToLeft shouldBe true

        PageGrouping.forMode(pages, ReadingMode.DUAL_LTR, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0), pageId(1)), listOf(pageId(2), pageId(3)))
        ReaderLayout.policy(ReadingMode.DUAL_LTR).isDualPage shouldBe true

        PageGrouping.forMode(pages, ReadingMode.DUAL_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(1), pageId(0)), listOf(pageId(3), pageId(2)))
        ReaderLayout.policy(ReadingMode.DUAL_RTL) shouldBe ReaderLayoutPolicy(
            isRightToLeft = true,
            isDualPage = true,
            isContinuous = false,
            continuousGapPixels = 0,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.VERTICAL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.VERTICAL) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = true,
            continuousGapPixels = ReaderLayout.DEFAULT_CONTINUOUS_GAP_PIXELS,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.WEBTOON, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.WEBTOON) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = true,
            continuousGapPixels = 0,
            forcedScaleMode = ScaleMode.FIT_WIDTH,
        )
    }

    @Test
    fun `shouldSplit decision respects NEVER ALWAYS and WIDE modes`() {
        val widePage = PageDescriptor(id = pageId(0), width = 400, height = 200)
        val portraitPage = PageDescriptor(id = pageId(1), width = 100, height = 200)
        val squarePage = PageDescriptor(id = pageId(2), width = 200, height = 200)

        // NEVER never splits
        PageGrouping.shouldSplit(widePage, DualPageSplit.NEVER) shouldBe false
        PageGrouping.shouldSplit(portraitPage, DualPageSplit.NEVER) shouldBe false

        // WIDE only splits wide images (width > height)
        PageGrouping.shouldSplit(widePage, DualPageSplit.WIDE) shouldBe true
        PageGrouping.shouldSplit(portraitPage, DualPageSplit.WIDE) shouldBe false
        PageGrouping.shouldSplit(squarePage, DualPageSplit.WIDE) shouldBe false

        // ALWAYS splits all pages
        PageGrouping.shouldSplit(widePage, DualPageSplit.ALWAYS) shouldBe true
        PageGrouping.shouldSplit(portraitPage, DualPageSplit.ALWAYS) shouldBe true
        PageGrouping.shouldSplit(squarePage, DualPageSplit.ALWAYS) shouldBe true

        // rotateToFit suppresses split for wide images
        PageGrouping.shouldSplit(widePage, DualPageSplit.WIDE, rotateToFit = true) shouldBe false
        PageGrouping.shouldSplit(widePage, DualPageSplit.ALWAYS, rotateToFit = true) shouldBe false
    }

    @Test
    fun `splitPages splits wide images with left half first in LTR and right half first in RTL`() {
        val widePage = PageDescriptor(id = pageId(0), width = 401, height = 200)

        val ltrSplit = PageGrouping.splitPages(listOf(widePage), DualPageSplit.WIDE, isRightToLeft = false)
        ltrSplit.size shouldBe 2
        ltrSplit[0].splitSide shouldBe mihon.reader.model.SplitSide.LEFT
        ltrSplit[0].width shouldBe 200
        ltrSplit[0].height shouldBe 200
        ltrSplit[1].splitSide shouldBe mihon.reader.model.SplitSide.RIGHT
        ltrSplit[1].width shouldBe 201
        ltrSplit[1].height shouldBe 200

        // RTL puts right half first
        val rtlSplit = PageGrouping.splitPages(listOf(widePage), DualPageSplit.WIDE, isRightToLeft = true)
        rtlSplit.size shouldBe 2
        rtlSplit[0].splitSide shouldBe mihon.reader.model.SplitSide.RIGHT
        rtlSplit[0].width shouldBe 201
        rtlSplit[0].height shouldBe 200
        rtlSplit[1].splitSide shouldBe mihon.reader.model.SplitSide.LEFT
        rtlSplit[1].width shouldBe 200
        rtlSplit[1].height shouldBe 200
    }

    @Test
    fun `splitPages with rotateToFit rotates wide page 90 degrees without splitting`() {
        val widePage = PageDescriptor(id = pageId(0), width = 400, height = 200)
        val result = PageGrouping.splitPages(listOf(widePage), DualPageSplit.WIDE, rotateToFit = true)
        result.size shouldBe 1
        result[0].width shouldBe 200
        result[0].height shouldBe 400
        result[0].rotated shouldBe true
        result[0].splitSide shouldBe null
    }

    @Test
    fun `dual grouping pairs split halves into spread in LTR and reverses spread in RTL`() {
        val widePage = PageDescriptor(id = pageId(0), width = 400, height = 200)
        val ltrGroups = PageGrouping.dual(
            listOf(widePage),
            reserveCover = false,
            split = DualPageSplit.WIDE,
            isRightToLeft = false,
        )
        ltrGroups.size shouldBe 1
        ltrGroups[0].size shouldBe 2
        ltrGroups[0][0].splitSide shouldBe mihon.reader.model.SplitSide.LEFT
        ltrGroups[0][1].splitSide shouldBe mihon.reader.model.SplitSide.RIGHT

        // In RTL forMode, spread is reversed for visual presentation: Left on left, Right on right
        val rtlGroups = PageGrouping.forMode(
            listOf(widePage),
            ReadingMode.DUAL_RTL,
            reserveCover = false,
            split = DualPageSplit.WIDE,
        )
        rtlGroups.size shouldBe 1
        rtlGroups[0].size shouldBe 2
        rtlGroups[0][0].splitSide shouldBe mihon.reader.model.SplitSide.LEFT
        rtlGroups[0][1].splitSide shouldBe mihon.reader.model.SplitSide.RIGHT
    }

    @Test
    fun `dual grouping with reserveCover keeps cover alone and pairs wide page halves together`() {
        val cover = PageDescriptor(id = pageId(0), width = 100, height = 200)
        val wide = PageDescriptor(id = pageId(1), width = 400, height = 200)
        val regular = PageDescriptor(id = pageId(2), width = 100, height = 200)

        val groups = PageGrouping.dual(listOf(cover, wide, regular), reserveCover = true, split = DualPageSplit.WIDE)
        groups.size shouldBe 3
        groups[0].size shouldBe 1
        groups[0][0].id shouldBe pageId(0)

        groups[1].size shouldBe 2
        groups[1][0].id shouldBe pageId(1)
        groups[1][0].splitSide shouldBe mihon.reader.model.SplitSide.LEFT
        groups[1][1].id shouldBe pageId(1)
        groups[1][1].splitSide shouldBe mihon.reader.model.SplitSide.RIGHT

        groups[2].size shouldBe 1
        groups[2][0].id shouldBe pageId(2)
    }
}

private fun pageDescriptors(count: Int) = (0 until count).map { index ->
    PageDescriptor(id = pageId(index), width = 100, height = 200)
}

private fun pageId(index: Int) = PageId(chapterId = "chapter", entryName = "page-$index.jpg")
