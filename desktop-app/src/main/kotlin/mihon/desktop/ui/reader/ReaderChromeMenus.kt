package mihon.desktop.ui.reader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.reader.input.ReaderShortcutAction
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyBindingMap
import mihon.desktop.ui.shortcut.chordsLabel
import mihon.desktop.ui.shortcut.shortcutLabel
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderState

internal const val READER_BARS_SLIDE_MILLIS = 200

@Composable
internal fun ReaderShortcutTooltip(
    label: String,
    action: ReaderShortcutAction,
    content: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    val chords = DesktopKeyBindings.defaultReader.chordsFor(action)
    val tooltip = if (chords.isEmpty()) {
        label
    } else {
        strings.text(UiText.ReaderTooltipWithKey, label, strings.chordsLabel(chords))
    }
    DesktopTooltipBox(tooltip = tooltip, content = content)
}
internal const val READER_BARS_FADE_MILLIS = 150

@Composable
internal fun ReadingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-mode-menu")) {
            Text(strings.readerModeLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadingMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(strings.readerModeLabel(mode)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                    modifier = Modifier.testTag("reader-mode-${mode.name}"),
                )
            }
        }
    }
}

@Composable
internal fun ScaleModeMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("reader-scale-menu")) {
            Text(strings.readerScaleLabel(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScaleMode.entries.forEach { scale ->
                DropdownMenuItem(
                    text = { Text(strings.readerScaleLabel(scale)) },
                    onClick = {
                        expanded = false
                        onSelected(scale)
                    },
                    modifier = Modifier.testTag("reader-scale-${scale.name}"),
                )
            }
        }
    }
}

@Composable
internal fun ReaderShortcutsDialog(
    onDismiss: () -> Unit,
    bindings: KeyBindingMap<ReaderShortcutAction> = DesktopKeyBindings.defaultReader,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(strings.readerShortcutsTitle, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                @Composable
                fun row(action: ReaderShortcutAction) {
                    ShortcutItemRow(strings.chordsLabel(bindings.chordsFor(action)), strings.shortcutLabel(action))
                }
                ShortcutSectionHeader(strings.text(UiText.Navigation))
                listOf(
                    ReaderShortcutAction.PAGE_LEFT,
                    ReaderShortcutAction.PAGE_RIGHT,
                    ReaderShortcutAction.PREVIOUS_PAGE,
                    ReaderShortcutAction.NEXT_PAGE,
                    ReaderShortcutAction.FIRST_PAGE,
                    ReaderShortcutAction.LAST_PAGE,
                ).forEach { row(it) }
                ShortcutItemRow(strings.text(UiText.MouseBackForward), strings.text(UiText.PreviousNextPage))
                listOf(
                    ReaderShortcutAction.PREVIOUS_CHAPTER,
                    ReaderShortcutAction.NEXT_CHAPTER,
                    ReaderShortcutAction.TOGGLE_BOOKMARK,
                    ReaderShortcutAction.CYCLE_READING_MODE,
                    ReaderShortcutAction.PAGE_ACTIONS,
                ).forEach { row(it) }

                ShortcutSectionHeader(strings.text(UiText.ZoomWindow))
                listOf(
                    ReaderShortcutAction.ZOOM_IN,
                    ReaderShortcutAction.ZOOM_OUT,
                    ReaderShortcutAction.RESET_ZOOM,
                    ReaderShortcutAction.FULLSCREEN,
                    ReaderShortcutAction.EXIT,
                ).forEach { row(it) }

                ShortcutSectionHeader(strings.text(UiText.ControlsHelp))
                ShortcutItemRow(strings.text(UiText.ClickCenter), strings.text(UiText.ShowHideControls))
                row(ReaderShortcutAction.SHOW_HELP)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogOk)
            }
        },
    )
}

@Composable
internal fun ShortcutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun ShortcutItemRow(keys: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal const val MIB = 1024L * 1024L
