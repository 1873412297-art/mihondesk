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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * End-to-end coverage of the opt-in "split tall images" download setting: page storage, resume,
 * chapter inspection, CBZ parity and reading.
 */
class SplitTallImagesDownloadTest {

    private val sourceId = 999L
    private val mangaTitle = "Split Tall Manga"
    private val chapterName = "Chapter 1"
    private val partHeights = listOf(82, 82, 82, 82, 85)
    private val partNames = listOf(
        "001__001.jpg",
        "001__002.jpg",
        "001__003.jpg",
        "001__004.jpg",
        "001__005.jpg",
    )
    private val partCount = partNames.size

    private lateinit var server: HttpServer
    private lateinit var networkHelper: DesktopNetworkHelper
    private lateinit var serverExecutor: ExecutorService
    private var serverPort: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverExecutor = Executors.newCachedThreadPool()
        server.executor = serverExecutor
        server.start()
        serverPort = server.address.port
        networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1", "localhost"))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        serverExecutor.shutdownNow()
    }

    @Test
    fun `split download finalizes, inspects and reads as extra pages`(@TempDir tempDir: Path) = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db"))
        val tallBytes = tallDownloadImage(width = 40, height = 413)
        val plainBytes = validDownloadImage()
        serve("/tall.png", tallBytes)
        serve("/plain.png", plainBytes)

        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("queue.json")),
            diskProvider = disk,
            networkHelper = networkHelper,
            mutationPort = repository,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, url("/tall.png"), url("/tall.png")),
                    Page(1, url("/plain.png"), url("/plain.png")),
                )
            },
            tallImageSplitter = splitter(disk),
        )
        val mangaId = repository.insertManga(
            MangaRecord(sourceId = sourceId, url = "/manga/1", title = mangaTitle),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(mangaId = mangaId, url = "/chapter/1", name = chapterName),
        )
        val manga = manga(mangaId)
        val chapter = chapter(chapterId, mangaId)

        downloader.enqueue(manga, listOf(chapter), autoStart = true)
        awaitCompleted(downloader)

        val published = requireNotNull(disk.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId))
        published.archive shouldBe false
        val parts = (0 until partCount).map { disk.getSplitPartFile(published.path, 0, it) }
        parts.forEach { Files.isRegularFile(it) shouldBe true }
        Files.exists(disk.getPageFile(published.path, 0)) shouldBe false
        Files.isRegularFile(disk.getPageFile(published.path, 1)) shouldBe true

        // Byte accounting follows what landed on disk, not the downloaded payload.
        val partBytes = parts.sumOf { Files.size(it) }
        val wholePageBytes = Files.size(disk.getPageFile(published.path, 1))
        downloader.queueState.value.single().pages[0].bytesWritten shouldBe partBytes
        downloader.queueState.value.single().pages[1].bytesWritten shouldBe wholePageBytes
        downloader.queueState.value.single().bytesDownloaded shouldBe partBytes + wholePageBytes

        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, mangaId, chapterId) shouldBe true
        val inspection = disk.inspectChapter(sourceId, mangaTitle, chapterName, listOf(0, 1), mangaId, chapterId)
        inspection.isComplete shouldBe true
        inspection.totalBytes shouldBe partBytes + wholePageBytes
        requireNotNull(repository.chapterAsset(chapterId)).sizeBytes shouldBe partBytes + wholePageBytes

        // Reading offline yields one page per part, in order, plus the untouched second page.
        server.stop(0)
        val readerFactory = DesktopReaderFactory(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            repository,
            DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("prefs.json"))),
        )
        val session = readerFactory.createSession()
        session.open(chapterId)
        withTimeout(5_000) { while (session.state.value.pages.isEmpty()) delay(50) }

        val pages = session.state.value.pages
        pages shouldHaveSize partNames.size + 1
        pages.map { it.id.entryName } shouldBe partNames + "002.jpg"
        partHeights.forEachIndexed { index, height ->
            val frame = readerFactory.loadFrame(pages[index].id, 0)
            frame.metadata.width shouldBe 40
            frame.metadata.height shouldBe height
        }

        downloader.close()
        networkHelper.close()
        repository.close()
    }

    @Test
    fun `resume skips a page already split in the temporary directory`(@TempDir tempDir: Path) = runBlocking {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val manga = manga(1L)
        val chapter = chapter(10L, 1L)
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, manga.id, chapter.id)
        val splitter = splitter(disk)
        val page = disk.savePage(temp, 0, tallDownloadImage(40, 413))
        splitter.split(page, pageIndex = 0) shouldBe TallImageSplitResult.Split(5, partsByteCount(disk, temp))

        val firstPageRequests = AtomicInteger(0)
        val secondPageRequests = AtomicInteger(0)
        serve("/split-page0.png", tallDownloadImage(40, 413), firstPageRequests)
        serve("/split-page1.png", validDownloadImage(), secondPageRequests)

        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("queue.json")),
            diskProvider = disk,
            networkHelper = networkHelper,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, url("/split-page0.png"), url("/split-page0.png")),
                    Page(1, url("/split-page1.png"), url("/split-page1.png")),
                )
            },
            tallImageSplitter = splitter,
        )
        downloader.enqueue(manga, listOf(chapter), autoStart = true)
        awaitCompleted(downloader)

        firstPageRequests.get() shouldBe 0
        secondPageRequests.get() shouldBe 1
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, manga.id, chapter.id) shouldBe true
        downloader.close()
    }

    @Test
    fun `retry reuses split parts from the published directory`(@TempDir tempDir: Path) = runBlocking {
        val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
        val manga = manga(1L)
        val chapter = chapter(10L, 1L)
        val published = disk.getChapterDir(sourceId, mangaTitle, chapterName, manga.id, chapter.id)
        val splitter = splitter(disk)
        splitter.split(disk.savePage(published, 0, tallDownloadImage(40, 413)), pageIndex = 0)
            .let { it as TallImageSplitResult.Split }.partCount shouldBe 5
        disk.savePage(published, 1, validDownloadImage())

        val store = DownloadStore(tempDir.resolve("queue.json"))
        val pages = (0..1).map { index ->
            DownloadPage(index, url("/never-fetched.png"), status = PageStatus.READY)
        }
        store.save(listOf(completedDownload(manga, chapter, pages)))

        val requests = AtomicInteger(0)
        serve("/never-fetched.png", validDownloadImage(), requests)
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = disk,
            networkHelper = networkHelper,
            tallImageSplitter = splitter,
        )
        try {
            downloader.enqueue(manga, listOf(chapter))
            withTimeout(5_000) { while (downloader.isRunning.value) delay(10) }

            requests.get() shouldBe 0
            downloader.queueState.value.single().status shouldBe DownloadStatus.COMPLETED
            disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, manga.id, chapter.id) shouldBe true
            val parts = (0 until partCount).map { disk.getSplitPartFile(published, 0, it) }
            parts.forEach { disk.isValidPage(it) shouldBe true }
        } finally {
            downloader.close()
        }
    }

    @Test
    fun `archive mode stores the split parts and reads back as the same pages`(@TempDir tempDir: Path) = runBlocking {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("archive-split.db"))
        val tallBytes = tallDownloadImage(width = 40, height = 413)
        val plainBytes = validDownloadImage()
        serve("/archive-tall.png", tallBytes)
        serve("/archive-plain.png", plainBytes)

        val disk = DownloadDiskProvider(tempDir.resolve("downloads"), saveAsCbz = { true })
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("queue.json")),
            diskProvider = disk,
            networkHelper = networkHelper,
            mutationPort = repository,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, url("/archive-tall.png"), url("/archive-tall.png")),
                    Page(1, url("/archive-plain.png"), url("/archive-plain.png")),
                )
            },
            tallImageSplitter = splitter(disk),
        )
        val mangaId = repository.insertManga(
            MangaRecord(sourceId = sourceId, url = "/manga/1", title = mangaTitle),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(mangaId = mangaId, url = "/chapter/1", name = chapterName),
        )
        val manga = manga(mangaId)
        val chapter = chapter(chapterId, mangaId)

        downloader.enqueue(manga, listOf(chapter), autoStart = true)
        awaitCompleted(downloader)

        val entry = requireNotNull(disk.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId))
        entry.archive shouldBe true
        val expectedNames = listOf(".mihon-download.json") + partNames + "002.jpg"
        ZipFile(entry.path.toFile()).use { zip ->
            zip.entries().asSequence().map(ZipEntry::getName).sorted().toList() shouldBe expectedNames
        }
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, mangaId, chapterId) shouldBe true
        val inspection = disk.inspectChapter(sourceId, mangaTitle, chapterName, listOf(0, 1), mangaId, chapterId)
        inspection.isComplete shouldBe true
        inspection.totalBytes shouldBe
            (repository.chapterAsset(chapterId)?.sizeBytes ?: error("chapter asset is missing"))

        server.stop(0)
        val readerFactory = DesktopReaderFactory(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            repository,
            DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("archive-prefs.json"))),
        )
        val session = readerFactory.createSession()
        session.open(chapterId)
        withTimeout(5_000) { while (session.state.value.pages.isEmpty()) delay(50) }

        val pages = session.state.value.pages
        pages shouldHaveSize partNames.size + 1
        pages.map { it.id.entryName } shouldBe expectedNames.drop(1)
        val frame = readerFactory.loadFrame(pages.first().id, 0)
        frame.metadata.width shouldBe 40
        frame.metadata.height shouldBe partHeights.first()

        downloader.close()
        networkHelper.close()
        repository.close()
    }

    @Test
    fun `a download whose split fails to encode still completes with the whole page`(@TempDir tempDir: Path) =
        runBlocking {
            val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("failing-split.db"))
            val tallBytes = tallDownloadImage(width = 40, height = 413)
            serve("/failing-tall.png", tallBytes)

            val disk = DownloadDiskProvider(tempDir.resolve("downloads"))
            val downloader = DesktopDownloader(
                store = DownloadStore(tempDir.resolve("queue.json")),
                diskProvider = disk,
                networkHelper = networkHelper,
                mutationPort = repository,
                pageListFetcher = { _, _ -> listOf(Page(0, url("/failing-tall.png"), url("/failing-tall.png"))) },
                tallImageSplitter = TallImageSplitter(
                    diskProvider = disk,
                    enabled = { true },
                    encodeJpeg = { _, _ -> throw java.io.IOException("encoder unavailable") },
                    optimalSplitHeight = 100,
                ),
            )
            val mangaId = repository.insertManga(
                MangaRecord(sourceId = sourceId, url = "/manga/1", title = mangaTitle),
            )
            val chapterId = repository.insertChapter(
                ChapterRecord(mangaId = mangaId, url = "/chapter/1", name = chapterName),
            )

            downloader.enqueue(manga(mangaId), listOf(chapter(chapterId, mangaId)), autoStart = true)
            awaitCompleted(downloader)

            val entry = requireNotNull(disk.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId))
            Files.readAllBytes(disk.getPageFile(entry.path, 0)).toList() shouldBe tallBytes.toList()
            Files.list(entry.path).use { paths ->
                paths.map { it.fileName.toString() }.filter { it.contains("__") }.toList()
            } shouldBe emptyList()
            disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, mangaId, chapterId) shouldBe true
            downloader.queueState.value.single().pages[0].bytesWritten shouldBe tallBytes.size.toLong()

            downloader.close()
            networkHelper.close()
            repository.close()
        }

    private fun splitter(disk: DownloadDiskProvider) =
        TallImageSplitter(disk, enabled = { true }, optimalSplitHeight = 100)

    private fun partsByteCount(disk: DownloadDiskProvider, temp: Path): Long =
        (0 until partCount).sumOf { Files.size(disk.getSplitPartFile(temp, 0, it)) }

    private fun serve(path: String, bytes: ByteArray, counter: AtomicInteger? = null) {
        server.createContext(path) { exchange ->
            counter?.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun url(path: String) = "http://127.0.0.1:$serverPort$path"

    private suspend fun awaitCompleted(downloader: DesktopDownloader) {
        withTimeout(10_000) {
            while (true) {
                val item = downloader.queueState.value.firstOrNull()
                when (item?.status) {
                    DownloadStatus.COMPLETED -> return@withTimeout
                    DownloadStatus.ERROR -> throw AssertionError(
                        "download failed: ${item.error} / ${item.failureReason} / " +
                            "pages=${item.pages.map { it.index to it.status }}",
                    )
                    else -> delay(20)
                }
            }
        }
    }

    private fun manga(id: Long) = LibraryManga(
        id = id,
        sourceId = sourceId,
        url = "/manga/1",
        title = mangaTitle,
        thumbnailUrl = null,
        chapterCount = 1L,
        unreadCount = 1L,
        author = null,
    )

    private fun chapter(id: Long, mangaId: Long) = LibraryChapter(
        id = id,
        mangaId = mangaId,
        url = "/chapter/1",
        name = chapterName,
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = 0L,
        chapterNumber = 1.0,
        sourceOrder = 0L,
        lastModifiedAt = 0L,
        version = 0L,
        memoJson = "{}",
    )

    private fun completedDownload(
        manga: LibraryManga,
        chapter: LibraryChapter,
        pages: List<DownloadPage>,
    ) = DesktopDownload(
        chapterId = chapter.id,
        mangaId = manga.id,
        sourceId = manga.sourceId,
        mangaTitle = manga.title,
        chapterName = chapter.name,
        chapterUrl = chapter.url,
        pages = pages,
        status = DownloadStatus.COMPLETED,
        progress = 1f,
        storageLayoutVersion = 1,
    )
}
