package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

internal fun searchableReaderEntries(strings: DesktopStrings): List<SearchableSettingEntry> =
    searchableReaderEntriesPart1(strings) +
        searchableReaderEntriesPart2(strings) +
        searchableReaderEntriesPart3(strings) +
        searchableReaderEntriesPart4(strings) +
        searchableReaderEntriesPart5(strings) +
        searchableReaderEntriesPart6(strings)

private fun searchableReaderEntriesPart1(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "reader-reading-mode",
        section = SettingsSection.Reader,
        title = strings.settingsDefaultReadingMode,
        subtitle = "${strings.readerModeSingleLtr}, ${strings.readerModeSingleRtl}, ${strings.readerModeWebtoon}",
        keywords = listOf(
            "reading mode",
            "webtoon",
            "vertical",
            "horizontal",
            "ltr",
            "rtl",
            "阅读模式",
            "閱讀模式",
            "条漫",
            "條漫",
            "单页",
        ),
    ),
    SearchableSettingEntry(
        id = "reader-scale-type",
        section = SettingsSection.Reader,
        title = strings.settingsDefaultScaleMode,
        subtitle = listOf(
            strings.readerScaleFitWidth,
            strings.readerScaleFitHeight,
            strings.readerScaleOriginal,
        ).joinToString(),
        keywords = listOf("scale", "fit", "stretch", "original", "缩放", "縮放", "适应宽度", "拉伸"),
    ),
)

private fun searchableReaderEntriesPart2(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "reader-color-filter",
        section = SettingsSection.Reader,
        title = strings.readerColorFilter,
        subtitle = listOf(
            strings.readerFilterInvert,
            strings.readerFilterGrayscale,
            strings.readerFilterSepia,
            strings.readerFilterNight,
            strings.readerFilterCustom,
        ).joinToString(),
        keywords = listOf(
            "color filter",
            "filter",
            "grayscale",
            "sepia",
            "night",
            "custom",
            "invert",
            "滤镜",
            "濾鏡",
            "反转",
            "灰阶",
            "黑白",
            "夜间",
        ),
    ),
    SearchableSettingEntry(
        id = "reader-custom-filter",
        section = SettingsSection.Reader,
        title = strings.readerFilterCustom,
        subtitle = listOf(
            strings.readerCustomHue,
            strings.readerCustomBrightness,
            strings.readerCustomContrast,
            strings.readerDimming,
        ).joinToString(),
        keywords = listOf("hue", "brightness", "contrast", "dimming", "色相", "亮度", "对比度", "变暗", "屏幕变暗", "自订", "自定义"),
    ),
    SearchableSettingEntry(
        id = "reader-background-color",
        section = SettingsSection.Reader,
        title = strings.readerBackgroundColor,
        subtitle = listOf(
            strings.readerBgDarkGray,
            strings.readerBgBlack,
            strings.readerBgWhite,
            strings.readerBgWarmCream,
        ).joinToString(),
        keywords = listOf("background", "bg color", "canvas", "black", "white", "背景", "背景色", "纯黑", "暖白", "深灰"),
    ),
)

private fun searchableReaderEntriesPart3(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "reader-page-transitions",
        section = SettingsSection.Reader,
        title = strings.readerPageTransitions,
        subtitle = mihon.desktop.reader.ReaderPageTransition.entries.joinToString {
            strings.readerPageTransitionLabel(it)
        },
        keywords = listOf("transition", "animation", "fade", "slide", "flip", "过渡", "過渡", "动画", "淡入淡出", "滑动"),
    ),
    SearchableSettingEntry(
        id = "reader-dual-page-split",
        section = SettingsSection.Reader,
        title = strings.readerDualPageSplit,
        subtitle = strings.readerDualPageRotateToFit,
        keywords = listOf(
            "dual page",
            "split",
            "wide page",
            "spread",
            "rotate",
            "跨页",
            "跨頁",
            "双页",
            "雙頁",
            "分割",
            "拆分",
            "旋转",
        ),
    ),
    SearchableSettingEntry(
        id = "reader-double-spread",
        section = SettingsSection.Reader,
        title = strings.settingsDoubleSpread,
        subtitle = null,
        keywords = listOf("spread", "double spread", "dual", "双页", "雙頁"),
    ),
    SearchableSettingEntry(
        id = "reader-crop-borders",
        section = SettingsSection.Reader,
        title = strings.readerCropBorders,
        subtitle = strings.readerCropBordersWebtoon,
        keywords = listOf("crop", "borders", "trim", "margins", "white space", "裁剪", "切边", "白边", "裁邊", "留白"),
    ),
    SearchableSettingEntry(
        id = "reader-webtoon-layout",
        section = SettingsSection.Reader,
        title = strings.readerWebtoonMaxWidth,
        subtitle = strings.readerWebtoonSidePadding,
        keywords = listOf("webtoon width", "max width", "side padding", "margin", "条漫宽度", "條漫寬度", "侧边距", "邊距"),
    ),
)

