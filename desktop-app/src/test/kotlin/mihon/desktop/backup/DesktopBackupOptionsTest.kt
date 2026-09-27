@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.desktop.backup

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.sync.core.model.AndroidBackup
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupPreference
import mihon.sync.core.model.AndroidBackupSource
import mihon.sync.core.model.AndroidBackupSourcePreferences
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.AndroidStringPreferenceValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopBackupOptionsTest {

    private fun sampleBackup(): AndroidBackup {
        return AndroidBackup(
            backupManga = listOf(
                AndroidBackupManga(
                    source = 100L,
                    url = "/manga/one",
                    title = "Sample Manga",
                    categories = listOf(1L),
                    tracking = listOf(
                        AndroidBackupTracking(
                            syncId = 1,
                            libraryId = 10L,
                            title = "Tracker 1",
                        ),
                    ),
                    history = listOf(
                        AndroidBackupHistory(
                            url = "/chapter/1",
                            lastRead = 12345L,
                        ),
                    ),
                    chapters = listOf(
                        AndroidBackupChapter(
                            url = "/chapter/1",
                            name = "Chapter 1",
                            read = true,
                            bookmark = true,
                            lastPageRead = 15L,
                        ),
                    ),
                ),
            ),
            backupCategories = listOf(
                AndroidBackupCategory(
                    name = "Favorites",
                    order = 1L,
                    id = 1L,
                ),
            ),
            backupSources = listOf(
                AndroidBackupSource(name = "Source 1", sourceId = 100L),
            ),
            backupPreferences = listOf(
                AndroidBackupPreference(key = "theme", value = AndroidStringPreferenceValue("Dark")),
            ),
            backupSourcePreferences = listOf(
                AndroidBackupSourcePreferences(
                    sourceKey = "src1",
                    prefs = listOf(
                        AndroidBackupPreference(key = "lang", value = AndroidStringPreferenceValue("en")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `default options keep all components`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions()
        val filtered = backup.filterByOptions(options)

        filtered.backupCategories shouldHaveSize 1
        filtered.backupPreferences shouldHaveSize 1
        filtered.backupSourcePreferences shouldHaveSize 1
        val manga = filtered.backupManga.single()
        manga.categories shouldHaveSize 1
        manga.tracking shouldHaveSize 1
        manga.history shouldHaveSize 1
        val chapter = manga.chapters.single()
        chapter.read shouldBe true
        chapter.bookmark shouldBe true
        chapter.lastPageRead shouldBe 15L
    }

    @Test
    fun `disabling categories removes backupCategories and manga category links`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(categories = false)
        val filtered = backup.filterByOptions(options)

        filtered.backupCategories.shouldBeEmpty()
        filtered.backupManga.single().categories.shouldBeEmpty()
    }

    @Test
    fun `disabling tracking removes tracking entries`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(tracking = false)
        val filtered = backup.filterByOptions(options)

        filtered.backupManga.single().tracking.shouldBeEmpty()
    }

    @Test
    fun `disabling history removes history entries`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(history = false)
        val filtered = backup.filterByOptions(options)

        filtered.backupManga.single().history.shouldBeEmpty()
    }

    @Test
    fun `disabling chapterState resets read and bookmark`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(chapterState = false)
        val filtered = backup.filterByOptions(options)

        val ch = filtered.backupManga.single().chapters.single()
        ch.read shouldBe false
        ch.bookmark shouldBe false
        ch.lastPageRead shouldBe 15L // preserved
    }

    @Test
    fun `disabling readProgress resets lastPageRead`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(readProgress = false)
        val filtered = backup.filterByOptions(options)

        val ch = filtered.backupManga.single().chapters.single()
        ch.read shouldBe true // preserved
        ch.bookmark shouldBe true // preserved
        ch.lastPageRead shouldBe 0L
    }

    @Test
    fun `disabling settings removes app and source preferences`() {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(settings = false)
        val filtered = backup.filterByOptions(options)

        filtered.backupPreferences.shouldBeEmpty()
        filtered.backupSourcePreferences.shouldBeEmpty()
    }

    @Test
    fun `filtered backup passes validator and codec roundtrip`(@TempDir tempDir: Path) {
        val backup = sampleBackup()
        val options = DesktopBackupOptions(
            chapterState = false,
            categories = false,
            tracking = false,
            history = false,
            readProgress = false,
            settings = false,
        )
        val filtered = backup.filterByOptions(options)

        val codec = AndroidBackupCodec()
        val validator = AndroidBackupValidator()

        val filePath = tempDir.resolve("test.tachibk")
        codec.encode(filtered, filePath)

        val decoded = codec.decode(filePath)
        val validated = validator.validate(decoded)

        validated.backup.backupManga shouldHaveSize 1
        val ch = validated.backup.backupManga.single().chapters.single()
        ch.read shouldBe false
        ch.bookmark shouldBe false
        ch.lastPageRead shouldBe 0L
        validated.backup.backupCategories.shouldBeEmpty()
        validated.backup.backupPreferences.shouldBeEmpty()
        validated.backup.backupSourcePreferences.shouldBeEmpty()
    }
}
