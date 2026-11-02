package mihon.desktop.ui.browse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SelectFilterLayoutTest {

    private fun longOptions(): List<String> = buildList {
        add("全部")
        repeat(10) { add("類型 · 國漫$it") }
        repeat(10) { add("題材 · 玄幻$it") }
        repeat(10) { add("標籤 · 熱血$it") }
        add("狀態 · 連載中")
        add("狀態 · 已完結")
    }

    @Test
    fun `short lists use dropdown`() {
        selectFilterLayout(listOf("最新", "人氣", "評分")) shouldBe SelectFilterLayout.Dropdown
    }

    @Test
    fun `long prefixed lists use grouped chips`() {
        selectFilterLayout(longOptions()) shouldBe SelectFilterLayout.GroupedChips
    }

    @Test
    fun `long lists without shared prefixes keep dropdown`() {
        selectFilterLayout((1..30).map { "選項$it" }) shouldBe SelectFilterLayout.Dropdown
    }

    @Test
    fun `threshold is exclusive at twelve`() {
        val twelve = (1..12).map { "類型 · A$it" }
        selectFilterLayout(twelve) shouldBe SelectFilterLayout.Dropdown
        selectFilterLayout(twelve + "類型 · B") shouldBe SelectFilterLayout.GroupedChips
    }

    @Test
    fun `groups options by prefix preserving order and original indices`() {
        val groups = groupSelectOptions(listOf("全部", "類型 · 國漫", "題材 · 玄幻", "類型 · 日漫", "狀態·連載中"))
        groups.map { it.header } shouldBe listOf(null, "類型", "題材", "狀態")
        groups[0].options shouldBe listOf(SelectOption(0, "全部"))
        groups[1].options shouldBe listOf(SelectOption(1, "國漫"), SelectOption(3, "日漫"))
        groups[2].options shouldBe listOf(SelectOption(2, "玄幻"))
        groups[3].options shouldBe listOf(SelectOption(4, "連載中"))
    }

    @Test
    fun `dropdown max height fits about nine items`() {
        (THEMED_DROPDOWN_MAX_HEIGHT.value in 280f..340f) shouldBe true
    }
}
