package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.chapterCountLabel
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.DownloadIndicator
import mihon.desktop.ui.common.onSecondaryClick

@Composable
internal fun VolumeHeaderRow(item: ChapterListItem.VolumeHeader) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    val title = item.volume?.let { strings.text(mihon.desktop.i18n.UiText.ChapterVolumeLabel, it) }
        ?: strings.text(mihon.desktop.i18n.UiText.ChapterVolumeNone)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .testTag("chapter-volume-header-${item.volume ?: "none"}"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(
            text = strings.chapterCountLabel(item.chapterCount.toLong()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun chapterProgressLabel(
    chapter: LibraryChapter,
    strings: mihon.desktop.i18n.DesktopStrings,
    dateFormatter: java.time.format.DateTimeFormatter,
): String = buildList {
    add(if (chapter.read) strings.filterRead else strings.filterUnread)
    if (chapter.bookmark) add(strings.filterBookmarked)
    if (chapter.dateUpload > 0L) {
        val dateText = try {
            java.time.Instant.ofEpochMilli(chapter.dateUpload)
                .atZone(java.time.ZoneId.systemDefault())
                .format(dateFormatter)
        } catch (_: Exception) {
            null
        }
        if (!dateText.isNullOrBlank()) add(dateText)
    }
    if (chapter.lastPageRead > 0) add(strings.browsePageNumber(chapter.lastPageRead.toInt()))
    chapter.scanlator?.takeIf(String::isNotBlank)?.let { add(it) }
}.joinToString(" · ")

internal fun chapterActionLabel(
    chapter: LibraryChapter,
    availability: ChapterReaderAvailability,
    strings: mihon.desktop.i18n.DesktopStrings,
): String = when (availability) {
    ChapterReaderAvailability.Readable -> if (chapter.lastPageRead > 0L) {
        strings.text(UiText.ContinuePage, chapter.lastPageRead)
    } else {
        strings.text(UiText.ReadChapter)
    }
    ChapterReaderAvailability.MissingLocalContent -> strings.text(UiText.LocateContent)
    ChapterReaderAvailability.RemoteOnly -> strings.text(UiText.SourceSupportRequired)
}

@Composable
internal fun ChapterRow(
    chapter: LibraryChapter,
    displayName: String,
    availability: ChapterReaderAvailability,
    isDownloaded: Boolean,
    downloadOf: (Long) -> ChapterDownloadProgress? = NoChapterDownloadProgress,
    downloadsRunning: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onRangeSelect: () -> Unit = onToggleSelection,
    onReadChapter: (Long) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    onMarkPreviousRead: () -> Unit,
    onDownloadChapter: () -> Unit,
    onDeleteDownload: () -> Unit,
    onOpenInWebView: (() -> Unit)? = null,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    val download = downloadOf(chapter.id)
    var menuExpanded by remember { mutableStateOf(false) }
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val shiftHeld: () -> Boolean = { windowInfo.keyboardModifiers.isShiftPressed }
    val chapterDateFormatter = remember(strings) {
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(strings.locale)
    }
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("chapter-row")
            .clip(MaterialTheme.shapes.medium)
            .onSecondaryClick { menuExpanded = true }
            .clickable {
                when {
                    shiftHeld() -> onRangeSelect()
                    isSelectionMode -> onToggleSelection()
                    else -> onReadChapter(chapter.id)
                }
            }
            .semantics(mergeDescendants = true) {},
        color = rowBackground,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (shiftHeld()) onRangeSelect() else onToggleSelection() },
                    modifier = Modifier.testTag("chapter-select-${chapter.id}"),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chapter.bookmark) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (chapter.read) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Text(
                    chapterProgressLabel(chapter, strings, chapterDateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DesktopTooltipBox(text = if (chapter.bookmark) strings.removeBookmark else strings.bookmarkChapter) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.testTag("chapter-bookmark-button"),
                ) {
                    val color = if (chapter.bookmark) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        imageVector = if (chapter.bookmark) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (chapter.bookmark) strings.removeBookmark else strings.bookmarkChapter,
                        tint = color,
                    )
                }
            }

            DesktopTooltipBox(text = if (isDownloaded) strings.deleteDownload else strings.downloadChapter) {
                IconButton(
                    onClick = {
                        if (isDownloaded) onDeleteDownload() else onDownloadChapter()
                    },
                    modifier = Modifier.testTag("chapter-download-button"),
                ) {
                    DownloadIndicator(
                        status = download?.status ?: if (isDownloaded) DownloadStatus.COMPLETED else null,
                        progress = download?.progress ?: if (isDownloaded) 1f else 0f,
                        isRunning = downloadsRunning,
                        modifier = Modifier.testTag("chapter-download-indicator-${chapter.id}"),
                    )
                }
            }

            Box {
                DesktopTooltipBox(text = strings.text(UiText.MoreOptions)) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("chapter-more-button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = strings.text(UiText.MoreOptions),
                        )
                    }
                }
                if (menuExpanded) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (chapter.read) strings.markAsUnread else strings.markAsRead) },
                            onClick = {
                                onToggleRead()
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.markPreviousAsRead) },
                            onClick = {
                                onMarkPreviousRead()
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (chapter.bookmark) strings.removeBookmark else strings.bookmarkChapter) },
                            onClick = {
                                onToggleBookmark()
                                menuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isDownloaded) strings.deleteDownload else strings.downloadChapter) },
                            onClick = {
                                if (isDownloaded) onDeleteDownload() else onDownloadChapter()
                                menuExpanded = false
                            },
                        )
                        if (onOpenInWebView != null) {
                            DropdownMenuItem(
                                text = { Text(strings.chapterOpenInWebView) },
                                onClick = {
                                    onOpenInWebView.invoke()
                                    menuExpanded = false
                                },
                                modifier = Modifier.testTag("chapter-open-webview"),
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = { onReadChapter(chapter.id) },
                enabled = availability is ChapterReaderAvailability.Readable,
                modifier = Modifier.testTag("chapter-reader-action"),
            ) {
                Text(chapterActionLabel(chapter, availability, strings))
            }
        }
    }
}

@Composable
internal fun MissingChapterIndicator(count: Int) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .testTag("missing-chapter-indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = strings.mangaDetailMissingChapters(count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
