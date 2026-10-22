package mihon.desktop.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.settings.SettingsSection
import mihon.desktop.ui.shortcut.ShellShortcutAction
import mihon.desktop.ui.shortcut.shortcutLabel

const val COMMAND_PALETTE_TEST_TAG = "command-palette"
const val COMMAND_PALETTE_INPUT_TEST_TAG = "command-palette-input"
private const val LIBRARY_LIMIT = 6
private const val COMMAND_LIMIT = 6
private const val SOURCE_DEBOUNCE_MILLIS = 350L

private sealed interface PaletteRow {
    data class Header(val key: String, val title: String, val loading: Boolean = false, val error: String? = null) :
        PaletteRow
    data class Entry(val item: PaletteItem, val index: Int) : PaletteRow
}

/**
 * Ctrl+K: one keyboard-first box over the whole app — bookshelf (every category), commands and
 * settings answer instantly, then every installed source is searched and merged, grouped by source.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun CommandPalette(
    library: List<LibraryManga>,
    sourceSearch: PaletteSourceSearch?,
    onDismiss: () -> Unit,
    onSelect: (PaletteTarget) -> Unit,
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var sourceGroups by remember { mutableStateOf<List<PaletteSourceGroup>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val commandItems = remember(strings) {
        val destinations = listOf(
            DesktopDestination.Library to PaletteCommand.OpenLibrary,
            DesktopDestination.Updates to PaletteCommand.OpenUpdates,
            DesktopDestination.History to PaletteCommand.OpenHistory,
            DesktopDestination.Browse to PaletteCommand.OpenBrowse,
            DesktopDestination.Downloads to PaletteCommand.OpenDownloads,
            DesktopDestination.Stats to PaletteCommand.OpenStats,
        ).map { (destination, command) ->
            val label = strings.destinationLabel(destination)
            PaletteItem(
                key = "command-${command.name}",
                title = strings.text(UiText.PaletteGoTo, label),
                subtitle = null,
                target = PaletteTarget.Command(command),
                keywords = listOf(label, destination.label),
            )
        }
        val actions = listOf(
            PaletteItem(
                key = "command-${PaletteCommand.UpdateLibrary.name}",
                title = strings.text(UiText.PaletteUpdateLibrary),
                subtitle = null,
                target = PaletteTarget.Command(PaletteCommand.UpdateLibrary),
                keywords = listOf("update", "refresh", "更新", "刷新"),
            ),
            PaletteItem(
                key = "command-${PaletteCommand.ShowShortcuts.name}",
                title = strings.shortcutLabel(ShellShortcutAction.ShowShortcutsHelp),
                subtitle = null,
                target = PaletteTarget.Command(PaletteCommand.ShowShortcuts),
                keywords = listOf("shortcuts", "keys", "快捷键"),
            ),
            PaletteItem(
                key = "command-${PaletteCommand.ToggleNavigationRail.name}",
                title = strings.shortcutLabel(ShellShortcutAction.ToggleNavigationRail),
                subtitle = null,
                target = PaletteTarget.Command(PaletteCommand.ToggleNavigationRail),
                keywords = listOf("sidebar", "rail", "侧栏"),
            ),
        )
        val settings = SettingsSection.entries.map { section ->
            val label = section.localized(strings)
            PaletteItem(
                key = "settings-${section.name}",
                title = strings.text(UiText.PaletteSettingsEntry, label),
                subtitle = null,
                target = PaletteTarget.Settings(section),
                keywords = listOf(label, section.label, "settings", "设置"),
            )
        }
        destinations + actions + settings
    }
    val libraryItems = remember(library) {
        library.sortedByDescending { it.lastRead }.map { manga ->
            PaletteItem(
                key = "manga-${manga.id}",
                title = manga.title,
                subtitle = manga.author,
                target = PaletteTarget.Manga(manga.id),
                keywords = listOfNotNull(manga.author),
                thumbnailUrl = manga.thumbnailUrl,
            )
        }
    }

    val libraryMatches = remember(query, libraryItems) { filterPaletteItems(query, libraryItems, LIBRARY_LIMIT) }
    val commandMatches = remember(query, commandItems) {
        filterPaletteItems(query, commandItems, if (query.isBlank()) COMMAND_LIMIT + 2 else COMMAND_LIMIT)
    }

    LaunchedEffect(query, sourceSearch) {
        sourceGroups = emptyList()
        if (sourceSearch == null || query.trim().length < PALETTE_MIN_SOURCE_QUERY) return@LaunchedEffect
        delay(SOURCE_DEBOUNCE_MILLIS)
        sourceSearch.run(query) { sourceGroups = it }
    }

    val rows = buildList {
        var index = 0
        if (libraryMatches.isNotEmpty()) {
            add(PaletteRow.Header("section-library", strings.text(UiText.PaletteLibrary)))
            libraryMatches.forEach { add(PaletteRow.Entry(it, index++)) }
        }
        if (commandMatches.isNotEmpty()) {
            add(PaletteRow.Header("section-commands", strings.text(UiText.PaletteCommands)))
            commandMatches.forEach { add(PaletteRow.Entry(it, index++)) }
        }
        sourceGroups.forEach { group ->
            if (!group.loading && group.error == null && group.mangas.isEmpty()) return@forEach
            add(
                PaletteRow.Header(
                    key = "section-source-${group.source.id}",
                    title = group.source.name,
                    loading = group.loading,
                    error = group.error?.let { strings.text(UiText.PaletteSourceFailed) },
                ),
            )
            paletteSourceItems(listOf(group)).forEach { add(PaletteRow.Entry(it, index++)) }
        }
    }
    val entries = rows.filterIsInstance<PaletteRow.Entry>()
    val selectedIndex = selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0))

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(selectedIndex, rows.size) {
        val rowIndex = rows.indexOfFirst { it is PaletteRow.Entry && it.index == selectedIndex }
        if (rowIndex >= 0) {
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val fullyVisible = listState.layoutInfo.visibleItemsInfo.any {
                it.index == rowIndex && it.offset >= 0 && it.offset + it.size <= viewportEnd
            }
            if (!fullyVisible) {
                listState.animateScrollToItem(rowIndex)
            }
        }
    }

    val choose: (PaletteItem) -> Unit = { item ->
        onDismiss()
        onSelect(item.target)
    }

    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(top = 72.dp)
                    .width(640.dp)
                    .heightIn(max = 560.dp)
                    .clickable(interactionSource = null, indication = null) {}
                    .testTag(COMMAND_PALETTE_TEST_TAG),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = strings.text(UiText.PaletteHint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag(COMMAND_PALETTE_INPUT_TEST_TAG)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                selected = moveSelection(selectedIndex, 1, entries.size)
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                selected = moveSelection(selectedIndex, -1, entries.size)
                                                true
                                            }
                                            Key.Enter, Key.NumPadEnter -> {
                                                entries.getOrNull(selectedIndex)?.let { choose(it.item) }
                                                true
                                            }
                                            Key.Escape -> {
                                                onDismiss()
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            )
                        }
                        if (sourceGroups.any { it.loading }) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (rows.isEmpty()) {
                        Text(
                            text = strings.text(UiText.PaletteNoResults),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp).testTag("command-palette-empty"),
                        )
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                        rows.forEach { row ->
                            when (row) {
                                is PaletteRow.Header -> item(key = row.key) { PaletteHeader(row) }
                                is PaletteRow.Entry -> item(key = row.item.key) {
                                    PaletteEntryRow(
                                        item = row.item,
                                        isSelected = row.index == selectedIndex,
                                        onHover = { selected = row.index },
                                        onClick = { choose(row.item) },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = strings.text(UiText.PaletteFooter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteHeader(row: PaletteRow.Header) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            .testTag("palette-${row.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            row.loading -> Text(
                strings.text(UiText.PaletteSearching),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.error != null -> Text(
                row.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PaletteEntryRow(item: PaletteItem, isSelected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .onPointerEvent(PointerEventType.Move) { if (!isSelected) onHover() }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("palette-item-${item.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        item.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
