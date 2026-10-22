package mihon.desktop.activity

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.library.update.LibraryUpdateReport
import mihon.desktop.library.update.MangaUpdateItemResult
import org.junit.jupiter.api.Test

class MissingSourceActivityTest {
    private fun report(vararg results: MangaUpdateItemResult) =
        LibraryUpdateReport(results.size, 0, 0, results.toList(), results.mapNotNull { it.error })

    @Test
    fun `groups manga whose source extension is not installed`() {
        val groups = missingSourceGroups(
            report(
                MangaUpdateItemResult(1, "慾債", emptyList(), "x", missingSourceId = 36L),
                MangaUpdateItemResult(2, "B", emptyList(), "x", missingSourceId = 36L),
                MangaUpdateItemResult(3, "C", emptyList(), "other error"),
            ),
        )
        groups shouldBe mapOf(36L to listOf("慾債", "B"))
    }

    @Test
    fun `installable source offers install and migrate actions with a friendly reason`() {
        var installed = 0
        var migrated = 0
        val item = missingSourceActivity(
            sourceId = 36L,
            sourceName = "肉漫屋",
            mangaTitles = listOf("慾債", "B"),
            strings = SimplifiedChineseStrings,
            onInstall = { installed++ },
            onMigrate = { migrated++ },
        )
        item.id shouldBe "missing-source-36"
        item.kind shouldBe ActivityKind.EXTENSION_INSTALL
        item.state shouldBe ActivityState.FAILED
        item.subject shouldBe "肉漫屋"
        item.failureReason!! shouldContain "慾債"
        item.failureReason!! shouldNotContain "扩展包文件缺失"
        item.actions.map { it.label } shouldContainExactly listOf("安装扩展", "迁移到其他图源")
        item.actions.forEach { it.run() }
        installed shouldBe 1
        migrated shouldBe 1
    }

    @Test
    fun `source not found in repos only offers migration`() {
        val item = missingSourceActivity(36L, null, listOf("慾債"), SimplifiedChineseStrings, null, {})
        item.subject shouldBe "36"
        item.actions.map { it.label } shouldContainExactly listOf("迁移到其他图源")
    }
}
