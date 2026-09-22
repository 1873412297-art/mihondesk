package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.model.MangaRecord
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test

class MissingSourceResolverTest {

    private fun manga(id: Long, sourceId: Long, title: String = "Manga $id") = MangaRecord(
        id = id,
        sourceId = sourceId,
        url = "/manga/$id",
        title = title,
    )

    private fun storeItem(pkg: String, name: String, vararg sources: SourceDescriptor) = ExtensionStoreItem(
        pkg = pkg,
        name = name,
        version = "1.0",
        versionCode = 1,
        sources = sources.toList(),
    )

    @Test
    fun `findMissingSources matches single extension`() {
        val mangas = listOf(manga(1, 100L))
        val ext = storeItem("ext.test", "Test Ext", SourceDescriptor(100L, "Test Source", "en", "cls"))
        val available = listOf(ext)
        val installed = emptySet<Long>()

        val result = MissingSourceResolver.findMissingSources(mangas, installed, available)

        result.size shouldBe 1
        result[0].sourceId shouldBe 100L
        result[0].mangaCount shouldBe 1
        result[0].extension shouldBe ext
    }

    @Test
    fun `findMissingSources resolves multiple sources from same extension`() {
        val mangas = listOf(manga(1, 100L), manga(2, 200L))
        val ext = storeItem(
            "ext.multi",
            "Multi Source Ext",
            SourceDescriptor(100L, "Source 100", "en", "cls"),
            SourceDescriptor(200L, "Source 200", "ja", "cls"),
        )
        val available = listOf(ext)
        val installed = emptySet<Long>()

        val result = MissingSourceResolver.findMissingSources(mangas, installed, available)

        result.size shouldBe 2
        val r100 = result.find { it.sourceId == 100L }!!
        val r200 = result.find { it.sourceId == 200L }!!
        r100.extension shouldBe ext
        r200.extension shouldBe ext
    }

    @Test
    fun `findMissingSources marks extension as null when not found in repository`() {
        val mangas = listOf(manga(1, 999L))
        val ext = storeItem("ext.other", "Other Ext", SourceDescriptor(100L, "Source 100", "en", "cls"))
        val available = listOf(ext)
        val installed = emptySet<Long>()

        val result = MissingSourceResolver.findMissingSources(mangas, installed, available)

        result.size shouldBe 1
        result[0].sourceId shouldBe 999L
        result[0].mangaCount shouldBe 1
        result[0].extension shouldBe null
    }

    @Test
    fun `findMissingSources skips local source with sourceId 0`() {
        val mangas = listOf(manga(1, 0L))
        val result = MissingSourceResolver.findMissingSources(mangas, emptySet(), emptyList())
        result shouldBe emptyList()
    }

    @Test
    fun `findMissingSources skips already installed sources`() {
        val mangas = listOf(manga(1, 100L))
        val ext = storeItem("ext.test", "Test Ext", SourceDescriptor(100L, "Test Source", "en", "cls"))
        val result = MissingSourceResolver.findMissingSources(mangas, setOf(100L), listOf(ext))
        result shouldBe emptyList()
    }

    @Test
    fun `findMissingSources aggregates count across multiple manga`() {
        val mangas = listOf(
            manga(1, 100L),
            manga(2, 100L),
            manga(3, 100L),
            manga(4, 200L),
        )
        val ext1 = storeItem("ext.one", "Ext One", SourceDescriptor(100L, "Source 100", "en", "cls"))
        val ext2 = storeItem("ext.two", "Ext Two", SourceDescriptor(200L, "Source 200", "en", "cls"))

        val result = MissingSourceResolver.findMissingSources(mangas, emptySet(), listOf(ext1, ext2))

        result.size shouldBe 2
        result.find { it.sourceId == 100L }?.mangaCount shouldBe 3
        result.find { it.sourceId == 200L }?.mangaCount shouldBe 1
    }

    @Test
    fun `ensureAvailableExtensions returns cached items without network call`() = runBlocking {
        val cached = listOf(storeItem("ext.cached", "Cached Ext"))
        val result = MissingSourceResolver.ensureAvailableExtensions(null, cached)
        result shouldBe cached
    }

    @Test
    fun `ensureAvailableExtensions handles null store service gracefully`() = runBlocking {
        val result = MissingSourceResolver.ensureAvailableExtensions(null, emptyList())
        result shouldBe emptyList()
    }
}
