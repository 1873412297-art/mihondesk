package mihon.desktop.ui.library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mihon.desktop.library.model.MangaReaderSettings
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.readerSettingsOverride
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.model.ReadingMode
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MangaReaderSettingsOverridePresenterTest {

    private fun mangaRecord(id: Long, title: String = "Test Manga", sourceId: Long = 100L) = MangaRecord(
        id = id,
        sourceId = sourceId,
        url = "/$id",
        title = title,
        favorite = true,
    )

    @Test
    fun `save and clear reading settings override updates repository and state`() = runTest {
        val repository = TestLibraryRepository()
        repository.addManga(mangaRecord(1, "One Piece"))

        val preferenceFile = Files.createTempFile("reader-settings-override", ".properties")
        val preferences = DesktopPreferenceStore(preferenceFile)
        val presenter = LibraryPresenter(
            repository,
            backgroundScope,
            preferences = preferences,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        settledState(presenter) { !it.loading && it.items.isNotEmpty() }
        presenter.selectManga(1)
        val initialDetail = settledDetail(presenter) { it.manga?.id == 1L }
        initialDetail.readingSettingsOverride shouldBe null
        initialDetail.isReadingSettingsDialogOpen shouldBe false

        // Toggle dialog state
        presenter.setReadingSettingsDialogOpen(true)
        settledDetail(presenter) { it.isReadingSettingsDialogOpen }.isReadingSettingsDialogOpen shouldBe true

        presenter.setReadingSettingsDialogOpen(false)
        settledDetail(presenter) { !it.isReadingSettingsDialogOpen }.isReadingSettingsDialogOpen shouldBe false

        // Save override
        val override = MangaReaderSettingsOverride(
            readingMode = ReadingMode.WEBTOON,
            preloadPages = 8,
        )
        presenter.saveReadingSettingsOverride(override)

        val updatedDetail = settledDetail(presenter) {
            it.readingSettingsOverride?.readingMode == ReadingMode.WEBTOON &&
                it.readingSettingsOverride.preloadPages == 8
        }
        updatedDetail.readingSettingsOverride shouldBe override

        // Verify repository snapshot
        val snapshot = repository.mangaSnapshot(1) ?: error("Manga not found")
        val decodedOverride = snapshot.readerSettingsOverride
        decodedOverride shouldNotBe null
        decodedOverride!!.readingMode shouldBe ReadingMode.WEBTOON
        decodedOverride.preloadPages shouldBe 8

        // Verify chapter settings are intact and default
        updatedDetail.chapterSettings.displayMode shouldBe ChapterDisplayMode.Name
        updatedDetail.chapterSettings.showMissingChapters shouldBe true

        // Clear override
        presenter.clearReadingSettingsOverride()
        val clearedDetail = settledDetail(presenter) { it.readingSettingsOverride == null }
        clearedDetail.readingSettingsOverride shouldBe null

        val clearedSnapshot = repository.mangaSnapshot(1) ?: error("Manga not found")
        clearedSnapshot.readerSettingsOverride shouldBe null
        MangaReaderSettings.readingModeFromViewerFlags(clearedSnapshot.viewerFlags) shouldBe null

        presenter.close()
    }
}
