package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DownloadIndicator
import mihon.desktop.ui.common.animatedDownloadProgress
import mihon.desktop.ui.common.downloadIsIndeterminate
import mihon.desktop.ui.common.normalizedDownloadProgress

/*
 * Progress widgets read [progressOf] during their own composition, so a per-page progress tick
 * invalidates only these small scopes instead of the whole download card or queue list.
 */

@Composable
internal fun LiveDownloadIndicator(
    chapterId: Long,
    status: DownloadStatus,
    isRunning: Boolean,
    progressOf: (Long) -> DownloadProgressInfo,
    modifier: Modifier = Modifier,
) {
    DownloadIndicator(
        status = status,
        progress = progressOf(chapterId).progress,
        isRunning = isRunning,
        modifier = modifier,
    )
}

@Composable
internal fun DownloadProgressBar(
    chapterId: Long,
    status: DownloadStatus,
    isRunning: Boolean,
    progressOf: (Long) -> DownloadProgressInfo,
    modifier: Modifier = Modifier,
) {
    val target = normalizedDownloadProgress(status, progressOf(chapterId).progress)
    val progressModifier = modifier.testTag("download-progress-$chapterId")
    if (downloadIsIndeterminate(status, target, isRunning)) {
        LinearProgressIndicator(modifier = progressModifier)
    } else {
        val animated = animatedDownloadProgress(status, target, isRunning)
        LinearProgressIndicator(
            progress = { animated },
            modifier = progressModifier,
            color = if (status == DownloadStatus.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
internal fun DownloadStatusDetail(
    chapterId: Long,
    status: DownloadStatus,
    progressOf: (Long) -> DownloadProgressInfo,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val info = progressOf(chapterId)
    val text = when (status) {
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
            if (info.totalPages > 0) {
                strings.text(
                    UiText.DownloadProgress,
                    info.downloadedImages,
                    info.totalPages,
                    (info.progress * 100).toInt(),
                )
            } else {
                strings.text(UiText.PreparingDownload)
            }
        }
        DownloadStatus.COMPLETED -> strings.text(UiText.DownloadedPages, info.downloadedImages)
        DownloadStatus.ERROR -> if (info.totalPages > 0) {
            strings.text(
                UiText.DownloadProgress,
                info.downloadedImages,
                info.totalPages,
                (normalizedDownloadProgress(status, info.progress) * 100).toInt(),
            )
        } else {
            ""
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = if (status == DownloadStatus.ERROR) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
