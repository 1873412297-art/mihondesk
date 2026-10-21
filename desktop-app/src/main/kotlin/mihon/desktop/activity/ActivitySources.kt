package mihon.desktop.activity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.download.DownloadStatus
import mihon.desktop.library.update.LibraryUpdateProgress
import mihon.desktop.library.update.LibraryUpdateRunState
import mihon.desktop.library.update.LibraryUpdateScheduler
import mihon.desktop.library.update.LibraryUpdateStatus

const val DOWNLOADS_ACTIVITY_ID = "downloads"
const val LIBRARY_UPDATE_ACTIVITY_ID = "library-update"
const val SYNC_ACTIVITY_ID = "sync"

/** Aggregates the download queue into a single activity entry (or none when there is nothing to show). */
fun downloadsActivity(
    queue: List<DesktopDownload>,
    isRunning: Boolean,
    retryAll: () -> Unit,
): ActivityItem? {
    val pending = queue.filter { it.status != DownloadStatus.COMPLETED }
    if (pending.isEmpty()) return null
    val completed = queue.count { it.status == DownloadStatus.COMPLETED }
    val failed = queue.filter { it.status == DownloadStatus.ERROR }
    val active = queue.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val current = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING } ?: active.firstOrNull()
    return if (active.isNotEmpty() && (isRunning || failed.isEmpty())) {
        ActivityItem(
            id = DOWNLOADS_ACTIVITY_ID,
            kind = ActivityKind.DOWNLOAD,
            state = ActivityState.RUNNING,
            subject = current?.mangaTitle,
            current = completed,
            total = queue.size,
            progress = (
                queue.sumOf { if (it.status == DownloadStatus.COMPLETED) 1.0 else it.progress.toDouble() } /
                    queue.size
                )
                .toFloat(),
            failedCount = failed.size,
            retry = if (failed.isNotEmpty()) retryAll else null,
        )
    } else if (failed.isNotEmpty()) {
        ActivityItem(
            id = DOWNLOADS_ACTIVITY_ID,
            kind = ActivityKind.DOWNLOAD,
            state = ActivityState.FAILED,
            subject = failed.first().mangaTitle,
            current = completed,
            total = queue.size,
            failedCount = failed.size,
            failureReason = failed.first().error ?: failed.first().failureReason?.name,
            retry = retryAll,
        )
    } else {
        // Only paused chapters remain: nothing is running and nothing needs attention.
        null
    }
}

fun libraryUpdateActivity(
    isUpdating: Boolean,
    progress: LibraryUpdateProgress?,
    runState: LibraryUpdateRunState,
    retry: () -> Unit,
): ActivityItem? = when {
    isUpdating -> ActivityItem(
        id = LIBRARY_UPDATE_ACTIVITY_ID,
        kind = ActivityKind.LIBRARY_UPDATE,
        state = ActivityState.RUNNING,
        subject = progress?.currentMangaTitle,
        current = progress?.currentIndex,
        total = progress?.totalManga,
        progress = progress?.takeIf { it.totalManga > 0 }?.let {
            (it.currentIndex.toFloat() / it.totalManga).coerceIn(0f, 1f)
        },
        retry = retry,
    )
    runState.status == LibraryUpdateStatus.FAILED -> ActivityItem(
        id = LIBRARY_UPDATE_ACTIVITY_ID,
        kind = ActivityKind.LIBRARY_UPDATE,
        state = ActivityState.FAILED,
        failedCount = runState.failedMangaIds.size,
        failureReason = runState.error?.lineSequence()?.firstOrNull { it.isNotBlank() },
        updatedAt = runState.lastAttemptEpochMillis,
        retry = retry,
    )
    runState.status == LibraryUpdateStatus.COMPLETED -> ActivityItem(
        id = LIBRARY_UPDATE_ACTIVITY_ID,
        kind = ActivityKind.LIBRARY_UPDATE,
        state = ActivityState.SUCCEEDED,
        updatedAt = runState.lastCompletedEpochMillis,
        retry = retry,
    )
    else -> null
}

/** Mirrors the download queue and library-update state into [center] for the app's lifetime. */
fun bindActivitySources(
    center: ActivityCenter,
    scope: CoroutineScope,
    downloader: DesktopDownloader?,
    libraryUpdates: LibraryUpdateScheduler?,
) {
    if (downloader != null) {
        scope.launch {
            combine(downloader.queueState, downloader.isRunning) { queue, running ->
                downloadsActivity(queue, running, retryAll = downloader::retryAllFailed)
            }.collect { center.publish(it, DOWNLOADS_ACTIVITY_ID) }
        }
    }
    if (libraryUpdates != null) {
        val retry: () -> Unit = {
            scope.launch {
                try {
                    libraryUpdates.triggerUpdateNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The scheduler records the failure in its run state, which is mirrored below.
                }
            }
        }
        scope.launch {
            combine(libraryUpdates.isUpdating, libraryUpdates.currentProgress, libraryUpdates.runState) { u, p, s ->
                libraryUpdateActivity(u, p, s, retry)
            }.collect { center.publish(it, LIBRARY_UPDATE_ACTIVITY_ID) }
        }
    }
}
