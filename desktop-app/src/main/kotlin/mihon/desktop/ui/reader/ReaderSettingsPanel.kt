package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
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

private enum class ReaderSettingsPage {
    READING,
    GENERAL,
    FILTER,
}

internal val READER_SETTINGS_PANEL_WIDTH = 360.dp

/**
 * Non-modal side panel: every change is applied (and persisted) immediately so the page behind
 * shows the result while adjusting. There is no draft, save or cancel; Reset restores defaults.
 */
@Composable
internal fun ReaderSettingsPanel(
    settings: DesktopReaderSettings,
    onChange: (DesktopReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var page by remember { mutableStateOf(ReaderSettingsPage.READING) }
    Surface(
        modifier = modifier
            .width(READER_SETTINGS_PANEL_WIDTH)
            .fillMaxHeight()
            .testTag("reader-settings-panel")
            .semantics { paneTitle = strings.readerSettingsDialogTitle },
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.readerSettingsDialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("reader-settings-close")) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.dialogClose)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SettingsTab(
                    strings.readerSettingsTabReading,
                    "reader-settings-tab-reading",
                    page == ReaderSettingsPage.READING,
                ) { page = ReaderSettingsPage.READING }
                SettingsTab(
                    strings.readerSettingsTabGeneral,
                    "reader-settings-tab-general",
                    page == ReaderSettingsPage.GENERAL,
                ) { page = ReaderSettingsPage.GENERAL }
                SettingsTab(
                    strings.readerSettingsTabFilter,
                    "reader-settings-tab-filter",
                    page == ReaderSettingsPage.FILTER,
                ) { page = ReaderSettingsPage.FILTER }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (page) {
                    ReaderSettingsPage.READING -> ReadingSettingsPage(settings, onChange)
                    ReaderSettingsPage.GENERAL -> GeneralSettingsPage(settings, onChange)
                    ReaderSettingsPage.FILTER -> FilterSettingsPage(settings, onChange)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onChange(DesktopReaderSettings()) },
                    modifier = Modifier.testTag("reader-settings-reset"),
                ) { Text(strings.libraryFilterReset) }
            }
        }
    }
}

@Composable
private fun SettingsTab(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
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
private fun GeneralSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
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

@Composable
private fun FilterSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
    val strings = LocalStrings.current
    Text(strings.settingsSectionAppearance, style = MaterialTheme.typography.titleSmall)
    SettingColorFilterMenu(settings.colorFilter) { onChange(settings.copy(colorFilter = it)) }
    SettingBackgroundColorMenu(settings.backgroundColor) { onChange(settings.copy(backgroundColor = it)) }

    Box(modifier = Modifier.testTag("reader-setting-hue")) {
        Text(strings.readerCustomHueLabel(settings.customHue))
    }
    Slider(
        value = settings.customHue.toFloat(),
        onValueChange = { onChange(settings.copy(customHue = it.roundToInt())) },
        valueRange = 0f..360f,
        modifier = Modifier.testTag("reader-setting-custom-hue"),
    )

    Box(modifier = Modifier.testTag("reader-setting-brightness")) {
        Text(strings.readerCustomBrightnessLabel(settings.customBrightness))
    }
    Slider(
        value = settings.customBrightness.toFloat(),
        onValueChange = { onChange(settings.copy(customBrightness = it.roundToInt())) },
        valueRange = -100f..100f,
        modifier = Modifier.testTag("reader-setting-custom-brightness"),
    )

    Box(modifier = Modifier.testTag("reader-setting-contrast")) {
        Text(strings.readerCustomContrastLabel(settings.customContrast))
    }
    Slider(
        value = settings.customContrast.toFloat(),
        onValueChange = { onChange(settings.copy(customContrast = it.roundToInt())) },
        valueRange = -100f..100f,
        modifier = Modifier.testTag("reader-setting-custom-contrast"),
    )

    Box(modifier = Modifier.testTag("reader-setting-dimming-percent")) {
        Text(strings.readerDimmingLabel(settings.dimmingPercent))
    }
    Slider(
        value = settings.dimmingPercent.toFloat(),
        onValueChange = { onChange(settings.copy(dimmingPercent = it.roundToInt().coerceIn(20, 100))) },
        valueRange = 20f..100f,
        modifier = Modifier.testTag("reader-setting-dimming"),
    )
}