private fun searchableReaderEntriesPart4(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "reader-keep-screen-on",
        section = SettingsSection.Reader,
        title = strings.readerKeepScreenOn,
        subtitle = null,
        keywords = listOf("keep screen on", "sleep", "awake", "display", "常亮", "屏幕常亮", "螢幕常亮", "睡眠", "熄屏"),
    ),
    SearchableSettingEntry(
        id = "reader-page-flash",
        section = SettingsSection.Reader,
        title = strings.readerPageFlash,
        subtitle = null,
        keywords = listOf("flash", "e-ink", "page flash", "闪烁", "閃爍", "水墨屏"),
    ),
    SearchableSettingEntry(
        id = "reader-webtoon-zoom",
        section = SettingsSection.Reader,
        title = strings.readerWebtoonPreventDownsizing,
        subtitle = strings.readerWebtoonDoubleTapZoom,
        keywords = listOf("downsizing", "double tap", "zoom", "webtoon zoom", "双击缩放", "禁用缩小"),
    ),
    SearchableSettingEntry(
        id = "shortcuts-keymap",
        section = SettingsSection.Shortcuts,
        title = strings.text(UiText.ShortcutSettingsTitle),
        subtitle = strings.text(UiText.ShortcutSettingsHint),
        keywords = listOf(
            "keyboard", "shortcut", "hotkey", "keymap", "key binding", "rebind",
            "快捷键", "快速鍵", "按键", "按鍵", "热键", "熱鍵",
        ),
    ),
    SearchableSettingEntry(
        id = "downloads-location",
        section = SettingsSection.Downloads,
        title = strings.settingsDownloadLocation,
        subtitle = strings.settingsDefaultStorageFolder,
        keywords = listOf(
            "download location",
            "storage path",
            "folder",
            "directory",
            "下载位置",
            "下載目錄",
            "保存路径",
            "存储路径",
        ),
    ),
)

private fun searchableReaderEntriesPart5(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "downloads-parallel",
        section = SettingsSection.Downloads,
        title = strings.settingsParallelDownloads,
        subtitle = strings.text(UiText.ParallelPages),
        keywords = listOf("parallel", "concurrent", "threads", "simultaneous", "并发", "並發", "同时下载", "多线程"),
    ),
    SearchableSettingEntry(
        id = "downloads-ahead",
        section = SettingsSection.Downloads,
        title = strings.settingsDownloadAheadTitle,
        subtitle = null,
        keywords = listOf("download ahead", "preload", "buffer", "cache", "预下载", "預先下載", "自动预载"),
    ),
    SearchableSettingEntry(
        id = "downloads-delete-read",
        section = SettingsSection.Downloads,
        title = strings.settingsDeleteReadChaptersTitle,
        subtitle = null,
        keywords = listOf("delete read", "auto delete", "remove", "clean", "已读删除", "已讀刪除", "自动清理", "阅读后删除"),
    ),
    SearchableSettingEntry(
        id = "downloads-save-as-cbz",
        section = SettingsSection.Downloads,
        title = strings.settingsSaveChapterAsCbzTitle,
        subtitle = strings.settingsSaveChapterAsCbzDesc,
        keywords = listOf(
            "cbz",
            "zip",
            "archive",
            "compressed",
            "folder",
            "单文件",
            "壓縮包",
            "打包",
            "資料夾",
        ),
    ),
)

private fun searchableReaderEntriesPart6(strings: DesktopStrings): List<SearchableSettingEntry> = listOf(
    SearchableSettingEntry(
        id = "downloads-split-tall-images",
        section = SettingsSection.Downloads,
        title = strings.settingsSplitTallImagesTitle,
        subtitle = strings.settingsSplitTallImagesDesc,
        keywords = listOf(
            "split",
            "tall",
            "long image",
            "webtoon",
            "strip",
            "长图",
            "長圖",
            "拆分",
            "分割",
        ),
    ),
    SearchableSettingEntry(
        id = "downloads-auto-download-new",
        section = SettingsSection.Downloads,
        title = strings.libraryAutoDownloadNew,
        subtitle = null,
        keywords = listOf("auto download", "new chapters", "自动下载新章节", "下載新章節"),
    ),
    SearchableSettingEntry(
        id = "downloads-unread-only",
        section = SettingsSection.Downloads,
        title = strings.downloadNewUnreadOnly,
        keywords = listOf("unread only", "new chapters", "仅未读", "僅未讀"),
    ),
    SearchableSettingEntry(
        id = "downloads-categories",
        section = SettingsSection.Downloads,
        title = strings.downloadNewCategories,
        keywords = listOf("download categories", "include", "exclude", "分类选择", "分類選擇"),
    ),
)
