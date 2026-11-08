package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.ui.library.ChapterDownloadProgress
import mihon.desktop.ui.tasks.DownloadProgressInfo
import mihon.desktop.ui.tasks.DownloadsNavBadge
import mihon.desktop.ui.tasks.downloadsNavBadge

/**
 * Live view of the download queue for the shell. High-frequency values are exposed as providers so
 * only the leaf composables that read them are invalidated; `badge` and `isRunning` change rarely
 * and may be read during shell composition.
 */
internal class DownloadLiveState(
    val queue: () -> List<DesktopDownload>,
    val speedBytesPerSec: () -> Double,
    val badge: State<DownloadsNavBadge?>,
    val isRunning: State<Boolean>,
    val progressOf: (Long) -> DownloadProgressInfo,
    val chapterProgressOf: (Long) -> ChapterDownloadProgress?,
)

/**
 * Derives queue/progress maps off the UI thread and keeps them for the app session, so the data
 * does not live in a composable and queue ticks never invalidate the shell itself.
 */
internal class DownloadLivePresenter(
    private val downloader: DesktopDownloader?,
    scope: CoroutineScope,
) {
    private val queueState = mutableStateOf<List<DesktopDownload>>(emptyList())
    private val speedState = mutableStateOf(0.0)
    private val badgeState = mutableStateOf<DownloadsNavBadge?>(null)
    private val progressState = mutableStateOf<Map<Long, DownloadProgressInfo>>(emptyMap())
    private val chapterState = mutableStateOf<Map<Long, ChapterDownloadProgress>>(emptyMap())

    init {
        downloader?.queueState?.let { queueFlow ->
            scope.launch(Dispatchers.Default) {
                queueFlow.collect { queue ->
                    queueState.value = queue
                    badgeState.value = downloadsNavBadge(queue)
                    progressState.value = queue.associate { item ->
                        item.chapterId to DownloadProgressInfo(item.progress, item.downloadedImages, item.totalPages)
                    }
                    chapterState.value = queue.associate { item ->
                        item.chapterId to ChapterDownloadProgress(item.status, item.progress)
                    }
                }
            }
        }
        downloader?.speedBytesPerSec?.let { speedFlow ->
            scope.launch(Dispatchers.Default) {
                speedFlow.collect { speedState.value = it }
            }
        }
    }

    @Composable
    fun liveState(): DownloadLiveState {
        val running = downloader?.isRunning?.collectAsState() ?: remember { mutableStateOf(false) }
        return DownloadLiveState(
            queue = { queueState.value },
            speedBytesPerSec = { speedState.value },
            badge = badgeState,
            isRunning = running,
            progressOf = { chapterId -> progressState.value[chapterId] ?: DownloadProgressInfo(0f, 0, 0) },
            chapterProgressOf = { chapterId -> chapterState.value[chapterId] },
        )
    }
}
