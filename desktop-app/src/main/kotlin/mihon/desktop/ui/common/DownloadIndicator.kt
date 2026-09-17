package mihon.desktop.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DownloadStatus
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

internal fun normalizedDownloadProgress(status: DownloadStatus?, progress: Float): Float = when {
    status == DownloadStatus.COMPLETED -> 1f
    !progress.isFinite() -> 0f
    else -> progress.coerceIn(0f, 1f)
}

internal fun downloadIsIndeterminate(status: DownloadStatus?, progress: Float, isRunning: Boolean): Boolean =
    isRunning && (status == DownloadStatus.QUEUED || (status == DownloadStatus.DOWNLOADING && progress <= 0f))

@Composable
internal fun animatedDownloadProgress(status: DownloadStatus?, progress: Float, isRunning: Boolean): Float {
    val target = normalizedDownloadProgress(status, progress)
    val animation = remember { Animatable(target) }
    LaunchedEffect(status, target, isRunning) {
        if (status == DownloadStatus.DOWNLOADING && isRunning && target > animation.value) {
            animation.animateTo(target, ProgressIndicatorDefaults.ProgressAnimationSpec)
        } else {
            // Pause, retry/reset and terminal states must never continue a stale animation.
            animation.snapTo(target)
        }
    }
    return if (status == DownloadStatus.DOWNLOADING && isRunning && target >= animation.value) {
        animation.value
    } else {
        target
    }
}

@Composable
internal fun downloadStatusLabel(status: DownloadStatus?, isRunning: Boolean = true): String {
    val strings = LocalStrings.current
    return when {
        status == DownloadStatus.PAUSED ||
            (!isRunning && status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) ->
            strings.downloadsStatusPaused
        status == DownloadStatus.QUEUED -> strings.text(UiText.DownloadQueued)
        status == DownloadStatus.DOWNLOADING -> strings.downloadsStatusDownloading
        status == DownloadStatus.COMPLETED -> strings.downloadsStatusCompleted
        status == DownloadStatus.ERROR -> strings.downloadsStatusError
        else -> strings.text(UiText.NotDownloaded)
    }
}

/** Adapts Mihon's ChapterDownloadIndicator, including its filled progress circle and arrow. */
@Composable
fun DownloadIndicator(
    status: DownloadStatus?,
    progress: Float,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val target = normalizedDownloadProgress(status, progress)
    val animated = animatedDownloadProgress(status, target, isRunning)
    val indeterminate = downloadIsIndeterminate(status, target, isRunning)
    val paused = status == DownloadStatus.PAUSED ||
        (!isRunning && status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
    val label = downloadStatusLabel(status, isRunning)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.size(26.dp).clearAndSetSemantics {
            contentDescription = strings.downloadChapter
            stateDescription = label
            if (indeterminate) {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            } else if (status != null && status != DownloadStatus.ERROR) {
                progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        when {
            status == DownloadStatus.COMPLETED -> Icon(Icons.Rounded.CheckCircle, null, tint = tint)
            status == DownloadStatus.ERROR -> Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
            status == null -> Icon(Icons.Rounded.Download, null, tint = tint)
            else -> {
                if (indeterminate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp).padding(2.dp),
                        color = tint,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Butt,
                    )
                } else if (paused) {
                    CircularProgressIndicator(
                        progress = { target },
                        modifier = Modifier.size(26.dp).padding(2.dp),
                        color = tint,
                        strokeWidth = 2.dp,
                        trackColor = tint.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(26.dp).padding(2.dp),
                        color = tint,
                        strokeWidth = 13.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                    )
                }
                Icon(
                    imageVector = if (paused) Icons.Rounded.Pause else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (!paused && !indeterminate &&
                        animated >= 0.5f
                    ) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        tint
                    },
                )
            }
        }
    }
}
