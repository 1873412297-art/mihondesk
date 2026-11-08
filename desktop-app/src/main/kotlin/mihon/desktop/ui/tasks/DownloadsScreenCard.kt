package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.classifyDownloadFailure
import mihon.desktop.ui.common.ErrorDetails
import mihon.desktop.ui.common.FailureExplanation
import mihon.desktop.ui.common.onSecondaryClick

@Composable
internal fun DownloadCard(
    download: DesktopDownload,
    progressOf: (Long) -> DownloadProgressInfo,
    isRunning: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRead: () -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSecondaryClick { showContextMenu = true },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DOWNLOAD_ITEM_TEST_TAG_PREFIX + download.chapterId),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DownloadCardHeader(
                    download = download,
                    isRunning = isRunning,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    progressOf = progressOf,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                )
                DownloadCardProgressSection(
                    download = download,
                    isRunning = isRunning,
                    progressOf = progressOf,
                )
                DownloadCardFooter(
                    download = download,
                    progressOf = progressOf,
                    onRead = onRead,
                    onResume = onResume,
                    onPause = onPause,
                    onRetry = onRetry,
                    onCancel = onCancel,
                )
            }
        }
        if (showContextMenu) {
            DownloadCardContextMenu(
                download = download,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onDismiss = { showContextMenu = false },
                onRead = onRead,
                onResume = onResume,
                onPause = onPause,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRetry = onRetry,
                onCancel = onCancel,
            )
        }
    }
}
