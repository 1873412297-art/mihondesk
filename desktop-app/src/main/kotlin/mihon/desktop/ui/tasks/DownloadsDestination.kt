package mihon.desktop.ui.tasks

import androidx.compose.runtime.Composable
import mihon.desktop.download.DesktopDownload
import mihon.desktop.ui.DownloadActions

/**
 * Reads the high-frequency download queue and speed here, inside their own composition scope, so a
 * progress tick recomposes the Downloads page instead of the whole shell.
 */
@Composable
internal fun DownloadsDestination(
    queue: () -> List<DesktopDownload>,
    speedBytesPerSec: () -> Double,
    isRunning: Boolean,
    progressOf: (Long) -> DownloadProgressInfo,
    recoveryMessage: String?,
    storageError: String?,
    actions: DownloadActions,
) {
    DownloadsScreen(
        queue = queue(),
        speedBytesPerSec = speedBytesPerSec(),
        isRunning = isRunning,
        progressOf = progressOf,
        onPauseAll = actions.onPauseAll,
        onResumeAll = actions.onResumeAll,
        onClearCompleted = actions.onClearCompleted,
        onCancel = actions.onCancel,
        onPause = actions.onPause,
        onResume = actions.onResume,
        onMoveUp = actions.onMoveUp,
        onMoveDown = actions.onMoveDown,
        onRetry = actions.onRetry,
        onRetryAllFailed = actions.onRetryAllFailed,
        onGroupAction = actions.onGroupAction,
        onReorder = actions.onReorder,
        onReadChapter = actions.onReadChapter,
        recoveryMessage = recoveryMessage,
        storageError = storageError,
    )
}
