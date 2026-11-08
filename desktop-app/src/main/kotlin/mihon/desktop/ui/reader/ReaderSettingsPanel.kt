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
