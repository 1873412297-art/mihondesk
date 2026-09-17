package mihon.desktop.ui.library

import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus

data class ChapterDownloadProgress(val status: DownloadStatus, val progress: Float)

/** Reuses the application's live queue; progress updates do not query chapter files or the database. */
fun MangaDetailUiState.withDownloadProgress(
    queue: List<DesktopDownload>,
    isRunning: Boolean,
): MangaDetailUiState = copy(
    chapterDownloads = queue.asSequence().filter { it.mangaId == manga?.id }
        .associate { it.chapterId to ChapterDownloadProgress(it.status, it.progress) },
    downloadsRunning = isRunning,
)
