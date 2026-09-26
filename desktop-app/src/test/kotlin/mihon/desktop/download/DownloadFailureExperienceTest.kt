package mihon.desktop.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.SourceHttpException
import mihon.extension.ipc.NetworkFailure
import mihon.extension.ipc.NetworkFailureKind
import mihon.extension.ipc.NetworkFailureProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadFailureExperienceTest {
    private fun item(
        id: Long,
        status: DownloadStatus,
    ) = DesktopDownload(id, 1, 2, "Manga", "Chapter $id", "/$id", status = status)

    @Test
    fun `reported legacy errors distinguish TLS host and domain failures`() {
        classifyDownloadFailure(
            "1 page(s) failed; page 3: mihon.extension.ipc.IpcException: Brokered HTTP request failed: TLS: Remote host terminated the handshake",
        ) shouldBe
            DownloadFailureReason.TLS
        classifyDownloadFailure("No isolated host registered for source 7698513740234984368") shouldBe
            DownloadFailureReason.SOURCE_UNAVAILABLE
        classifyDownloadFailure("扩展包文件缺失：D:\\appdata\\ext.mext，请重新安装该扩展") shouldBe
            DownloadFailureReason.EXTENSION_MISSING
        classifyDownloadFailure(
            "Extension package file missing: D:\\appdata\\ext.mext, please reinstall the extension",
        ) shouldBe
            DownloadFailureReason.EXTENSION_MISSING
        classifyDownloadFailure(
            "Brokered HTTP request failed: Access denied: domain 'i4.nhentaimg.com' is not declared",
        ) shouldBe
            DownloadFailureReason.DOMAIN_DENIED
        classifyDownloadFailure("Unexpected parser response") shouldBe DownloadFailureReason.UNKNOWN
    }

    @Test
    fun `structured failures take precedence over misleading diagnostic text`() {
        val brokerError = object : IOException("TLS: wrapper text"), NetworkFailureProvider {
            override val networkFailure = NetworkFailure(NetworkFailureKind.DOMAIN_DENIED, 0, "image.example")
        }
        classifyDownloadFailure(IOException("1 page failed", brokerError)) shouldBe DownloadFailureReason.DOMAIN_DENIED
        classifyDownloadFailure(IOException("1 page failed", SSLHandshakeException("handshake"))) shouldBe
            DownloadFailureReason.TLS
        classifyDownloadFailure(SourceHttpException(429)) shouldBe DownloadFailureReason.RATE_LIMITED
        classifyDownloadFailure(SourceHttpException(403, kind = NetworkFailureKind.SITE_BLOCKED)) shouldBe
            DownloadFailureReason.SITE_BLOCKED
    }

    @Test
    fun `legacy queue remains readable and typed failure survives persistence`(@TempDir dir: Path) {
        val raw = """{"chapterId":1,"mangaId":1,"sourceId":2,"mangaTitle":"Manga",
            "chapterName":"Chapter","chapterUrl":"/1","status":"ERROR","error":"Insufficient disk space"}"""
        val legacy = Json.decodeFromString<DesktopDownload>(raw)
        legacy.failureReason shouldBe null
        classifyDownloadFailure(legacy.error) shouldBe DownloadFailureReason.STORAGE_FULL
        val store = DownloadStore(dir.resolve("queue.json"))
        store.save(listOf(legacy.copy(failureReason = DownloadFailureReason.STORAGE_FULL)))
        store.restore().single().failureReason shouldBe DownloadFailureReason.STORAGE_FULL
    }

    @Test
    fun `batch retry preserves ready pages paused chapters and completed files`(@TempDir dir: Path) = runTest {
        val store = DownloadStore(dir.resolve("queue.json"))
        val disk = DownloadDiskProvider(dir.resolve("pages"))
        disk.savePage(disk.getChapterDir(2, "Manga", "Chapter 4", 1, 4), 0, validDownloadImage())
        val ready = DownloadPage(0, "/ready", status = PageStatus.READY, progress = 1f, bytesWritten = 123)
        val failed = item(1, DownloadStatus.ERROR).copy(
            error = "TLS:",
            failureReason = DownloadFailureReason.TLS,
            pages = listOf(ready, DownloadPage(1, "/bad", status = PageStatus.ERROR, error = "TLS:")),
        )
        store.save(
            listOf(
                failed,
                item(2, DownloadStatus.ERROR),
                item(3, DownloadStatus.QUEUED),
                item(4, DownloadStatus.COMPLETED).copy(pages = listOf(ready)),
            ),
        )
        DesktopNetworkHelper().use { network ->
            val downloader = DesktopDownloader(store, disk, network, scope = backgroundScope)
            try {
                downloader.pause()
                val before = downloader.queueState.value
                before[2].status shouldBe DownloadStatus.PAUSED
                downloader.retryAllFailed()
                val queue = downloader.queueState.value
                queue.take(2).map { it.status } shouldBe listOf(DownloadStatus.QUEUED, DownloadStatus.QUEUED)
                queue.first().pages.first() shouldBe ready
                queue.first().pages[1].status shouldBe PageStatus.QUEUE
                queue.first().pages[1].error shouldBe null
                queue.first().error shouldBe null
                queue.first().failureReason shouldBe null
                queue.drop(2) shouldBe before.drop(2)
                store.restore() shouldBe queue
            } finally {
                downloader.shutdown()
            }
        }
    }

    @Test
    fun `retry without matching failures does not start queued work or write the store`(@TempDir dir: Path) = runTest {
        val file = dir.resolve("queue.json")
        val store = DownloadStore(file)
        store.save(listOf(item(1, DownloadStatus.QUEUED)))
        val snapshot = Files.readString(file)
        DesktopNetworkHelper().use { network ->
            val downloader =
                DesktopDownloader(store, DownloadDiskProvider(dir.resolve("pages")), network, scope = backgroundScope)
            try {
                downloader.retry(999)
                downloader.retryAllFailed()
                downloader.isRunning.value shouldBe false
                Files.readString(file) shouldBe snapshot
                Files.exists(file.resolveSibling("queue.json.bak")) shouldBe false
            } finally {
                downloader.shutdown()
            }
        }
    }
}
