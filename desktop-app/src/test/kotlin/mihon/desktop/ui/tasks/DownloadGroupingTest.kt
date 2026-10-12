package mihon.desktop.ui.tasks

import io.kotest.matchers.shouldBe
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.NavItemBadge
import mihon.desktop.ui.navBadgeFor
import org.junit.jupiter.api.Test

class DownloadGroupingTest {

    @Test
    fun `groups follow the queue position of each manga's first chapter`() {
        val queue = listOf(
            download(chapterId = 1, mangaId = 20, title = "B"),
            download(chapterId = 2, mangaId = 10, title = "A"),
            download(chapterId = 3, mangaId = 20, title = "B", status = DownloadStatus.ERROR),
        )

        val groups = groupDownloadsByManga(queue)

        groups.map { it.mangaId } shouldBe listOf(20L, 10L)
        groups.first().items.map { it.chapterId } shouldBe listOf(1L, 3L)
        groups.first().mangaTitle shouldBe "B"
        groups.first().failedCount shouldBe 1
        groupDownloadsByManga(emptyList()) shouldBe emptyList()
    }

    @Test
    fun `nav badge prefers failures, then pending work, and hides when idle`() {
        downloadsNavBadge(
            listOf(
                download(1, status = DownloadStatus.DOWNLOADING),
                download(2, status = DownloadStatus.ERROR),
            ),
        ) shouldBe DownloadsNavBadge(1, failed = true)
        downloadsNavBadge(
            listOf(
                download(1, status = DownloadStatus.DOWNLOADING),
                download(2, status = DownloadStatus.QUEUED),
                download(3, status = DownloadStatus.COMPLETED),
                download(4, status = DownloadStatus.PAUSED),
            ),
        ) shouldBe DownloadsNavBadge(2, failed = false)
        downloadsNavBadge(listOf(download(1, status = DownloadStatus.COMPLETED))) shouldBe null
        downloadsNavBadge(emptyList()) shouldBe null
    }

    @Test
    fun `navigation badges map updates and downloads to their own colours`() {
        navBadgeFor(DesktopDestination.Updates, 3, null) shouldBe
            NavItemBadge(3, error = true, testTag = "updates-unread-badge")
        navBadgeFor(DesktopDestination.Updates, 0, null) shouldBe null
        navBadgeFor(DesktopDestination.Downloads, 0, DownloadsNavBadge(2, failed = false)) shouldBe
            NavItemBadge(2, error = false, testTag = "downloads-nav-badge")
        navBadgeFor(DesktopDestination.Downloads, 5, null) shouldBe null
        navBadgeFor(DesktopDestination.Library, 5, DownloadsNavBadge(1, failed = true)) shouldBe null
    }

    private fun download(
        chapterId: Long,
        mangaId: Long = 1,
        title: String = "Manga",
        status: DownloadStatus = DownloadStatus.QUEUED,
    ) = DesktopDownload(
        chapterId = chapterId,
        mangaId = mangaId,
        sourceId = 1,
        mangaTitle = title,
        chapterName = "Chapter $chapterId",
        chapterUrl = "/c/$chapterId",
        status = status,
        enqueuedAt = 0,
    )
}
