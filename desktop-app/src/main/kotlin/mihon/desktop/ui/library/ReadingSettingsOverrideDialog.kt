package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.reader.model.ReadingMode

@Composable
fun ReadingSettingsOverrideDialog(
    currentOverride: MangaReaderSettingsOverride?,
    onDismissRequest: () -> Unit,
    onSave: (MangaReaderSettingsOverride) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var selectedReadingMode by remember(currentOverride) {
        mutableStateOf(currentOverride?.readingMode)
    }
    var preloadCustomEnabled by remember(currentOverride) {
        mutableStateOf(currentOverride?.preloadPages != null)
    }
    var preloadPagesCount by remember(currentOverride) {
        mutableIntStateOf(currentOverride?.preloadPages ?: 4)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("reading-settings-override-dialog"),
        title = {
            Text(
                text = strings.text(UiText.ReadingSettingsOverride),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Section 1: Reading Mode
                Text(
                    text = strings.text(UiText.ReadingModeOverrideTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reading-mode-override-default")
                        .clickable { selectedReadingMode = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReadingMode == null,
                        onClick = { selectedReadingMode = null },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.text(UiText.ReadingModeFollowGlobal),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                for (mode in ReadingMode.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reading-mode-override-${mode.name.lowercase()}")
                            .clickable { selectedReadingMode = mode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReadingMode == mode,
                            onClick = { selectedReadingMode = mode },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.readerModeLabel(mode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Section 2: Preload Pages
                Text(
                    text = strings.text(UiText.PreloadPagesOverrideTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preload-pages-override-default")
                        .clickable { preloadCustomEnabled = false }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = !preloadCustomEnabled,
                        onClick = { preloadCustomEnabled = false },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.text(UiText.PreloadPagesFollowGlobal),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preload-pages-override-custom")
                        .clickable { preloadCustomEnabled = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = preloadCustomEnabled,
                        onClick = { preloadCustomEnabled = true },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.text(UiText.PreloadPagesCustom, preloadPagesCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (preloadCustomEnabled) {
                    Slider(
                        value = preloadPagesCount.toFloat(),
                        onValueChange = { preloadPagesCount = it.toInt().coerceIn(1, 10) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .testTag("preload-pages-slider"),
                    )
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        onClear()
                        onDismissRequest()
                    },
                    modifier = Modifier.testTag("reading-settings-clear-button"),
                ) {
                    Text(strings.text(UiText.ClearReadingSettingsOverride))
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("reading-settings-cancel-button"),
                ) {
                    Text(strings.dialogCancel)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        MangaReaderSettingsOverride(
                            readingMode = selectedReadingMode,
                            preloadPages = if (preloadCustomEnabled) preloadPagesCount else null,
                        ),
                    )
                    onDismissRequest()
                },
                modifier = Modifier.testTag("reading-settings-save-button"),
            ) {
                Text(strings.dialogOk)
            }
        },
    )
}
