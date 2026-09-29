package mihon.desktop.reader

import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.prefetch.PrefetchPolicy
import mihon.reader.session.ReaderSettings

enum class ReaderColorFilter {
    NONE,
    INVERT,
    GRAYSCALE,
    INVERT_GRAYSCALE,
    SEPIA,
    NIGHT,
    CUSTOM,
}

enum class ReaderBackgroundColor {
    DARK_GRAY,
    BLACK,
    WHITE,
    WARM_CREAM,
}

enum class ReaderPageTransition {
    NONE,
    FADE,
    SLIDE,
}

/** Versioned desktop-only reader preferences, kept separate from the portable reader-core API. */
data class DesktopReaderSettings(
    val mode: ReadingMode = ReadingMode.SINGLE_LTR,
    val coverOffset: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FIT_WIDTH,
    val clickRegions: ClickRegions = ClickRegions(),
    val wheelBehavior: ReaderWheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION,
    val lastWindowMode: ReaderWindowMode = ReaderWindowMode.NORMAL,
    val colorFilter: ReaderColorFilter = ReaderColorFilter.NONE,
    val backgroundColor: ReaderBackgroundColor = ReaderBackgroundColor.DARK_GRAY,
    val cropBorders: Boolean = false,
    val cropBordersWebtoon: Boolean = false,
    val webtoonMaxWidth: Int = 800,
    val webtoonSidePadding: Int = 0,
    val alwaysShowChapterTransition: Boolean = true,
    val skipReadChapters: Boolean = false,
    val skipFilteredChapters: Boolean = true,
    val skipDuplicateChapters: Boolean = false,
    val preloadPages: Int = PrefetchPolicy.AHEAD_PAGES,
    val keepScreenOn: Boolean = true,
    val pageFlash: Boolean = false,
    val webtoonPreventDownsizing: Boolean = false,
    val webtoonDoubleTapZoom: Boolean = true,
    val customHue: Int = 0,
    val customBrightness: Int = 0,
    val customContrast: Int = 0,
    val dimmingPercent: Int = 100,
    val pageTransition: ReaderPageTransition = ReaderPageTransition.NONE,
    val dualPageSplit: mihon.reader.layout.DualPageSplit = mihon.reader.layout.DualPageSplit.WIDE,
    val dualPageRotateToFit: Boolean = false,
) {
    fun toCoreSettings(): ReaderSettings = ReaderSettings(
        mode = mode,
        coverOffset = coverOffset,
        scaleMode = scaleMode,
        dualPageSplit = dualPageSplit,
        dualPageRotateToFit = dualPageRotateToFit,
    )

    fun withOverride(override: MangaReaderSettingsOverride?): DesktopReaderSettings {
        if (override == null || override.isEmpty) return this
        return copy(
            mode = override.readingMode ?: mode,
            preloadPages = override.preloadPages ?: preloadPages,
        )
    }
}

/**
 * Persistence edge for the chapter bookmark toggle shown in the reader chrome.
 *
 * The reader screen accepts any implementation so the application can back it with the library
 * repository. [DesktopReaderSettingsStore.bookmarkStore] provides a desktop-local fallback.
 */
interface ReaderChapterBookmarkStore {
    fun isBookmarked(chapterId: Long): Boolean

    fun setBookmarked(chapterId: Long, bookmarked: Boolean)
}

data class ClickRegions(
    val leftAction: ReaderClickAction = ReaderClickAction.PREVIOUS,
    val centerAction: ReaderClickAction = ReaderClickAction.TOGGLE_CHROME,
    val rightAction: ReaderClickAction = ReaderClickAction.NEXT,
    val leftEndPercent: Int = 25,
    val centerEndPercent: Int = 75,
) {
    init {
        require(leftEndPercent in 1 until centerEndPercent) { "left click region must precede the center region" }
        require(centerEndPercent in 2..99) { "center click region must end before 100" }
    }
}

enum class ReaderClickAction { PREVIOUS, TOGGLE_CHROME, NEXT, NONE }

