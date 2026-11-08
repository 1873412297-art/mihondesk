package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode

@Composable
internal fun ReaderSettingsPane(presenter: SettingsPresenter) {
    val strings = LocalStrings.current
    var settings by remember { mutableStateOf(presenter.readerSettings.value) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionReader,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDefaultReadingMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReadingMode.SINGLE_LTR to strings.readerModeSingleLtr,
                        ReadingMode.SINGLE_RTL to strings.readerModeSingleRtl,
                        ReadingMode.WEBTOON to strings.readerModeWebtoon,
                    ).forEach { (mode, label) ->
                        val isSelected = settings.mode == mode
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(mode = mode)
                                    presenter.saveReaderSettings(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsDefaultScaleMode, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScaleMode.FIT_WIDTH to strings.readerScaleFitWidth,
                        ScaleMode.FIT_HEIGHT to strings.readerScaleFitHeight,
                        ScaleMode.ORIGINAL to strings.readerScaleOriginal,
                    ).forEach { (scale, label) ->
                        val isSelected = settings.scaleMode == scale
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(scaleMode = scale)
                                    presenter.saveReaderSettings(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(strings.settingsDoubleSpread, fontWeight = FontWeight.Bold)
                        Text(
                            strings.settingsDoubleSpread,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.coverOffset,
                        onCheckedChange = { checked ->
                            settings = settings.copy(coverOffset = checked)
                            presenter.saveReaderSettings(settings)
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.settingsMouseWheelBehavior, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderWheelBehavior.PAGE_NAVIGATION to strings.settingsWheelFlipPage,
                        ReaderWheelBehavior.SCROLL to strings.settingsWheelScrollPage,
                    ).forEach { (behavior, label) ->
                        val isSelected = settings.wheelBehavior == behavior
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(wheelBehavior = behavior)
                                    presenter.saveReaderSettings(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerColorFilter, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderColorFilter.NONE to strings.readerFilterNone,
                        ReaderColorFilter.INVERT to strings.readerFilterInvert,
                        ReaderColorFilter.GRAYSCALE to strings.readerFilterGrayscale,
                        ReaderColorFilter.SEPIA to strings.readerFilterSepia,
                        ReaderColorFilter.NIGHT to strings.readerFilterNight,
                        ReaderColorFilter.CUSTOM to strings.readerFilterCustom,
                    ).forEach { (filter, label) ->
                        val isSelected = settings.colorFilter == filter
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(colorFilter = filter)
                                    presenter.saveReaderSettings(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.readerBackgroundColor, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderBackgroundColor.DARK_GRAY to strings.readerBgDarkGray,
                        ReaderBackgroundColor.BLACK to strings.readerBgBlack,
                        ReaderBackgroundColor.WHITE to strings.readerBgWhite,
                        ReaderBackgroundColor.WARM_CREAM to strings.readerBgWarmCream,
                    ).forEach { (bg, label) ->
                        val isSelected = settings.backgroundColor == bg
                        if (isSelected) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settings = settings.copy(backgroundColor = bg)
                                    presenter.saveReaderSettings(settings)
                                },
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBorders, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBorders,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBorders = checked)
                            presenter.saveReaderSettings(settings)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerCropBordersWebtoon, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.cropBordersWebtoon,
                        onCheckedChange = { checked ->
                            settings = settings.copy(cropBordersWebtoon = checked)
                            presenter.saveReaderSettings(settings)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerKeepScreenOn, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.keepScreenOn,
                        onCheckedChange = { checked ->
                            settings = settings.copy(keepScreenOn = checked)
                            presenter.saveReaderSettings(settings)
                        },
                        modifier = Modifier.testTag("reader-keep-screen-on"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerPageFlash, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.pageFlash,
                        onCheckedChange = { checked ->
                            settings = settings.copy(pageFlash = checked)
                            presenter.saveReaderSettings(settings)
                        },
                        modifier = Modifier.testTag("reader-page-flash"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerWebtoonPreventDownsizing, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.webtoonPreventDownsizing,
                        onCheckedChange = { checked ->
                            settings = settings.copy(webtoonPreventDownsizing = checked)
                            presenter.saveReaderSettings(settings)
                        },
                        modifier = Modifier.testTag("reader-webtoon-prevent-downsizing"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.readerWebtoonDoubleTapZoom, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.webtoonDoubleTapZoom,
                        onCheckedChange = { checked ->
                            settings = settings.copy(webtoonDoubleTapZoom = checked)
                            presenter.saveReaderSettings(settings)
                        },
                        modifier = Modifier.testTag("reader-webtoon-double-tap-zoom"),
                    )
                }
            }
        }
    }
}
