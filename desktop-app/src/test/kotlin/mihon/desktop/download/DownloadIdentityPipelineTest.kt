package mihon.desktop.download

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.extension.source.model.Page
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class DownloadIdentityPipelineTest {
    @Test
    fun `same names download concurrently and remain distinct after queue clearing renames and deletion`(
        @TempDir temp: Path,
    ): Unit = runBlocking {
        DesktopLibraryDatabaseFactory.open(temp.resolve("library.db")).use { repository ->
            val first = repository.insertManga(MangaRecord(sourceId = 42, url = "/a", title = "Same:title"))
            val second = repository.insertManga(MangaRecord(sourceId = 42, url = "/b", title = "Same?title"))
            val third = repository.insertManga(MangaRecord(sourceId = 42, url = "/c", title = "Same:title"))
            val ids = listOf(first, first, second, third).mapIndexed { index, mangaId ->
                repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/$index", name = "Chapter"))
            }
            val images = ids.mapIndexed { index, id ->
                val image = BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB)
                image.setRGB(1, 1, 0xFF0000 shr (index * 8))
                id to ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()
            }.toMap()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            ids.forEach { id ->
                server.createContext("/$id") { exchange ->
                    val bytes = images.getValue(id)
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            server.start()
            val network = DesktopNetworkHelper()
            val disk = DownloadDiskProvider(temp.resolve("downloads"))
            val downloader = DesktopDownloader(
                DownloadStore(temp.resolve("queue.json")),
                disk,
                network,
                mutationPort = repository,
                downloadParallelism = { 3 },
                sourceParallelism = { 3 },
                pageListFetcher = { _, url ->
                    listOf(Page(0, imageUrl = "http://127.0.0.1:${server.address.port}/${ids[url.drop(1).toInt()]}"))
                },
            )
            try {
                for (manga in listOf(first, second, third)) {
                    downloader.enqueue(
                        requireNotNull(repository.mangaSnapshot(manga)),
                        repository.chapterSnapshot(manga),
                        false,
                    )
                }
                downloader.start()
                withTimeout(10_000) { while (downloader.isRunning.value) delay(10) }
                downloader.queueState.value.map { it.status }.toSet() shouldBe setOf(DownloadStatus.COMPLETED)
                val paths = ids.associateWith { id ->
                    requireNotNull(repository.chapterAsset(id)).let { it.storageRoot.resolve(it.relativePath) }
                }
                paths.values.toSet().size shouldBe 4
                paths.forEach { (id, path) ->
                    Files.readAllBytes(path.resolve("001.jpg")).toList() shouldBe
                        images.getValue(id).toList()
                }
                // The second same-name item must not select the first queue entry for deletion.
                downloader.deleteDownloadedChapter(42, first, "Same:title", ids[1], "Chapter") shouldBe true
                Files.exists(paths.getValue(ids[0])) shouldBe true
                Files.exists(paths.getValue(ids[1])) shouldBe false
                Files.exists(paths.getValue(ids[2])) shouldBe true
                downloader.clearCompleted()
                repository.updateManga(requireNotNull(repository.findManga(42, "/a")).copy(title = "Renamed"))
                val remaining = repository.chapterSnapshot(first).first {
                    it.id == ids[0]
                }.copy(name = "Renamed chapter")
                downloader.isChapterDownloaded(42, "Renamed", remaining.id, remaining.name, first) shouldBe true
                downloader.enqueue(42, first, "Renamed", listOf(remaining), false)
                downloader.queueState.value shouldBe emptyList()
                downloader.deleteDownloadedChapter(42, first, "Renamed", remaining.id, remaining.name) shouldBe true
                Files.exists(paths.getValue(ids[2])) shouldBe true
            } finally {
                downloader.shutdown()
                network.close()
                server.stop(0)
            }
        }
    }

    @Test
    fun `registered legacy chapter survives queue clearing root change and rename`(@TempDir temp: Path) {
        DesktopLibraryDatabaseFactory.open(temp.resolve("library.db")).use { repository ->
            val manga = repository.insertManga(MangaRecord(sourceId = 42, url = "/old", title = "Old title"))
            val chapter = repository.insertChapter(ChapterRecord(mangaId = manga, url = "/1", name = "Old chapter"))
            val oldRoot = temp.resolve("old-custom-root/42/Old title")
            val oldChapter = oldRoot.resolve("Old chapter")
            DownloadDiskProvider(temp.resolve("old-custom-root")).savePage(oldChapter, 0, validDownloadImage())
            repository.insertLocalManga(LocalMangaRecord(manga, oldRoot.toString(), "", 0))
            repository.insertLocalChapter(LocalChapterRecord(chapter, "Old chapter", "DIRECTORY", 100, 0))
            val disk = DownloadDiskProvider(temp.resolve("new-root"), registeredChapterDirectory = { _, owner, id ->
                repository.chapterAsset(id)?.takeIf {
                    it.mangaId == owner
                }?.let { it.storageRoot.resolve(it.relativePath) }
            })
            disk.findChapterDir(42, "Renamed", "Renamed", manga, chapter) shouldBe oldChapter
            disk.isChapterDownloaded(42, "Renamed", "Renamed", manga, chapter) shouldBe true
            disk.deleteChapter(42, "Renamed", "Renamed", manga, chapter) shouldBe true
            Files.exists(oldChapter) shouldBe false
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelled chapter cleanup finishes before the same chapter starts again`(@TempDir temp: Path): Unit = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val oldCancelling = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val releaseOther = CompletableDeferred<Unit>()
        val otherFailed = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        val network = DesktopNetworkHelper()
        val downloader = DesktopDownloader(
            DownloadStore(temp.resolve("queue.json")),
            DownloadDiskProvider(temp.resolve("downloads")),
            network,
            scope = backgroundScope,
            downloadParallelism = { 2 },
            sourceParallelism = { 2 },
            pageListFetcher = { _, url ->
                if (url == "/other") {
                    releaseOther.await()
                    error("other worker released")
                }
                if (attempts.incrementAndGet() == 1) {
                    oldStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            oldCancelling.complete(Unit)
                            releaseOld.await()
                        }
                    }
                }
                newStarted.complete(Unit)
                emptyList()
            },
            onDownloadFailed = { item, _ -> if (item.chapterUrl == "/other") otherFailed.complete(Unit) },
        )
        DesktopLibraryDatabaseFactory.open(temp.resolve("library.db")).use { repository ->
            val manga = repository.insertManga(MangaRecord(sourceId = 42, url = "/m", title = "Manga"))
            val id = repository.insertChapter(ChapterRecord(mangaId = manga, url = "/first", name = "First"))
            repository.insertChapter(ChapterRecord(mangaId = manga, url = "/other", name = "Other"))
            val chapters = repository.chapterSnapshot(manga)
            try {
                withTimeout(5_000) {
                    downloader.enqueue(42, manga, "Manga", chapters)
                    runCurrent()
                    oldStarted.await()
                    downloader.cancel(id)
                    runCurrent()
                    oldCancelling.await()
                    downloader.enqueue(42, manga, "Manga", chapters.filter { it.id == id })
                    releaseOther.complete(Unit)
                    runCurrent()
                    otherFailed.await()
                    newStarted.isCompleted shouldBe false
                    releaseOld.complete(Unit)
                    runCurrent()
                    newStarted.await()
                    while (downloader.isRunning.value) delay(10)
                    attempts.get() shouldBe 2
                }
            } finally {
                releaseOld.complete(Unit)
                releaseOther.complete(Unit)
                downloader.shutdown()
                network.close()
            }
        }
    }
}
