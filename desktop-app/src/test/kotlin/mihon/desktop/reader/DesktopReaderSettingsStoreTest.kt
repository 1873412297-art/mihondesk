package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.layout.DualPageSplit
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
    fun `keep screen on defaults to on and round trips`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))

        store.load().keepScreenOn shouldBe true

        store.save(DesktopReaderSettings(keepScreenOn = false))
        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().keepScreenOn shouldBe false

        Files.writeString(file, "reader.v1.keep-screen-on=not-a-boolean")
        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().keepScreenOn shouldBe true
    }

    @Test
    fun `phase 6_2 page transition settings default and round trip`() {
        val file = tempDir.resolve("phase62.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))

        val defaults = store.load()
        defaults.pageTransition shouldBe ReaderPageTransition.NONE

        val custom = DesktopReaderSettings(pageTransition = ReaderPageTransition.SLIDE)
        store.save(custom)

        val reloaded = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        reloaded.pageTransition shouldBe ReaderPageTransition.SLIDE

        val text = Files.readString(file)
        text.contains("reader.v1.page-transition=SLIDE") shouldBe true

        store.save(DesktopReaderSettings(pageTransition = ReaderPageTransition.FADE))
        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().pageTransition shouldBe
            ReaderPageTransition.FADE

        Files.writeString(file, "reader.v1.page-transition=corrupt")
        DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load().pageTransition shouldBe
            ReaderPageTransition.NONE
    }

    @Test
    fun `phase 6_4 preference toggles default and round trip`() {
        val file = tempDir.resolve("phase64.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))

        val defaults = store.load()
        defaults.pageFlash shouldBe false
        defaults.webtoonPreventDownsizing shouldBe false
        defaults.webtoonDoubleTapZoom shouldBe true

        val custom = DesktopReaderSettings(
            pageFlash = true,
            webtoonPreventDownsizing = true,
            webtoonDoubleTapZoom = false,
        )
        store.save(custom)

        val reloaded = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        reloaded.pageFlash shouldBe true
        reloaded.webtoonPreventDownsizing shouldBe true
        reloaded.webtoonDoubleTapZoom shouldBe false

        val text = Files.readString(file)
        text.contains("reader.v1.page-flash=true") shouldBe true
        text.contains("reader.v1.webtoon-prevent-downsizing=true") shouldBe true
        text.contains("reader.v1.webtoon-double-tap-zoom=false") shouldBe true

        Files.writeString(
            file,
            """
            reader.v1.page-flash=corrupt
            reader.v1.webtoon-prevent-downsizing=invalid
            reader.v1.webtoon-double-tap-zoom=bad
            """.trimIndent(),
        )
        val fallback = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        fallback.pageFlash shouldBe false
        fallback.webtoonPreventDownsizing shouldBe false
        fallback.webtoonDoubleTapZoom shouldBe true
    }

    @Test
    fun `phase 6_3 custom filter and dimming settings default and round trip`() {
        val file = tempDir.resolve("phase63.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))

        val defaults = store.load()
        defaults.customHue shouldBe 0
        defaults.customBrightness shouldBe 0
        defaults.customContrast shouldBe 0
        defaults.dimmingPercent shouldBe 100

        val custom = DesktopReaderSettings(
            colorFilter = ReaderColorFilter.CUSTOM,
            customHue = 180,
            customBrightness = 40,
            customContrast = -30,
            dimmingPercent = 60,
        )
        store.save(custom)

        val reloaded = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        reloaded.colorFilter shouldBe ReaderColorFilter.CUSTOM
        reloaded.customHue shouldBe 180
        reloaded.customBrightness shouldBe 40
        reloaded.customContrast shouldBe -30
        reloaded.dimmingPercent shouldBe 60

        val text = Files.readString(file)
        text.contains("reader.v1.color-filter=CUSTOM") shouldBe true
        text.contains("reader.v1.custom-hue=180") shouldBe true
        text.contains("reader.v1.custom-brightness=40") shouldBe true
        text.contains("reader.v1.custom-contrast=-30") shouldBe true
        text.contains("reader.v1.dimming-percent=60") shouldBe true

        Files.writeString(
            file,
            """
            reader.v1.custom-hue=500
            reader.v1.custom-brightness=-200
            reader.v1.custom-contrast=150
            reader.v1.dimming-percent=10
            """.trimIndent(),
        )
        val clamped = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        clamped.customHue shouldBe 360
        clamped.customBrightness shouldBe -100
        clamped.customContrast shouldBe 100
        clamped.dimmingPercent shouldBe 20

        Files.writeString(
            file,
            """
            reader.v1.custom-hue=invalid
            reader.v1.custom-brightness=bad
            reader.v1.custom-contrast=corrupt
            reader.v1.dimming-percent=not-a-number
            """.trimIndent(),
        )
        val fallback = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        fallback.customHue shouldBe 0
        fallback.customBrightness shouldBe 0
        fallback.customContrast shouldBe 0
        fallback.dimmingPercent shouldBe 100
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

    @Test
    fun `dualPageSplit and dualPageRotateToFit settings default and round trip`() {
        val file = tempDir.resolve("split.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(file))

        val defaults = store.load()
        defaults.dualPageSplit shouldBe DualPageSplit.WIDE
        defaults.dualPageRotateToFit shouldBe false

        val custom = DesktopReaderSettings(
            dualPageSplit = DualPageSplit.ALWAYS,
            dualPageRotateToFit = true,
        )
        store.save(custom)

        val reloaded = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        reloaded.dualPageSplit shouldBe DualPageSplit.ALWAYS
        reloaded.dualPageRotateToFit shouldBe true
        Files.readString(file).contains("reader.v1.dual-page-split=ALWAYS") shouldBe true
        Files.readString(file).contains("reader.v1.dual-page-rotate-to-fit=true") shouldBe true

        Files.writeString(file, "reader.v1.dual-page-split=INVALID")
        val fallback = DesktopReaderSettingsStore(DesktopPreferenceStore(file)).load()
        fallback.dualPageSplit shouldBe DualPageSplit.WIDE
    }
}
