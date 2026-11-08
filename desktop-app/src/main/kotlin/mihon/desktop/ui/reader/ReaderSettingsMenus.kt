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
internal fun FilterSettingsPage(settings: DesktopReaderSettings, onChange: (DesktopReaderSettings) -> Unit) {
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
internal fun SettingCheckBox(tag: String, checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
        Text(label)
    }
}

@Composable
internal fun ClickActionMenu(
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
internal fun WheelMenu(current: ReaderWheelBehavior, onSelected: (ReaderWheelBehavior) -> Unit) {
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
internal fun SettingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
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
internal fun SettingScaleMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
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
internal fun SettingTransitionMenu(current: ReaderPageTransition, onSelected: (ReaderPageTransition) -> Unit) {
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
internal fun SettingDualPageSplitMenu(current: DualPageSplit, onSelected: (DualPageSplit) -> Unit) {
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
internal fun SettingColorFilterMenu(current: ReaderColorFilter, onSelected: (ReaderColorFilter) -> Unit) {
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
internal fun SettingBackgroundColorMenu(
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
