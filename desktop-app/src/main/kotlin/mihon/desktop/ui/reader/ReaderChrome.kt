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
import mihon.desktop.ui.shortcut.DesktopKeyBindings
import mihon.desktop.ui.shortcut.KeyBindingMap
import mihon.desktop.ui.shortcut.chordsLabel
import mihon.desktop.ui.shortcut.shortcutLabel
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderState

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun ReaderChrome(
    state: ReaderState,
    title: String,
    chapterTitle: String,
    settings: DesktopReaderSettings,
    visible: Boolean,
    canRetry: Boolean,
    debugEnabled: Boolean,
    onBack: () -> Unit,
    onMode: (ReadingMode) -> Unit,
    onScale: (ScaleMode) -> Unit,
    onCoverOffset: (Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    onRetry: () -> Unit,
    onFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onColorFilter: (ReaderColorFilter) -> Unit = {},
    onBackgroundColor: (ReaderBackgroundColor) -> Unit = {},
    onCropBorders: (Boolean) -> Unit = {},
    onCropBordersWebtoon: (Boolean) -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    hasPreviousChapter: Boolean = false,
    hasNextChapter: Boolean = false,
    bookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onOpenPageActions: () -> Unit = {},
    onOpenShortcuts: () -> Unit = {},
    chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
    currentChapterId: Long? = null,
    onChapterSelected: (Long) -> Unit = {},
    onOpenMangaDetails: (() -> Unit)? = null,
    onControlsHovered: (Boolean) -> Unit = {},
) {
    val strings = LocalStrings.current
    var isChapterDrawerOpen by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val controlsHover = Modifier
        .onPointerEvent(PointerEventType.Enter) { onControlsHovered(true) }
        .onPointerEvent(PointerEventType.Exit) { onControlsHovered(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader-chrome")
            .semantics {
                readerChromeVisible = visible
                readerBookmarked = bookmarked
            },
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = slideInVertically(tween(READER_BARS_SLIDE_MILLIS)) { -it } +
                fadeIn(tween(READER_BARS_FADE_MILLIS)),
            exit = slideOutVertically(tween(READER_BARS_SLIDE_MILLIS)) { -it } +
                fadeOut(tween(READER_BARS_FADE_MILLIS)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().then(controlsHover).testTag("reader-top-bar"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader-back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.mangaDetailBack,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            modifier = Modifier.testTag("reader-title"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            chapterTitle,
                            modifier = Modifier
                                .then(
                                    if (chapterCatalog.isNotEmpty()) {
                                        Modifier.clickable { isChapterDrawerOpen = true }
                                    } else {
                                        Modifier
                                    },
                                )
                                .testTag("reader-chapter"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onOpenMangaDetails != null) {
                        TextButton(
                            onClick = onOpenMangaDetails,
                            modifier = Modifier.testTag("reader-manga-details"),
                        ) {
                            Text(strings.readerMangaDetails)
                        }
                    }
                    if (chapterCatalog.isNotEmpty()) {
                        IconButton(
                            onClick = { isChapterDrawerOpen = true },
                            modifier = Modifier.testTag("reader-chapter-list-button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.FormatListBulleted,
                                contentDescription = strings.readerChapterList,
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.testTag("reader-bookmark-toggle"),
                    ) {
                        Icon(
                            imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (bookmarked) strings.removeBookmark else strings.bookmarkChapter,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag("reader-overflow"),
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = strings.text(UiText.MoreReaderActions))
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.text(UiText.PageActions)) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenPageActions()
                                },
                                modifier = Modifier.testTag("reader-page-actions"),
                            )
                            DropdownMenuItem(
                                text = { Text(strings.readerShortcutsHelp) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenShortcuts()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                                modifier = Modifier.testTag("reader-shortcuts-btn"),
                            )
                            if (state.mode.isDualPage) {
                                DropdownMenuItem(
                                    text = { Text(strings.readerCoverOffsetToggle(state.coverOffset)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onCoverOffset(!state.coverOffset)
                                    },
                                    modifier = Modifier.testTag("reader-cover-toggle"),
                                )
                            }
                            if (canRetry) {
                                DropdownMenuItem(
                                    text = { Text(strings.downloadsRetry) },
                                    onClick = {
                                        overflowExpanded = false
                                        onRetry()
                                    },
                                    modifier = Modifier.testTag("reader-retry"),
                                )
                            }
                            if (debugEnabled) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Core ${state.cacheMetrics.residentBytes / MIB} MiB · " +
                                                "pinned ${state.cacheMetrics.pinnedBytes / MIB} MiB",
                                        )
                                    },
                                    onClick = { overflowExpanded = false },
                                    modifier = Modifier.testTag("reader-cache-diagnostic"),
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(tween(READER_BARS_SLIDE_MILLIS)) { it } +
                fadeIn(tween(READER_BARS_FADE_MILLIS)),
            exit = slideOutVertically(tween(READER_BARS_SLIDE_MILLIS)) { it } +
                fadeOut(tween(READER_BARS_FADE_MILLIS)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().then(controlsHover).testTag("reader-bottom-bar"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ReaderChapterNavigator(
                        currentPage = if (state.pages.isEmpty()) 0 else state.selectedIndex + 1,
                        totalPages = state.pages.size,
                        isRtl = state.mode.isRightToLeft,
                        onPageSelected = onPageSelected,
                        onPreviousChapter = onPreviousChapter,
                        onNextChapter = onNextChapter,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReadingModeMenu(state.mode, onMode)
                        ScaleModeMenu(state.scaleMode, onScale)
                        val isWebtoon = state.mode == ReadingMode.WEBTOON
                        val cropActive = if (isWebtoon) settings.cropBordersWebtoon else settings.cropBorders
                        TextButton(
                            onClick = {
                                if (isWebtoon) {
                                    onCropBordersWebtoon(!settings.cropBordersWebtoon)
                                } else {
                                    onCropBorders(!settings.cropBorders)
                                }
                            },
                            modifier = Modifier.testTag("reader-crop-toggle"),
                        ) {
                            Text(strings.readerCropToggle(cropActive))
                        }
                        IconButton(
                            onClick = { onZoom(state.zoom - 0.25f) },
                            modifier = Modifier.testTag("reader-zoom-out"),
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = strings.text(UiText.ZoomOut))
                        }
                        TextButton(
                            onClick = { onZoom(1f) },
                            modifier = Modifier.testTag("reader-zoom-reset"),
                        ) {
                            Text("${(state.zoom * 100).toInt()}%")
                        }
                        IconButton(
                            onClick = { onZoom(state.zoom + 0.25f) },
                            modifier = Modifier.testTag("reader-zoom-in"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = strings.text(UiText.ZoomIn))
                        }
                        IconButton(onClick = onFullscreen, modifier = Modifier.testTag("reader-fullscreen")) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = strings.text(UiText.ReaderImmersive))
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("reader-settings")) {
                            Icon(Icons.Rounded.Settings, contentDescription = strings.settingsTitle)
                        }
                    }
                }
            }
        }
        ReaderChapterDrawer(
            isOpen = isChapterDrawerOpen,
            onDismissRequest = { isChapterDrawerOpen = false },
            title = title,
            chapters = chapterCatalog,
            currentChapterId = currentChapterId,
            onSelectChapter = {
                isChapterDrawerOpen = false
                onChapterSelected(it)
            },
        )
    }
}

private const val READER_BARS_SLIDE_MILLIS = 200
private const val READER_BARS_FADE_MILLIS = 150

@Composable
private fun ReadingModeMenu(current: ReadingMode, onSelected: (ReadingMode) -> Unit) {
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
private fun ScaleModeMenu(current: ScaleMode, onSelected: (ScaleMode) -> Unit) {
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
private fun ShortcutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ShortcutItemRow(keys: String, description: String) {
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

private const val MIB = 1024L * 1024L
