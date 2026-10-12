package mihon.desktop.ui.tasks

import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus

/** One manga's slice of the download queue, in queue order. */
data class DownloadGroup(
    val mangaId: Long,
    val mangaTitle: String,
    val items: List<DesktopDownload>,
) {
    val failedCount: Int get() = items.count { it.status == DownloadStatus.ERROR }
}

/** Groups downloads by manga, ordering groups by their first queued chapter so queue priority is kept. */
fun groupDownloadsByManga(items: List<DesktopDownload>): List<DownloadGroup> =
    items.groupBy { it.mangaId }.map { (mangaId, chapters) ->
        DownloadGroup(mangaId = mangaId, mangaTitle = chapters.first().mangaTitle, items = chapters)
    }

/** Badge on the Downloads navigation item: failures win (error colour), otherwise the pending count. */
data class DownloadsNavBadge(val count: Int, val failed: Boolean)

fun downloadsNavBadge(queue: List<DesktopDownload>): DownloadsNavBadge? {
    val failed = queue.count { it.status == DownloadStatus.ERROR }
    if (failed > 0) return DownloadsNavBadge(failed, failed = true)
    val pending = queue.count { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
    return if (pending > 0) DownloadsNavBadge(pending, failed = false) else null
}
