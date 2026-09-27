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

const val UPDATES_SCREEN_TEST_TAG = "updates_screen"
const val UPDATES_CHECK_NOW_BUTTON_TEST_TAG = "updates_check_now"
const val UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG = "updates_open_upcoming"
const val UPDATES_ITEM_TEST_TAG_PREFIX = "updates_item_"
const val UPDATES_MARK_READ_TEST_TAG_PREFIX = "updates_mark_read_"
const val UPDATES_DOWNLOAD_TEST_TAG_PREFIX = "updates_download_"

@Composable
fun UpdatesScreen(
    updatedChapters: List<UpdatedChapterItem>,
    isUpdating: Boolean,
    lastResult: LibraryUpdateResult?,
    onCheckForUpdates: () -> Unit,
    onReadChapter: (chapterId: Long) -> Unit,
    onOpenUpcoming: () -> Unit = {},
    modifier: Modifier = Modifier,
    runState: mihon.desktop.library.update.LibraryUpdateRunState? = null,
    progress: mihon.desktop.library.update.LibraryUpdateProgress? = null,
    sourceNameFor: (Long) -> String = { "Source #$it" },
    onCancelUpdate: () -> Unit = {},
    onMarkChapterRead: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
    onDownloadChapter: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val groups = remember(updatedChapters, zone, today) { groupUpdatesByDay(updatedChapters, zone, today) }
    val dateFormatter = remember(strings) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(strings.locale)
    }
    val timeFormatter = remember(strings) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(strings.locale)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(UPDATES_SCREEN_TEST_TAG)
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = strings.updatesTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = when {
                    isUpdating -> strings.updatesChecking
                    lastResult != null -> {
                        if (lastResult.newChaptersFound > 0) {
                            mihon.desktop.i18n.recoveryText(
                                "${lastResult.mangaWithNewChapters} manga · ${lastResult.newChaptersFound} new chapters",
                                "${lastResult.mangaWithNewChapters} 部漫画 · ${lastResult.newChaptersFound} 个新章节",
                                "${lastResult.mangaWithNewChapters} 部漫畫 · ${lastResult.newChaptersFound} 個新章節",
                            )
                        } else {
                            strings.updatesEmptySubtitle
                        }
                    }
                    else -> strings.updatesEmptySubtitle
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onOpenUpcoming,
                    modifier = Modifier.testTag(UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(mihon.desktop.i18n.recoveryText("Upcoming", "更新日历", "更新日曆"))
                }

                Button(
                    onClick = if (isUpdating) onCancelUpdate else onCheckForUpdates,
                    modifier = Modifier.testTag(UPDATES_CHECK_NOW_BUTTON_TEST_TAG),
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mihon.desktop.i18n.recoveryText("Cancel update", "取消更新"))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.updatesCheckButton)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isUpdating && progress != null) {
            Text(
                listOfNotNull(
                    "${progress.currentIndex}/${progress.totalManga}",
                    progress.currentSourceId?.let(sourceNameFor),
                    progress.currentMangaTitle,
                ).joinToString(" · "),
                modifier = Modifier.padding(bottom = 12.dp).testTag("library-update-progress"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!isUpdating && runState != null) {
            val notice = when (runState.status) {
                mihon.desktop.library.update.LibraryUpdateStatus.FAILED -> {
                    val retry = runState.retryAfterEpochMillis.takeIf { it > System.currentTimeMillis() }
                        ?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
                    listOfNotNull(
                        mihon.desktop.i18n.recoveryText(
                            "Library update failed. You can check again.",
                            "书库更新失败，可点击“立即检查”重试。",
                            "書架更新失敗，可點擊「立即檢查」重試。",
                        ),
                        retry?.let { mihon.desktop.i18n.recoveryText("Retry after $it", "将在 $it 后重试", "將在 $it 後重試") },
                    ).joinToString("\n")
                }
                mihon.desktop.library.update.LibraryUpdateStatus.CANCELLED ->
                    mihon.desktop.i18n.recoveryText(
                        "Update cancelled. Finished updates are saved.",
                        "更新已取消，已完成的结果已保存。",
                        "更新已取消，已完成的結果已儲存。",
                    )
                else -> null
            }
            notice?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("library-update-recovery"),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        if (runState.status == mihon.desktop.library.update.LibraryUpdateStatus.FAILED) {
                            mihon.desktop.ui.common.FailureExplanation(
                                mihon.desktop.download.classifyDownloadFailure(runState.error),
                            )
                            mihon.desktop.ui.common.ErrorDetails(runState.error, "library-update-error")
                        }
                    }
                }
            }
        }

        if (updatedChapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.updatesEmptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.updatesEmptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.date}", contentType = { "header" }) {
                        val header = when (group.kind) {
                            UpdatesGroupKind.Today -> strings.text(UiText.UpdatesGroupToday)
                            UpdatesGroupKind.Yesterday -> strings.text(UiText.UpdatesGroupYesterday)
                            UpdatesGroupKind.Date -> dateFormatter.format(group.date)
                        }
                        Text(
                            text = header,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates-group-${group.date}"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(group.items, key = { it.chapterId }, contentType = { "update" }) { item ->
                        UpdateRow(
                            item = item,
                            timeFormatter = timeFormatter,
                            zone = zone,
                            onReadChapter = { onReadChapter(item.chapterId) },
                            onMarkRead = { onMarkChapterRead(item.mangaId, item.chapterId) },
                            onDownload = { onDownloadChapter(item.mangaId, item.chapterId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    item: UpdatedChapterItem,
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
