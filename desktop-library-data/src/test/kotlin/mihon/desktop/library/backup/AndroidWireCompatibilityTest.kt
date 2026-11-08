package mihon.desktop.library.backup

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Cross-checks the desktop backup codec against the **Android** field numbering using
 * [AndroidWireFormat], which encodes the same payload without any shared annotation.
 *
 * `AndroidBackupRoundTripTest` encodes and decodes with the same desktop serializer and so cannot
 * notice a `@ProtoNumber` that no longer matches the Android model.
 */
class AndroidWireCompatibilityTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `desktop reads an Android-encoded backup through the Android wire layout`() {
        val wire = WireBackup(
            mangas = listOf(androidManga()),
            categories = listOf(WireCategory(name = "Reading", order = 3, id = 77, flags = 5)),
            sources = listOf(WireSource(name = "Android Source", sourceId = 42)),
            preferences = listOf(
                WirePreference(
                    key = "pref_display_mode_library",
                    typeUrl = AndroidWireFormat.STRING_PREFERENCE_TYPE,
                    valueBytes = AndroidWireFormat.stringPreferenceValue("COMPACT_GRID"),
                ),
            ),
        )
        val file = writeGzip("android-wire.tachibk", AndroidWireFormat.encode(wire))

        DesktopLibraryDatabaseFactory.open(tempDir.resolve("import.db")).use { repository ->
            val report = importer(repository).import(file, nowMillis = 5_000)

            report.counts.mangaInserted shouldBe 1L
            report.counts.chaptersInserted shouldBe 2L

            val manga = repository.findManga(42, "/android/manga")!!
            manga.title shouldBe "Android 标题"
            manga.author shouldBe "作者"
            manga.artist shouldBe "画师"
            manga.description shouldBe "简介"
            manga.genreJson shouldBe """["Action","Comedy"]"""
            manga.status shouldBe 2L
            manga.thumbnailUrl shouldBe "https://example.invalid/cover.jpg"
            manga.favorite shouldBe false
            manga.lastModifiedAt shouldBe 1_700_000_000_000
            manga.favoriteModifiedAt shouldBe 1_700_000_000_500
            manga.version shouldBe 4
            manga.notes shouldBe "备注"
            manga.initialized shouldBe true
            manga.dateAdded shouldBe 1_600_000_000_000

            val chapters = repository.chapterSnapshot(manga.id)
            chapters.size shouldBe 2
            val read = chapters.single { it.url == "/android/chapter/1" }
            read.name shouldBe "第 1 话"
            read.scanlator shouldBe "汉化组"
            read.read shouldBe true
            read.bookmark shouldBe true
            read.lastPageRead shouldBe 9
            read.chapterNumber shouldBe 1.5
            read.sourceOrder shouldBe 0
            read.lastModifiedAt shouldBe 1_700_000_000_100

            repository.historySnapshot().single().lastRead shouldBe 1_700_000_000_200
            repository.mangaSnapshot(manga.id)!!.categories.single().name shouldBe "Reading"
            repository.allPreferenceSnapshots().single().let {
                it.key shouldBe "pref_display_mode_library"
                it.valueType shouldBe "STRING"
            }
        }
    }

    @Test
    fun `desktop export is readable through the Android wire layout`() {
        DesktopLibraryDatabaseFactory.open(tempDir.resolve("export.db")).use { repository ->
            val mangaId = repository.insertManga(
                MangaRecord(
                    sourceId = 42,
                    url = "/desktop/manga",
                    title = "桌面标题",
                    artist = "画师",
                    author = "作者",
                    description = "简介",
                    genreJson = """["Action"]""",
                    status = 2,
                    thumbnailUrl = "https://example.invalid/desktop.jpg",
                    favorite = true,
                    dateAdded = 1_500,
                    lastModifiedAt = 2_000,
                    favoriteModifiedAt = 2_100,
                    notes = "备注",
                    initialized = true,
                ),
            )
            repository.insertChapter(
                mihon.desktop.library.model.ChapterRecord(
                    mangaId = mangaId,
                    url = "/desktop/chapter/1",
                    name = "第 1 话",
                    scanlator = "汉化组",
                    read = true,
                    bookmark = false,
                    lastPageRead = 12,
                    chapterNumber = 2.5,
                    sourceOrder = 1,
                    lastModifiedAt = 2_050,
                ),
            )
            val categoryId = repository.upsertCategory(
                mihon.desktop.library.model.CategoryRecord(name = "Favourites", flags = 1),
            )
            repository.linkCategory(mangaId, categoryId)
            val backupFile = tempDir.resolve("desktop-export.tachibk")
            AndroidBackupExporter(repository).export(backupFile)

            val decoded = AndroidWireFormat.decode(ungzip(backupFile))

            decoded.mangas.size shouldBe 1
            val manga = decoded.mangas.single()
            manga.source shouldBe 42L
            manga.url shouldBe "/desktop/manga"
            manga.title shouldBe "桌面标题"
            manga.author shouldBe "作者"
            manga.artist shouldBe "画师"
            manga.description shouldBe "简介"
            manga.genre shouldBe listOf("Action")
            manga.status shouldBe 2
            manga.thumbnailUrl shouldBe "https://example.invalid/desktop.jpg"
            manga.favorite shouldBe true
            manga.dateAdded shouldBe 1_500L
            manga.lastModifiedAt shouldBe 2_000L
            manga.favoriteModifiedAt shouldBe 2_100L
            manga.notes shouldBe "备注"
            manga.initialized shouldBe true
            // Android's UpdateStrategy ordinal: 0 = ALWAYS_UPDATE.
            manga.updateStrategy shouldBe 0

            val chapter = manga.chapters.single()
            chapter.url shouldBe "/desktop/chapter/1"
            chapter.name shouldBe "第 1 话"
            chapter.scanlator shouldBe "汉化组"
            chapter.read shouldBe true
            chapter.bookmark shouldBe false
            chapter.lastPageRead shouldBe 12L
            chapter.chapterNumber shouldBe 2.5f
            chapter.sourceOrder shouldBe 1L
            chapter.lastModifiedAt shouldBe 2_050L

            decoded.categories.single().apply {
                name shouldBe "Favourites"
                flags shouldBe 1L
            }
            manga.categoryOrder shouldBe listOf(decoded.categories.single().order)
        }
    }

    private fun importer(repository: mihon.desktop.library.db.SqlDelightLibraryRepository) = AndroidBackupImporter(
        codec = AndroidBackupCodec(),
        validator = AndroidBackupValidator(),
        mutations = repository,
    )

    private fun writeGzip(name: String, bytes: ByteArray): Path {
        val file = tempDir.resolve(name)
        Files.newOutputStream(file).use { out ->
            GZIPOutputStream(out).use { gzip -> gzip.write(bytes) }
        }
        return file
    }

    private fun ungzip(file: Path): ByteArray = java.util.zip.GZIPInputStream(
        Files.newInputStream(file),
    ).use { it.readBytes() }

    private fun androidManga() = WireManga(
        source = 42,
        url = "/android/manga",
        title = "Android 标题",
        artist = "画师",
        author = "作者",
        description = "简介",
        genre = listOf("Action", "Comedy"),
        status = 2,
        thumbnailUrl = "https://example.invalid/cover.jpg",
        dateAdded = 1_600_000_000_000,
        chapters = listOf(
            WireChapter(
                url = "/android/chapter/1",
                name = "第 1 话",
                scanlator = "汉化组",
                read = true,
                bookmark = true,
                lastPageRead = 9,
                chapterNumber = 1.5f,
                sourceOrder = 0,
                lastModifiedAt = 1_700_000_000_100,
                memo = "{}",
            ),
            WireChapter(
                url = "/android/chapter/2",
                name = "第 2 话",
                scanlator = null,
                read = false,
                bookmark = false,
                lastPageRead = 0,
                chapterNumber = 2f,
                sourceOrder = 1,
                lastModifiedAt = 1_700_000_000_150,
                memo = "{}",
            ),
        ),
        categoryOrder = listOf(3),
        favorite = false,
        historyLastRead = mapOf("/android/chapter/1" to 1_700_000_000_200),
        updateStrategy = 0,
        lastModifiedAt = 1_700_000_000_000,
        favoriteModifiedAt = 1_700_000_000_500,
        version = 4,
        notes = "备注",
        initialized = true,
        memo = """{"sync_device_id":"android"}""",
    )

    @Test
    fun `the wire fixture itself round trips through its own codec`() {
        // Guards the fixture: a broken hand-written codec would make the compatibility tests
        // vacuous, so encode/decode of the same structure must be lossless first.
        val wire = WireBackup(mangas = listOf(androidManga()))
        val decoded = AndroidWireFormat.decode(AndroidWireFormat.encode(wire))
        decoded.mangas.single() shouldBe wire.mangas.single()
        val kotlinx = ProtoBuf.decodeFromByteArray(AndroidBackup.serializer(), AndroidWireFormat.encode(wire))
        kotlinx.backupManga.single().title shouldBe "Android 标题"
        kotlinx.backupManga.single().chapters.first().memo shouldNotBe null
    }
}
