package mihon.desktop.ui.palette

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test

class CommandPaletteModelTest {
    private fun item(key: String, title: String, vararg keywords: String) = PaletteItem(
        key = key,
        title = title,
        subtitle = null,
        target = PaletteTarget.Command(PaletteCommand.OpenLibrary),
        keywords = keywords.toList(),
    )

    @Test
    fun `match score prefers exact, then prefix, then word prefix, then substring, then subsequence`() {
        val exact = matchScore("one piece", "One Piece")!!
        val prefix = matchScore("one", "One Piece")!!
        val word = matchScore("pie", "One Piece")!!
        val contains = matchScore("iec", "One Piece")!!
        val subsequence = matchScore("opc", "One Piece")!!
        (exact > prefix) shouldBe true
        (prefix > word) shouldBe true
        (word > contains) shouldBe true
        (contains > subsequence) shouldBe true
        matchScore("xyz", "One Piece").shouldBeNull()
        (matchScore("葬送", "葬送的芙莉莲")!! > matchScore("芙莉莲", "葬送的芙莉莲")!!) shouldBe true
    }

    @Test
    fun `filter ranks by best score over title and keywords and caps results`() {
        val items = listOf(
            item("a", "Downloads", "下载"),
            item("b", "Download settings"),
            item("c", "Library"),
            item("d", "Reader", "下载器"),
        )
        filterPaletteItems("下载", items, limit = 5).map { it.key } shouldContainExactly listOf("a", "d")
        filterPaletteItems("down", items, limit = 1).map { it.key } shouldContainExactly listOf("a")
        filterPaletteItems("  ", items, limit = 2).map { it.key } shouldContainExactly listOf("a", "b")
    }

    @Test
    fun `selection wraps around both ends and is safe on empty lists`() {
        moveSelection(current = 0, delta = -1, size = 3) shouldBe 2
        moveSelection(current = 2, delta = 1, size = 3) shouldBe 0
        moveSelection(current = 1, delta = 1, size = 3) shouldBe 2
        moveSelection(current = 5, delta = 1, size = 0) shouldBe 0
    }

    @Test
    fun `source search merges every source's results grouped in source order and keeps failures`() = runBlocking {
        val alpha = SourceDescriptor(id = 1, name = "Alpha", lang = "zh", className = "A")
        val beta = SourceDescriptor(id = 2, name = "Beta", lang = "en", className = "B")
        val gamma = SourceDescriptor(id = 3, name = "Gamma", lang = "en", className = "C")
        val search = PaletteSourceSearch(
            sources = { listOf(alpha, beta, gamma) },
            search = { sourceId, query ->
                when (sourceId) {
                    1L -> (1..8).map { manga("$query-a$it") }
                    2L -> error("boom")
                    else -> emptyList()
                }
            },
            perSourceLimit = 3,
        )
        val snapshots = mutableListOf<List<PaletteSourceGroup>>()
        search.run("naruto") { snapshots += it }
        val final = snapshots.last()
        final.map { it.source.name } shouldContainExactly listOf("Alpha", "Beta", "Gamma")
        final.all { !it.loading } shouldBe true
        final[0].mangas.map { it.title } shouldContainExactly listOf("naruto-a1", "naruto-a2", "naruto-a3")
        (final[1].error != null) shouldBe true
        final[2].mangas shouldBe emptyList()
        snapshots.first().all { it.loading } shouldBe true
        paletteSourceItems(final).map { it.key } shouldContainExactly
            listOf("source-1-naruto-a1", "source-1-naruto-a2", "source-1-naruto-a3")
    }

    @Test
    fun `blank or one-character queries do not hit the network`() = runBlocking {
        var calls = 0
        val search = PaletteSourceSearch(
            sources = { listOf(SourceDescriptor(id = 1, name = "A", lang = "en", className = "A")) },
            search = { _, _ ->
                calls++
                emptyList()
            },
        )
        val snapshots = mutableListOf<List<PaletteSourceGroup>>()
        search.run(" ") { snapshots += it }
        search.run("a") { snapshots += it }
        calls shouldBe 0
        snapshots.last() shouldBe emptyList()
    }

    private fun manga(title: String) = SManga(url = "/$title", title = title)
}