enum class ReaderWheelBehavior { PAGE_NAVIGATION, SCROLL }

enum class ReaderWindowMode { NORMAL, FULLSCREEN, BORDERLESS }

class DesktopReaderSettingsStore(private val preferences: DesktopPreferenceStore) {

    fun loadEffective(override: MangaReaderSettingsOverride? = null): DesktopReaderSettings =
        load().withOverride(override)

    fun load(): DesktopReaderSettings {
        val defaults = DesktopReaderSettings()
        val leftEnd = preferences.property(LEFT_END)?.toIntOrNull()
        val centerEnd = preferences.property(CENTER_END)?.toIntOrNull()
        val clickRegions = runCatching {
            ClickRegions(
                leftAction = enumOrDefault(preferences.property(LEFT_ACTION), defaults.clickRegions.leftAction),
                centerAction = enumOrDefault(preferences.property(CENTER_ACTION), defaults.clickRegions.centerAction),
                rightAction = enumOrDefault(preferences.property(RIGHT_ACTION), defaults.clickRegions.rightAction),
                leftEndPercent = leftEnd ?: defaults.clickRegions.leftEndPercent,
                centerEndPercent = centerEnd ?: defaults.clickRegions.centerEndPercent,
            )
        }.getOrDefault(defaults.clickRegions)
        return DesktopReaderSettings(
            mode = enumOrDefault(preferences.property(MODE), defaults.mode),
            coverOffset = preferences.property(COVER_OFFSET)?.toBooleanStrictOrNull() ?: defaults.coverOffset,
            scaleMode = enumOrDefault(preferences.property(SCALE), defaults.scaleMode),
            clickRegions = clickRegions,
            wheelBehavior = enumOrDefault(preferences.property(WHEEL), defaults.wheelBehavior),
            lastWindowMode = enumOrDefault(preferences.property(WINDOW), defaults.lastWindowMode),
            colorFilter = enumOrDefault(preferences.property(COLOR_FILTER), defaults.colorFilter),
            backgroundColor = enumOrDefault(preferences.property(BG_COLOR), defaults.backgroundColor),
            cropBorders = preferences.property(CROP_BORDERS)?.toBooleanStrictOrNull() ?: defaults.cropBorders,
            cropBordersWebtoon = preferences.property(CROP_BORDERS_WEBTOON)?.toBooleanStrictOrNull()
                ?: defaults.cropBordersWebtoon,
            webtoonMaxWidth = preferences.property(WEBTOON_MAX_WIDTH)?.toIntOrNull() ?: defaults.webtoonMaxWidth,
            webtoonSidePadding = preferences.property(WEBTOON_SIDE_PADDING)?.toIntOrNull()
                ?: defaults.webtoonSidePadding,
            alwaysShowChapterTransition = preferences.property(ALWAYS_SHOW_CHAPTER_TRANSITION)
                ?.toBooleanStrictOrNull() ?: defaults.alwaysShowChapterTransition,
            skipReadChapters = preferences.property(SKIP_READ)?.toBooleanStrictOrNull() ?: defaults.skipReadChapters,
            skipFilteredChapters = preferences.property(SKIP_FILTERED)?.toBooleanStrictOrNull()
                ?: defaults.skipFilteredChapters,
            skipDuplicateChapters = preferences.property(SKIP_DUPLICATE)?.toBooleanStrictOrNull()
                ?: defaults.skipDuplicateChapters,
            preloadPages = preferences.property(PRELOAD_PAGES)?.toIntOrNull()
                ?.coerceIn(DesktopPreloadPolicy.MIN_PRELOAD_PAGES, DesktopPreloadPolicy.MAX_PRELOAD_PAGES)
                ?: defaults.preloadPages,
            keepScreenOn = preferences.property(KEEP_SCREEN_ON)?.toBooleanStrictOrNull() ?: defaults.keepScreenOn,
            pageFlash = preferences.property(PAGE_FLASH)?.toBooleanStrictOrNull() ?: defaults.pageFlash,
            webtoonPreventDownsizing = preferences.property(WEBTOON_PREVENT_DOWNSIZING)?.toBooleanStrictOrNull()
                ?: defaults.webtoonPreventDownsizing,
            webtoonDoubleTapZoom = preferences.property(WEBTOON_DOUBLE_TAP_ZOOM)?.toBooleanStrictOrNull()
                ?: defaults.webtoonDoubleTapZoom,
            customHue = preferences.property(CUSTOM_HUE)?.toIntOrNull()
                ?.coerceIn(0, 360) ?: defaults.customHue,
            customBrightness = preferences.property(CUSTOM_BRIGHTNESS)?.toIntOrNull()
                ?.coerceIn(-100, 100) ?: defaults.customBrightness,
            customContrast = preferences.property(CUSTOM_CONTRAST)?.toIntOrNull()
                ?.coerceIn(-100, 100) ?: defaults.customContrast,
            dimmingPercent = preferences.property(DIMMING_PERCENT)?.toIntOrNull()
                ?.coerceIn(20, 100) ?: defaults.dimmingPercent,
            pageTransition = enumOrDefault(preferences.property(PAGE_TRANSITION), defaults.pageTransition),
            dualPageSplit = enumOrDefault(preferences.property(DUAL_PAGE_SPLIT), defaults.dualPageSplit),
            dualPageRotateToFit = preferences.property(DUAL_PAGE_ROTATE_TO_FIT)?.toBooleanStrictOrNull()
                ?: defaults.dualPageRotateToFit,
        )
    }

