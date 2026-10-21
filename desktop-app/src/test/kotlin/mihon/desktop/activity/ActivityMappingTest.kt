package mihon.desktop.activity

import io.kotest.matchers.shouldBe
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadFailureReason
import mihon.desktop.download.DownloadStatus
import mihon.desktop.library.update.LibraryUpdateProgress
import mihon.desktop.library.update.LibraryUpdateRunState
import mihon.desktop.library.update.LibraryUpdateStatus
import org.junit.jupiter.api.Test

class ActivityMappingTest {
    private fun dl(id: Long, status: DownloadStatus, progress: Float = 0f, error: String? = null) = DesktopDownload(
        chapterId = id,
        mangaId = 1,
        sourceId = 1,
        mangaTitle = "M",
        chapterName = "C$id",
        chapterUrl = "/c/$id",
        status = status,
        progress = progress,
        error = error,
        failureReason = if (error != null) DownloadFailureReason.TIMEOUT else null,
    )

    @Test
    fun `empty or fully completed queue publishes nothing`() {
        downloadsActivity(emptyList(), isRunning = false, retryAll = {}) shouldBe null
        downloadsActivity(listOf(dl(1, DownloadStatus.COMPLETED)), isRunning = false, retryAll = {}) shouldBe null
    }

    @Test
    fun `active queue reports chapter counts and averaged progress`() {
        val item = downloadsActivity(
            listOf(
                dl(1, DownloadStatus.COMPLETED, 1f),
                dl(2, DownloadStatus.DOWNLOADING, 0.5f),
                dl(3, DownloadStatus.QUEUED),
            ),
            isRunning = true,
            retryAll = {},
        )!!
        item.state shouldBe ActivityState.RUNNING
        item.current shouldBe 1
        item.total shouldBe 3
        item.progress shouldBe 0.5f
    }

    @Test
    fun `failed chapters surface the reason and a retry-all action`() {
        var retried = false
        val item = downloadsActivity(
            listOf(dl(1, DownloadStatus.ERROR, error = "timeout"), dl(2, DownloadStatus.COMPLETED, 1f)),
            isRunning = false,
            retryAll = { retried = true },
        )!!
        item.state shouldBe ActivityState.FAILED
        item.failedCount shouldBe 1
        item.failureReason shouldBe "timeout"
        item.retry!!.invoke()
        retried shouldBe true
    }

    @Test
    fun `library update maps progress, failure and idle`() {
        val running = libraryUpdateActivity(
            isUpdating = true,
            progress = LibraryUpdateProgress("One Piece", currentIndex = 3, totalManga = 10),
            runState = LibraryUpdateRunState(status = LibraryUpdateStatus.RUNNING),
            retry = {},
        )!!
        running.state shouldBe ActivityState.RUNNING
        running.subject shouldBe "One Piece"
        running.progress shouldBe 0.3f

        val failed = libraryUpdateActivity(
            isUpdating = false,
            progress = null,
            runState = LibraryUpdateRunState(
                status = LibraryUpdateStatus.FAILED,
                failedMangaIds = setOf(1, 2),
                error = "HTTP 503",
                lastAttemptEpochMillis = 5,
            ),
            retry = {},
        )!!
        failed.state shouldBe ActivityState.FAILED
        failed.failedCount shouldBe 2
        failed.failureReason shouldBe "HTTP 503"

        libraryUpdateActivity(false, null, LibraryUpdateRunState(), retry = {}) shouldBe null
    }
}
