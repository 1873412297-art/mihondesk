package mihon.desktop.ui.updates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.onSecondaryClick
import mihon.desktop.updates.LibraryUpdateResult
import mihon.desktop.updates.UpdatedChapterItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun UpdateRow(
    item: UpdatedChapterItem,
    chapterCount: Int = 1,
    timeFormatter: DateTimeFormatter,
    zone: ZoneId,
    onReadChapter: () -> Unit,
    onMarkRead: () -> Unit,
    onDownload: () -> Unit,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var showContextMenu by remember(item.chapterId) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UPDATES_ITEM_TEST_TAG_PREFIX + item.chapterId)
            .onSecondaryClick { showContextMenu = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover(
                thumbnailUrl = item.mangaThumbnailUrl,
                mangaId = item.mangaId,
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.mangaTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.chapterName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapterCount > 1) {
                    Text(
                        text = strings.text(UiText.UpdatesMangaChapters, chapterCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("updates-manga-count-${item.mangaId}"),
                    )
                }
                if (item.dateFetch > 0L) {
                    val timeStr = remember(item.dateFetch, timeFormatter) {
                        try {
                            Instant.ofEpochMilli(item.dateFetch).atZone(zone).format(timeFormatter)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (timeStr != null) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.read) {
                    DesktopTooltipBoxForRow(
                        label = strings.text(UiText.ContextMarkAsRead),
                        tag = UPDATES_MARK_READ_TEST_TAG_PREFIX + item.chapterId,
                        onClick = onMarkRead,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                if (!item.downloaded) {
                    DesktopTooltipBoxForRow(
                        label = strings.text(UiText.ContextDownload),
                        tag = UPDATES_DOWNLOAD_TEST_TAG_PREFIX + item.chapterId,
                        onClick = onDownload,
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                FilledTonalButton(onClick = onReadChapter) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.updatesReadButton)
                }
            }
        }

        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
            if (!item.read) {
                DropdownMenuItem(
                    text = { Text(strings.text(UiText.ContextMarkAsRead)) },
                    onClick = {
                        showContextMenu = false
                        onMarkRead()
                    },
                    modifier = Modifier.testTag("updates-menu-mark-read-${item.chapterId}"),
                )
            }
            if (!item.downloaded) {
                DropdownMenuItem(
                    text = { Text(strings.text(UiText.ContextDownload)) },
                    onClick = {
                        showContextMenu = false
                        onDownload()
                    },
                    modifier = Modifier.testTag("updates-menu-download-${item.chapterId}"),
                )
            }
            DropdownMenuItem(
                text = { Text(strings.updatesReadButton) },
                onClick = {
                    showContextMenu = false
                    onReadChapter()
                },
                modifier = Modifier.testTag("updates-menu-read-${item.chapterId}"),
            )
        }
    }
}

@Composable
private fun DesktopTooltipBoxForRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    mihon.desktop.ui.common.DesktopTooltipBox(text = label) {
        IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
            icon()
        }
    }
}