    fun save(settings: DesktopReaderSettings) {
        preferences.update {
            setProperty(MODE, settings.mode.name)
            setProperty(COVER_OFFSET, settings.coverOffset.toString())
            setProperty(SCALE, settings.scaleMode.name)
            setProperty(LEFT_ACTION, settings.clickRegions.leftAction.name)
            setProperty(CENTER_ACTION, settings.clickRegions.centerAction.name)
            setProperty(RIGHT_ACTION, settings.clickRegions.rightAction.name)
            setProperty(LEFT_END, settings.clickRegions.leftEndPercent.toString())
            setProperty(CENTER_END, settings.clickRegions.centerEndPercent.toString())
            setProperty(WHEEL, settings.wheelBehavior.name)
            setProperty(WINDOW, settings.lastWindowMode.name)
            setProperty(COLOR_FILTER, settings.colorFilter.name)
            setProperty(BG_COLOR, settings.backgroundColor.name)
            setProperty(CROP_BORDERS, settings.cropBorders.toString())
            setProperty(CROP_BORDERS_WEBTOON, settings.cropBordersWebtoon.toString())
            setProperty(WEBTOON_MAX_WIDTH, settings.webtoonMaxWidth.toString())
            setProperty(WEBTOON_SIDE_PADDING, settings.webtoonSidePadding.toString())
            setProperty(ALWAYS_SHOW_CHAPTER_TRANSITION, settings.alwaysShowChapterTransition.toString())
            setProperty(SKIP_READ, settings.skipReadChapters.toString())
            setProperty(SKIP_FILTERED, settings.skipFilteredChapters.toString())
            setProperty(SKIP_DUPLICATE, settings.skipDuplicateChapters.toString())
            setProperty(PRELOAD_PAGES, settings.preloadPages.toString())
            setProperty(KEEP_SCREEN_ON, settings.keepScreenOn.toString())
            setProperty(PAGE_FLASH, settings.pageFlash.toString())
            setProperty(WEBTOON_PREVENT_DOWNSIZING, settings.webtoonPreventDownsizing.toString())
            setProperty(WEBTOON_DOUBLE_TAP_ZOOM, settings.webtoonDoubleTapZoom.toString())
            setProperty(CUSTOM_HUE, settings.customHue.toString())
            setProperty(CUSTOM_BRIGHTNESS, settings.customBrightness.toString())
            setProperty(CUSTOM_CONTRAST, settings.customContrast.toString())
            setProperty(DIMMING_PERCENT, settings.dimmingPercent.toString())
            setProperty(PAGE_TRANSITION, settings.pageTransition.name)
            setProperty(DUAL_PAGE_SPLIT, settings.dualPageSplit.name)
            setProperty(DUAL_PAGE_ROTATE_TO_FIT, settings.dualPageRotateToFit.toString())
        }
    }

