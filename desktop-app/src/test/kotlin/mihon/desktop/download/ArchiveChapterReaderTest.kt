package mihon.desktop.download

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.extension.source.model.Page
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ArchiveChapterReaderTest {

    private fun createSampleImageBytes(): ByteArray {
        val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 20, 20)
        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "PNG", output)
            output.toByteArray()
        }
    }

    @Test
    fun `cbz download is readable as pages through DesktopReaderFactory`(@TempDir tempDir: Path) = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("archive-reader.db"))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val imageBytes = createSampleImageBytes()
        server.createContext("/p1.png") { exchange ->
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }
        server.createContext("/p2.png") { exchange ->
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }
        server.start()
        val port = server.address.port

        val networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("archive.ext", listOf("127.0.0.1", "localhost"))

        val diskProvider = DownloadDiskProvider(tempDir.resolve("downloads"), saveAsCbz = { true })
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("downloads.json")),
            diskProvider = diskProvider,
            networkHelper = networkHelper,
            mutationPort = repository,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, "http://127.0.0.1:$port/p1.png", "http://127.0.0.1:$port/p1.png"),
                    Page(1, "http://127.0.0.1:$port/p2.png", "http://127.0.0.1:$port/p2.png"),
                )
            },
        )

        val mangaId = repository.insertManga(
            MangaRecord(sourceId = 999L, url = "/online/manga/1", title = "Archive Read Manga"),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(mangaId = mangaId, url = "/online/chapter/10", name = "Chapter 10"),
        )
        val manga = LibraryManga(
            id = mangaId,
            sourceId = 999L,
            url = "/online/manga/1",
            title = "Archive Read Manga",
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 1L,
            author = null,
        )
        val chapter = LibraryChapter(
            id = chapterId,
            mangaId = mangaId,
            url = "/online/chapter/10",
            name = "Chapter 10",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 10.0,
            sourceOrder = 0L,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )

        downloader.enqueue(manga, listOf(chapter), autoStart = true)
        withTimeout(5_000) {
            while (downloader.queueState.value.firstOrNull()?.status != DownloadStatus.COMPLETED) {
                delay(50)
            }
        }

        val published = diskProvider.getChapterDir(999L, manga.title, chapter.name, mangaId, chapterId)
            .resolveSibling("chapter-$chapterId.cbz")
        Files.isRegularFile(published) shouldBe true
        Files.exists(published.resolveSibling("chapter-$chapterId")) shouldBe false
        repository.chapterAsset(chapterId)?.assetKind shouldBe "ARCHIVE"

        // Reading must work without any network access.
        server.stop(0)

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settingsStore = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("prefs.json")))
        val readerFactory = DesktopReaderFactory(appScope, repository, settingsStore)
        val session = readerFactory.createSession()
        session.open(chapterId)
        withTimeout(5_000) {
            while (session.state.value.pages.isEmpty()) {
                delay(50)
            }
        }

        session.state.value.pages shouldHaveSize 2
        val firstPage = session.state.value.pages.first()
        val frame = readerFactory.loadFrame(firstPage.id, 0)
        frame.metadata.width shouldBe 20
        frame.metadata.height shouldBe 20

        downloader.close()
        networkHelper.close()
        repository.close()
    }
}
