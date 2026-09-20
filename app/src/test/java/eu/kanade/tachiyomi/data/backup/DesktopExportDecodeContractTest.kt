package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reverse-direction contract: a backup produced by the Windows desktop exporter must
 * decode with the real Android [Backup] protobuf decoder and preserve every
 * transferable field written by [DesktopBackupFixtureWriterTest]'s fixture.
 *
 * Enabled only when the fixture path is provided:
 * `./gradlew :app:testDebugUnitTest --tests "*DesktopExportDecodeContractTest*" \
 *   -PmihonPlan2DesktopExport=path/to/desktop-export.tachibk`
 */
@OptIn(ExperimentalSerializationApi::class)
class DesktopExportDecodeContractTest {
    @Test
    fun `desktop export decodes through the real Android backup decoder`() {
        val configured = System.getProperty(DESKTOP_EXPORT_PROPERTY)?.takeIf(String::isNotBlank)
        assumeTrue(configured != null, "Desktop export decode contract is disabled during normal unit-test runs")
        val export = Path.of(checkNotNull(configured)).toAbsolutePath().normalize()
        // Explicitly configured but missing: that is a configuration error and must
        // fail the build, not silently skip (assumeTrue would report SKIPPED).
        require(Files.isRegularFile(export)) { "Desktop export fixture missing at $export" }

        val backup = export.source().gzip().buffer().use { source ->
            ProtoBuf.decodeFromByteArray(Backup.serializer(), source.readByteArray())
        }

        // Library / manga identity and metadata.
        backup.backupManga.size shouldBe 1
        val manga = backup.backupManga.single()
        manga.source shouldBe 42L
        manga.url shouldBe "/cross-platform"
        manga.title shouldBe "跨平台备份"
        manga.author shouldBe "Windows 迁移验证"
        manga.categories shouldBe listOf(7L)

        // Chapters: read state and page progress survive the round trip.
        manga.chapters.size shouldBe 1
        val chapter = manga.chapters.single()
        chapter.url shouldBe "/cross-platform/chapter-1"
        chapter.name shouldBe "第 1 话"
        chapter.read shouldBe true
        chapter.lastPageRead shouldBe 7
        chapter.chapterNumber shouldBe 1F

        // History.
        manga.history.size shouldBe 1
        manga.history.single().url shouldBe "/cross-platform/chapter-1"
        manga.history.single().lastRead shouldBe 1_700_000_000_000L
        manga.history.single().readDuration shouldBe 90L

        // Tracking.
        manga.tracking.size shouldBe 1
        val tracking = manga.tracking.single()
        tracking.syncId shouldBe 1
        tracking.libraryId shouldBe 2L
        tracking.mediaId shouldBe 420L
        tracking.lastChapterRead shouldBe 1F

        // Categories with order.
        backup.backupCategories.size shouldBe 1
        backup.backupCategories.single().name shouldBe "Android 收藏"
        backup.backupCategories.single().order shouldBe 7L

        // Sources.
        backup.backupSources.size shouldBe 1
        backup.backupSources.single().name shouldBe "Android Fixture Source"
        backup.backupSources.single().sourceId shouldBe 42L

        // Preferences: desktop exporter must emit transferable app preferences and
        // must NOT resurrect private/app-state/unrecognized keys skipped at import.
        val preferences = backup.backupPreferences.associate { it.key to it.value }
        preferences["pref_display_mode_library"].shouldNotBe(null)
        preferences.keys.none { it.startsWith("__PRIVATE_") } shouldBe true
        preferences.keys.none { it.startsWith("__APP_STATE_") } shouldBe true
        preferences.keys.contains("unrecognized_plan2_key") shouldBe false
    }

    private companion object {
        const val DESKTOP_EXPORT_PROPERTY = "mihon.plan2.desktopExport"
    }
}