    fun isChapterBookmarked(chapterId: Long): Boolean =
        preferences.property(bookmarkKey(chapterId))?.toBooleanStrictOrNull() ?: false

    fun setChapterBookmarked(chapterId: Long, bookmarked: Boolean) {
        val key = bookmarkKey(chapterId)
        preferences.update {
            if (bookmarked) {
                setProperty(key, true.toString())
            } else {
                remove(key)
            }
        }
    }

    fun bookmarkStore(): ReaderChapterBookmarkStore = object : ReaderChapterBookmarkStore {
        override fun isBookmarked(chapterId: Long): Boolean = isChapterBookmarked(chapterId)

        override fun setBookmarked(chapterId: Long, bookmarked: Boolean) =
            setChapterBookmarked(chapterId, bookmarked)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private fun bookmarkKey(chapterId: Long): String = "$BOOKMARK_PREFIX$chapterId"

    private companion object {
        const val BOOKMARK_PREFIX = "reader.bookmark.chapter."
        const val MODE = "reader.v1.mode"
        const val COVER_OFFSET = "reader.v1.cover-offset"
        const val SCALE = "reader.v1.scale"
        const val LEFT_ACTION = "reader.v1.click.left-action"
        const val CENTER_ACTION = "reader.v1.click.center-action"
        const val RIGHT_ACTION = "reader.v1.click.right-action"
        const val LEFT_END = "reader.v1.click.left-end"
        const val CENTER_END = "reader.v1.click.center-end"
        const val WHEEL = "reader.v1.wheel"
        const val WINDOW = "reader.v1.window"
        const val COLOR_FILTER = "reader.v1.color-filter"
        const val BG_COLOR = "reader.v1.background-color"
        const val CROP_BORDERS = "reader.v1.crop-borders"
        const val CROP_BORDERS_WEBTOON = "reader.v1.crop-borders-webtoon"
        const val WEBTOON_MAX_WIDTH = "reader.v1.webtoon-max-width"
        const val WEBTOON_SIDE_PADDING = "reader.v1.webtoon-side-padding"
        const val ALWAYS_SHOW_CHAPTER_TRANSITION = "reader.v1.always-show-chapter-transition"
        const val SKIP_READ = "reader.v1.skip-read"
        const val SKIP_FILTERED = "reader.v1.skip-filtered"
        const val SKIP_DUPLICATE = "reader.v1.skip-duplicate"
        const val PRELOAD_PAGES = "reader.v1.preload-pages"
        const val KEEP_SCREEN_ON = "reader.v1.keep-screen-on"
        const val PAGE_FLASH = "reader.v1.page-flash"
        const val WEBTOON_PREVENT_DOWNSIZING = "reader.v1.webtoon-prevent-downsizing"
        const val WEBTOON_DOUBLE_TAP_ZOOM = "reader.v1.webtoon-double-tap-zoom"
        const val CUSTOM_HUE = "reader.v1.custom-hue"
        const val CUSTOM_BRIGHTNESS = "reader.v1.custom-brightness"
        const val CUSTOM_CONTRAST = "reader.v1.custom-contrast"
        const val DIMMING_PERCENT = "reader.v1.dimming-percent"
        const val PAGE_TRANSITION = "reader.v1.page-transition"
        const val DUAL_PAGE_SPLIT = "reader.v1.dual-page-split"
        const val DUAL_PAGE_ROTATE_TO_FIT = "reader.v1.dual-page-rotate-to-fit"
    }
}
