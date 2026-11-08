package mihon.desktop.ui.library

import mihon.desktop.download.DownloadStatus

data class ChapterDownloadProgress(val status: DownloadStatus, val progress: Float)

/**
 * Default for states without a live download queue. A shared instance keeps `MangaDetailUiState`
 * equality stable when the field is left at its default.
 */
internal val NoChapterDownloadProgress: (Long) -> ChapterDownloadProgress? = { null }
