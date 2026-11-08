package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.classifyDownloadFailure
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.ErrorDetails
import mihon.desktop.ui.common.FailureExplanation

@Composable
internal fun DownloadCardHeader(
    download: DesktopDownload,
    isRunning: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    progressOf: (Long) -> DownloadProgressInfo,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // Read title and chapter as one item instead of two unrelated text nodes.
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DownloadMoveButtons(download, canMoveUp, canMoveDown, onMoveUp, onMoveDown)
            Spacer(modifier = Modifier.width(4.dp))
            LiveDownloadIndicator(
                chapterId = download.chapterId,
                status = download.status,
                isRunning = isRunning,
                progressOf = progressOf,
                modifier = Modifier.testTag("download-status-indicator-${download.chapterId}"),
            )
            StatusBadge(download.status, isRunning)
        }
    }
}

@Composable
private fun DownloadMoveButtons(
    download: DesktopDownload,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val strings = LocalStrings.current
    DesktopTooltipBox(tooltip = strings.text(UiText.DownloadMoveUp)) {
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier
                .size(28.dp)
                .testTag("download-move-up-${download.chapterId}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = strings.text(UiText.DownloadMoveUp),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    DesktopTooltipBox(tooltip = strings.text(UiText.DownloadMoveDown)) {
        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier
                .size(28.dp)
                .testTag("download-move-down-${download.chapterId}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = strings.text(UiText.DownloadMoveDown),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun DownloadCardFooter(
    download: DesktopDownload,
    progressOf: (Long) -> DownloadProgressInfo,
    onRead: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadStatusDetail(
            chapterId = download.chapterId,
            status = download.status,
            progressOf = progressOf,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        DownloadActionButtons(download, onRead, onResume, onPause, onRetry, onCancel)
    }
}

@Composable
private fun DownloadActionButtons(
    download: DesktopDownload,
    onRead: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (download.status) {
            DownloadStatus.COMPLETED -> DownloadReadButton(download.chapterId, onRead)
            DownloadStatus.PAUSED -> DownloadTextAction(
                icon = Icons.Rounded.PlayArrow,
                label = strings.text(UiText.DownloadResumeItem),
                tooltip = strings.text(UiText.DownloadResumeItem),
                tag = "download-resume-${download.chapterId}",
                onClick = onResume,
            )
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadTextAction(
                icon = Icons.Rounded.Pause,
                label = strings.text(UiText.DownloadPauseItem),
                tooltip = strings.text(UiText.DownloadPauseItem),
                tag = "download-pause-${download.chapterId}",
                onClick = onPause,
            )
            DownloadStatus.ERROR -> DownloadTextAction(
                icon = Icons.Rounded.Refresh,
                label = strings.downloadsRetry,
                tooltip = strings.downloadsRetry,
                tag = "download-retry-${download.chapterId}",
                onClick = onRetry,
            )
            else -> Unit
        }
        if (download.status != DownloadStatus.COMPLETED) {
            DownloadTextAction(
                icon = Icons.Rounded.Close,
                label = strings.downloadsCancel,
                tooltip = strings.downloadsCancel,
                tag = "download-cancel-${download.chapterId}",
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun DownloadReadButton(chapterId: Long, onRead: () -> Unit) {
    val strings = LocalStrings.current
    DesktopTooltipBox(tooltip = strings.downloadsRead) {
        FilledTonalButton(
            onClick = onRead,
            modifier = Modifier.testTag(DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX + chapterId),
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
}

@Composable
private fun DownloadTextAction(
    icon: ImageVector,
    label: String,
    tooltip: String,
    tag: String,
    onClick: () -> Unit,
) {
    DesktopTooltipBox(tooltip = tooltip) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.testTag(tag),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(label)
        }
    }
}

@Composable
internal fun DownloadCardContextMenu(
    download: DesktopDownload,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        when (download.status) {
            DownloadStatus.COMPLETED -> DownloadMenuItem(strings.downloadsRead, Icons.Rounded.PlayArrow) {
                onDismiss()
                onRead()
            }
            DownloadStatus.PAUSED -> DownloadMenuItem(
                strings.text(UiText.DownloadResumeItem),
                Icons.Rounded.PlayArrow,
            ) {
                onDismiss()
                onResume()
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadMenuItem(
                strings.text(UiText.DownloadPauseItem),
                Icons.Rounded.Pause,
            ) {
                onDismiss()
                onPause()
            }
            DownloadStatus.ERROR -> DownloadMenuItem(strings.downloadsRetry, Icons.Rounded.Refresh) {
                onDismiss()
                onRetry()
            }
            else -> Unit
        }
        if (canMoveUp) {
            DownloadMenuItem(strings.text(UiText.DownloadMoveUp), Icons.Rounded.KeyboardArrowUp) {
                onDismiss()
                onMoveUp()
            }
        }
        if (canMoveDown) {
            DownloadMenuItem(strings.text(UiText.DownloadMoveDown), Icons.Rounded.KeyboardArrowDown) {
                onDismiss()
                onMoveDown()
            }
        }
        DownloadMenuItem(strings.downloadsCancel, Icons.Rounded.Close) {
            onDismiss()
            onCancel()
        }
    }
}

@Composable
private fun DownloadMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
internal fun DownloadCardProgressSection(
    download: DesktopDownload,
    isRunning: Boolean,
    progressOf: (Long) -> DownloadProgressInfo,
) {
    Spacer(modifier = Modifier.height(8.dp))
    DownloadProgressBar(
        chapterId = download.chapterId,
        status = download.status,
        isRunning = isRunning,
        progressOf = progressOf,
        modifier = Modifier.fillMaxWidth().height(6.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    if (download.status == DownloadStatus.ERROR) {
        FailureExplanation(download.failureReason ?: classifyDownloadFailure(download.error))
        ErrorDetails(download.error, "download-error-${download.chapterId}")
    }
}
