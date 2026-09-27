package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopReaderSettingsStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing preferences return reader defaults`() {
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("preferences.properties")))

        store.load() shouldBe DesktopReaderSettings()
    }

    @Test
    fun `reader settings round trip through versioned keys`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        val expected = DesktopReaderSettings(
            mode = ReadingMode.DUAL_RTL,
            coverOffset = true,
            scaleMode = ScaleMode.FIT_HEIGHT,
            clickRegions = ClickRegions(
                leftAction = ReaderClickAction.NEXT,
                centerAction = ReaderClickAction.NONE,
                rightAction = ReaderClickAction.PREVIOUS,
                leftEndPercent = 30,
                centerEndPercent = 70,
            ),
            wheelBehavior = ReaderWheelBehavior.SCROLL,
            lastWindowMode = ReaderWindowMode.FULLSCREEN,
            colorFilter = ReaderColorFilter.SEPIA,
            backgroundColor = ReaderBackgroundColor.WARM_CREAM,
            cropBorders = true,
            cropBordersWebtoon = true,
            webtoonMaxWidth = 1000,
            webtoonSidePadding = 15,
            preloadPages = 8,
        )

        store.save(expected)

        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load() shouldBe expected
        Files.readString(file).contains("reader.v1.mode=DUAL_RTL") shouldBe true
        Files.readString(file).contains("reader.v1.color-filter=SEPIA") shouldBe true
        Files.readString(file).contains("reader.v1.background-color=WARM_CREAM") shouldBe true
        Files.readString(file).contains("reader.v1.crop-borders=true") shouldBe true
        Files.readString(file).contains("reader.v1.crop-borders-webtoon=true") shouldBe true
        Files.readString(file).contains("reader.v1.webtoon-max-width=1000") shouldBe true
        Files.readString(file).contains("reader.v1.webtoon-side-padding=15") shouldBe true
        Files.readString(file).contains("reader.v1.preload-pages=8") shouldBe true
    }

    @Test
    fun `preload pages clamp into the supported range`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(file, "reader.v1.preload-pages=42")

        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().preloadPages shouldBe 10

        Files.writeString(file, "reader.v1.preload-pages=0")
        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().preloadPages shouldBe 1
    }

    @Test
    fun `corrupt and unknown reader values fall back independently`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(
            file,
            """
            reader.v1.mode=UNKNOWN
            reader.v1.cover-offset=not-a-boolean
            reader.v1.scale=FIT_HEIGHT
            reader.v1.click.left-action=INVALID
            reader.v1.click.center-action=NONE
            reader.v1.click.right-action=PREVIOUS
            reader.v1.click.left-end=0
            reader.v1.click.center-end=70
            reader.v1.wheel=SCROLL
            reader.v1.window=NOT_A_WINDOW
            theme=Dark
            """.trimIndent(),
        )

        val settings = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()

        settings.mode shouldBe DesktopReaderSettings().mode
        settings.coverOffset shouldBe DesktopReaderSettings().coverOffset
        settings.scaleMode shouldBe ScaleMode.FIT_HEIGHT
        settings.clickRegions shouldBe DesktopReaderSettings().clickRegions
        settings.wheelBehavior shouldBe ReaderWheelBehavior.SCROLL
        settings.lastWindowMode shouldBe DesktopReaderSettings().lastWindowMode
        DesktopPreferenceStore(file).load().themeMode.name shouldBe "Dark"
    }

    @Test
    fun `withOverride overrides mode and preload pages when provided`() {
        val base = DesktopReaderSettings(mode = ReadingMode.SINGLE_LTR, preloadPages = 4)
        val override = MangaReaderSettingsOverride(readingMode = ReadingMode.WEBTOON, preloadPages = 8)

        val result = base.withOverride(override)
        result.mode shouldBe ReadingMode.WEBTOON
        result.preloadPages shouldBe 8
    }

    @Test
    fun `withOverride preserves base values when override fields are null`() {
        val base = DesktopReaderSettings(mode = ReadingMode.DUAL_LTR, preloadPages = 6)
        val override = MangaReaderSettingsOverride(readingMode = null, preloadPages = null)

        val result = base.withOverride(override)
        result.mode shouldBe ReadingMode.DUAL_LTR
        result.preloadPages shouldBe 6

        base.withOverride(null) shouldBe base
    }

    @Test
    fun `loadEffective applies override with priority over store settings`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        store.save(DesktopReaderSettings(mode = ReadingMode.SINGLE_LTR, preloadPages = 3))

        val override = MangaReaderSettingsOverride(readingMode = ReadingMode.VERTICAL, preloadPages = 9)
        val effective = store.loadEffective(override)

        effective.mode shouldBe ReadingMode.VERTICAL
        effective.preloadPages shouldBe 9
        // Global store remains unchanged
        store.load().mode shouldBe ReadingMode.SINGLE_LTR
        store.load().preloadPages shouldBe 3
    }

    @Test
    fun `loadEffective with null override returns global settings`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))
        store.save(DesktopReaderSettings(mode = ReadingMode.SINGLE_RTL, preloadPages = 5))

        val effective = store.loadEffective(null)
        effective.mode shouldBe ReadingMode.SINGLE_RTL
        effective.preloadPages shouldBe 5
    }
}