@Composable
private fun SettingCheckBox(tag: String, checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
        Text(label)
    }
}

@Composable
private fun ClickActionMenu(
    label: String,
    tag: String,
    current: ReaderClickAction,
    onSelected: (ReaderClickAction) -> Unit,
) {
    val strings = LocalStrings.current
    SettingsMenu(
        tag = tag,
        label = "$label: ${strings.readerActionLabel(current)}",
        values = ReaderClickAction.entries,
        valueLabel = { strings.readerActionLabel(it) },
        onSelected = onSelected,
    )
}

@Composable
private fun WheelMenu(current: ReaderWheelBehavior, onSelected: (ReaderWheelBehavior) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-wheel",
        strings.readerWheelLabel(current),
        ReaderWheelBehavior.entries,
        { strings.readerWheelLabel(it) },
        onSelected,
    )
}

@Composable
private fun SettingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-mode",
        "${strings.settingsDefaultReadingMode}: ${strings.readerModeLabel(current)}",
        ReadingMode.entries,
        { strings.readerModeLabel(it) },
        onSelected,
        optionTag = { "reader-setting-mode-${it.name}" },
    )
}

@Composable
private fun SettingScaleMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-scale",
        "${strings.settingsDefaultScaleMode}: ${strings.readerScaleLabel(current)}",
        ScaleMode.entries,
        { strings.readerScaleLabel(it) },
        onSelected,
        optionTag = { "reader-setting-scale-${it.name}" },
    )
}

@Composable
private fun SettingTransitionMenu(current: ReaderPageTransition, onSelected: (ReaderPageTransition) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-transition",
        "${strings.readerPageTransitions}: ${strings.readerPageTransitionLabel(current)}",
        ReaderPageTransition.entries,
        { strings.readerPageTransitionLabel(it) },
        onSelected,
        optionTag = { "reader-setting-transition-${it.name}" },
    )
}

@Composable
private fun SettingDualPageSplitMenu(current: DualPageSplit, onSelected: (DualPageSplit) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-dual-page-split",
        "${strings.readerDualPageSplit}: ${strings.readerDualPageSplitLabel(current)}",
        DualPageSplit.entries,
        { strings.readerDualPageSplitLabel(it) },
        onSelected,
        optionTag = { "reader-setting-dual-page-split-${it.name}" },
    )
}

@Composable
private fun SettingColorFilterMenu(current: ReaderColorFilter, onSelected: (ReaderColorFilter) -> Unit) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-filter",
        "${strings.readerColorFilter}: ${strings.readerFilterLabel(current)}",
        ReaderColorFilter.entries,
        { strings.readerFilterLabel(it) },
        onSelected,
        optionTag = { "reader-setting-filter-${it.name}" },
    )
}

@Composable
private fun SettingBackgroundColorMenu(
    current: ReaderBackgroundColor,
    onSelected: (ReaderBackgroundColor) -> Unit,
) {
    val strings = LocalStrings.current
    SettingsMenu(
        "reader-setting-bg",
        "${strings.readerBackgroundColor}: ${strings.readerBackgroundLabel(current)}",
        ReaderBackgroundColor.entries,
        { strings.readerBackgroundLabel(it) },
        onSelected,
        optionTag = { "reader-setting-bg-${it.name}" },
    )
}

@Composable
private fun <T> SettingsMenu(
    tag: String,
    label: String,
    values: Iterable<T>,
    valueLabel: (T) -> String,
    onSelected: (T) -> Unit,
    optionTag: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(valueLabel(value)) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                    modifier = optionTag?.let { Modifier.testTag(it(value)) } ?: Modifier,
                )
            }
        }
    }
}
