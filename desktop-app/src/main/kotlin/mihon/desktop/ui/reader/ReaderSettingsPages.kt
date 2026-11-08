package mihon.desktop.ui.reader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.AutoScrollController
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderPageTransition
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.layout.DualPageSplit
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import kotlin.math.roundToInt

@Composable
internal fun ReadingSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    SettingModeMenu(settings.mode) { onChange(settings.copy(mode = it)) }
    SettingScaleMenu(settings.scaleMode) { onChange(settings.copy(scaleMode = it)) }
    SettingTransitionMenu(settings.pageTransition) { onChange(settings.copy(pageTransition = it)) }
    SettingDualPageSplitMenu(settings.dualPageSplit) { onChange(settings.copy(dualPageSplit = it)) }
    SettingCheckBox(
        "reader-setting-dual-page-rotate",
        settings.dualPageRotateToFit,
        strings.readerDualPageRotateToFit,
    ) {
        onChange(settings.copy(dualPageRotateToFit = it))
    }
    SettingCheckBox("reader-setting-cover", settings.coverOffset, strings.readerReserveCover) {
        onChange(settings.copy(coverOffset = it))
    }
    SettingCheckBox("reader-setting-crop-paged", settings.cropBorders, strings.readerCropBordersPaged) {
        onChange(settings.copy(cropBorders = it))
    }
    SettingCheckBox("reader-setting-crop-webtoon", settings.cropBordersWebtoon, strings.readerCropBordersWebtoon) {
        onChange(settings.copy(cropBordersWebtoon = it))
    }
    Text(strings.readerWebtoonLayout, style = MaterialTheme.typography.titleSmall)
    Text(
        if (settings.webtoonMaxWidth == 0) {
            strings.readerWebtoonWidthFull
        } else {
            strings.readerWebtoonWidthDp(settings.webtoonMaxWidth)
        },
    )
    Slider(
        value = settings.webtoonMaxWidth.toFloat(),
        onValueChange = { onChange(settings.copy(webtoonMaxWidth = it.toInt())) },
        valueRange = 0f..1600f,
        modifier = Modifier.testTag("reader-setting-webtoon-max-width"),
    )
    Text(strings.readerWebtoonSidePaddingPercent(settings.webtoonSidePadding))
    Slider(
        value = settings.webtoonSidePadding.toFloat(),
        onValueChange = { onChange(settings.copy(webtoonSidePadding = it.toInt())) },
        valueRange = 0f..30f,
        modifier = Modifier.testTag("reader-setting-webtoon-side-padding"),
    )
    SettingCheckBox(
        "reader-setting-webtoon-prevent-downsizing",
        settings.webtoonPreventDownsizing,
        strings.readerWebtoonPreventDownsizing,
    ) {
        onChange(settings.copy(webtoonPreventDownsizing = it))
    }
    SettingCheckBox(
        "reader-setting-webtoon-double-tap-zoom",
        settings.webtoonDoubleTapZoom,
        strings.readerWebtoonDoubleTapZoom,
    ) {
        onChange(settings.copy(webtoonDoubleTapZoom = it))
    }
}

@Composable
internal fun GeneralSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    SettingCheckBox(
        "reader-setting-immersive-borderless",
        settings.immersiveBorderless,
        strings.text(UiText.ReaderImmersiveBorderless),
    ) {
        onChange(settings.copy(immersiveBorderless = it))
    }
    Text(strings.readerClickRegions, style = MaterialTheme.typography.titleSmall)
    ClickActionMenu(strings.readerRegionLeft, "reader-setting-left-action", settings.clickRegions.leftAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(leftAction = it)))
    }
    ClickActionMenu(strings.readerRegionCenter, "reader-setting-center-action", settings.clickRegions.centerAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(centerAction = it)))
    }
    ClickActionMenu(strings.readerRegionRight, "reader-setting-right-action", settings.clickRegions.rightAction) {
        onChange(settings.copy(clickRegions = settings.clickRegions.copy(rightAction = it)))
    }
    Text(strings.readerLeftBoundary(settings.clickRegions.leftEndPercent))
    Slider(
        value = settings.clickRegions.leftEndPercent.toFloat(),
        onValueChange = { value ->
            val left = value.toInt().coerceIn(1, settings.clickRegions.centerEndPercent - 1)
            onChange(settings.copy(clickRegions = settings.clickRegions.copy(leftEndPercent = left)))
        },
        valueRange = 1f..98f,
        modifier = Modifier.testTag("reader-setting-left-boundary"),
    )
    Text(strings.readerCenterBoundary(settings.clickRegions.centerEndPercent))
    Slider(
        value = settings.clickRegions.centerEndPercent.toFloat(),
        onValueChange = { value ->
            val center = value.toInt().coerceIn(settings.clickRegions.leftEndPercent + 1, 99)
            onChange(settings.copy(clickRegions = settings.clickRegions.copy(centerEndPercent = center)))
        },
        valueRange = 2f..99f,
        modifier = Modifier.testTag("reader-setting-center-boundary"),
    )
    WheelMenu(settings.wheelBehavior) { onChange(settings.copy(wheelBehavior = it)) }
    Text("${strings.text(UiText.AutoScrollSpeed)}: ${settings.autoScrollSpeed} dp/s")
    Slider(
        value = settings.autoScrollSpeed.toFloat(),
        onValueChange = { value ->
            val step = AutoScrollController.STEP_DP_PER_SECOND
            val speed = (value.roundToInt() / step * step).coerceIn(
                AutoScrollController.MIN_DP_PER_SECOND,
                AutoScrollController.MAX_DP_PER_SECOND,
            )
            onChange(settings.copy(autoScrollSpeed = speed))
        },
        valueRange = AutoScrollController.MIN_DP_PER_SECOND.toFloat()..AutoScrollController.MAX_DP_PER_SECOND.toFloat(),
        modifier = Modifier.testTag("reader-setting-auto-scroll-speed"),
    )
    Text(strings.text(UiText.ReaderPreloadPages, settings.preloadPages))
    Slider(
        value = settings.preloadPages.toFloat(),
        onValueChange = { value ->
            onChange(settings.copy(preloadPages = value.roundToInt().coerceIn(1, 10)))
        },
        valueRange = 1f..10f,
        steps = 8,
        modifier = Modifier.testTag("reader-setting-preload-pages"),
    )
    Text(strings.readerChapterTransitions, style = MaterialTheme.typography.titleSmall)
    SettingCheckBox(
        "reader-setting-always-show-transition",
        settings.alwaysShowChapterTransition,
        strings.readerAlwaysShowChapterTransition,
    ) { onChange(settings.copy(alwaysShowChapterTransition = it)) }
    SettingCheckBox("reader-setting-skip-read", settings.skipReadChapters, strings.readerSkipReadChapters) {
        onChange(settings.copy(skipReadChapters = it))
    }
    SettingCheckBox("reader-setting-skip-filtered", settings.skipFilteredChapters, strings.readerSkipFilteredChapters) {
        onChange(settings.copy(skipFilteredChapters = it))
    }
    SettingCheckBox(
        "reader-setting-skip-duplicate",
        settings.skipDuplicateChapters,
        strings.readerSkipDuplicateChapters,
    ) {
        onChange(settings.copy(skipDuplicateChapters = it))
    }
    SettingCheckBox(
        "reader-setting-page-flash",
        settings.pageFlash,
        strings.readerPageFlash,
    ) {
        onChange(settings.copy(pageFlash = it))
    }
}
