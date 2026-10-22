package mihon.desktop.i18n

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.desktop.ui.updates.UpdatesGroupKind
import org.junit.jupiter.api.Test

class GroupHeaderLocalizationTest {
    private val historyGroups = listOf("Today", "Yesterday", "Earlier this week", "This month", "Older")

    @Test
    fun `every history group header is localized in Chinese`() {
        historyGroups.map { SimplifiedChineseStrings.historyGroupTitle(it) } shouldBe
            listOf("今天", "昨天", "本周早些时候", "本月", "更早")
        historyGroups.map { TraditionalChineseStrings.historyGroupTitle(it) } shouldBe
            listOf("今天", "昨天", "本週稍早", "本月", "更早")
        historyGroups.map { EnglishStrings.historyGroupTitle(it) } shouldBe historyGroups
    }

    @Test
    fun `every updates group header has a Chinese label`() {
        val keys = mapOf(
            UpdatesGroupKind.Today to UiText.UpdatesGroupToday,
            UpdatesGroupKind.Yesterday to UiText.UpdatesGroupYesterday,
            UpdatesGroupKind.ThisWeek to UiText.UpdatesGroupThisWeek,
            UpdatesGroupKind.Earlier to UiText.UpdatesGroupEarlier,
        )
        keys.keys shouldBe UpdatesGroupKind.entries.toSet()
        keys.values.forEach { key ->
            SimplifiedChineseStrings.text(key) shouldNotBe EnglishStrings.text(key)
            TraditionalChineseStrings.text(key) shouldNotBe EnglishStrings.text(key)
        }
    }
}
