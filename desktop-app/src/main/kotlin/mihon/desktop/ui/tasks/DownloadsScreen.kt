package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.classifyDownloadFailure
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DownloadIndicator
import mihon.desktop.ui.common.ErrorDetails
import mihon.desktop.ui.common.FailureExplanation
import mihon.desktop.ui.common.animatedDownloadProgress
import mihon.desktop.ui.common.downloadIsIndeterminate
import mihon.desktop.ui.common.downloadStatusLabel
import mihon.desktop.ui.common.normalizedDownloadProgress

const val DOWNLOADS_SCREEN_TEST_TAG = "downloads_screen"
const val DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG = "downloads_pause_all"
const val DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG = "downloads_resume_all"
const val DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG = "downloads_clear_completed"
const val DOWNLOAD_ITEM_TEST_TAG_PREFIX = "download_item_"
const val DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX = "download_read_"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    queue: List<DesktopDownload>,
    isRunning: Boolean,
    speedBytesPerSec: Double,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onCancel: (chapterId: Long) -> Unit,
    onRetry: (chapterId: Long) -> Unit,
    onReadChapter: (mangaId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier,
    recoveryMessage: String? = null,
    storageError: String? = null,
    onRetryAllFailed: () -> Unit = {},
) {
    val strings = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf<DownloadStatus?>(null) }
    val failedCount = queue.count { it.status == DownloadStatus.ERROR }
    val counts = queue.groupingBy { it.displayStatus(isRunning) }.eachCount()
    val visible = queue.filter { item ->
        (selectedStatus == null || item.displayStatus(isRunning) == selectedStatus) &&
            (
                item.mangaTitle.contains(query.trim(), ignoreCase = true) ||
                    item.chapterName.contains(query.trim(), ignoreCase = true)
                )
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(DOWNLOADS_SCREEN_TEST_TAG)
                .padding(16.dp),
        ) {
            // Top Header
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        text = strings.downloadsTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val speedText = formatSpeed(speedBytesPerSec)
                    val activeCount = queue.count {
                        it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.QUEUED
                    }
                    Text(
                        text = if (isRunning && speedBytesPerSec > 0) {
                            strings.downloadsActiveSpeed(activeCount, speedText)
                        } else {
                            strings.text(UiText.DownloadQueueCount, queue.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = onPauseAll,
                            modifier = Modifier.testTag(DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.downloadsPauseAll)
                        }
                    } else {
                        Button(
                            onClick = onResumeAll,
                            modifier = Modifier.testTag(DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG),
                            enabled = queue.any {
                                it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.downloadsResumeAll)
                        }
                    }

                    if (failedCount > 0) {
                        OutlinedButton(
                            onClick = onRetryAllFailed,
                            modifier = Modifier.testTag("downloads-retry-failed"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.text(UiText.RetryAllFailed, failedCount))
                        }
                    }

                    TextButton(
                        onClick = onClearCompleted,
                        modifier = Modifier.testTag(DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG),
                        enabled = queue.any { it.status == DownloadStatus.COMPLETED },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.downloadsClearCompleted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (queue.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(strings.text(UiText.SearchDownloads)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.text(UiText.ClearSearch))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("downloads-search"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("${strings.text(UiText.All)} (${queue.size})") },
                        modifier = Modifier.testTag("download-filter-ALL"),
                    )
                    DownloadStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text("${downloadStatusLabel(status, true)} (${counts[status] ?: 0})") },
                            modifier = Modifier.testTag("download-filter-$status"),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            listOfNotNull(storageError, recoveryMessage).distinct().forEach { message ->
                Surface(
                    color = if (message == storageError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("download-recovery-notice"),
                ) {
                    Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (queue.isEmpty()) {
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
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = strings.downloadsEmptyTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(strings.text(UiText.DownloadsNoMatches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        query = ""
                        selectedStatus = null
                    }) { Text(strings.text(UiText.ClearFilters)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.chapterId }) { item ->
                        DownloadCard(
                            download = item,
                            isRunning = isRunning,
                            onCancel = { onCancel(item.chapterId) },
                            onRetry = { onRetry(item.chapterId) },
                            onRead = { onReadChapter(item.mangaId, item.chapterId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    download: DesktopDownload,
    isRunning: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRead: () -> Unit,
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DOWNLOAD_ITEM_TEST_TAG_PREFIX + download.chapterId),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.mangaTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = download.chapterName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DownloadIndicator(
                        status = download.status,
                        progress = download.progress,
                        isRunning = isRunning,
                        modifier = Modifier.testTag("download-status-indicator-${download.chapterId}"),
                    )
                    StatusBadge(download.status, isRunning)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val animatedProgress = animatedDownloadProgress(download.status, download.progress, isRunning)
            val progressModifier = Modifier.fillMaxWidth().height(
                6.dp,
            ).testTag("download-progress-${download.chapterId}")
            if (downloadIsIndeterminate(
                    download.status,
                    normalizedDownloadProgress(download.status, download.progress),
                    isRunning,
                )
            ) {
                LinearProgressIndicator(modifier = progressModifier)
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = progressModifier,
                    color = if (download.status ==
                        DownloadStatus.ERROR
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (download.status == DownloadStatus.ERROR) {
                FailureExplanation(download.failureReason ?: classifyDownloadFailure(download.error))
                ErrorDetails(download.error, "download-error-${download.chapterId}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusDetail = when (download.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
                        if (download.totalPages > 0) {
                            strings.text(
                                UiText.DownloadProgress,
                                download.downloadedImages,
                                download.totalPages,
                                (
                                    download.progress *
                                        100
                                    ).toInt(),
                            )
                        } else {
                            strings.text(UiText.PreparingDownload)
                        }
                    }
                    DownloadStatus.COMPLETED -> strings.text(UiText.DownloadedPages, download.downloadedImages)
                    DownloadStatus.ERROR -> if (download.totalPages > 0) {
                        strings.text(
                            UiText.DownloadProgress,
                            download.downloadedImages,
                            download.totalPages,
                            (normalizedDownloadProgress(download.status, download.progress) * 100).toInt(),
                        )
                    } else {
                        ""
                    }
                }

                Text(
                    text = statusDetail,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (download.status ==
                        DownloadStatus.ERROR
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (download.status == DownloadStatus.COMPLETED) {
                        FilledTonalButton(
                            onClick = onRead,
                            modifier = Modifier.testTag(DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX + download.chapterId),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.downloadsRead)
                        }
                    }
                    if (download.status == DownloadStatus.ERROR) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("download-retry-${download.chapterId}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.downloadsRetry)
                        }
                    }
                    if (download.status != DownloadStatus.COMPLETED) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("download-cancel-${download.chapterId}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.downloadsCancel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus, isRunning: Boolean) {
    val label = downloadStatusLabel(status, isRunning)
    val color = when (effectiveDownloadStatus(status, isRunning)) {
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun DesktopDownload.displayStatus(isRunning: Boolean) = effectiveDownloadStatus(status, isRunning)

private fun effectiveDownloadStatus(status: DownloadStatus, isRunning: Boolean): DownloadStatus =
    if (!isRunning &&
        (status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING)
    ) {
        DownloadStatus.PAUSED
    } else {
        status
    }

private fun formatSpeed(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
        else -> String.format("%.0f B/s", bytesPerSec)
    }
}
