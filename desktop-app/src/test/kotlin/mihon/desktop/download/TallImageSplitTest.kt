package mihon.desktop.download

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.reader.image.PageDecoder
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class TallImageSplitTest {

    private val sourceId = 5L
    private val mangaTitle = "Tall Manga"
    private val chapterName = "Chapter 1"

    /** 40x413 splits into 5 parts of 82 px, the last of them 85 px. */
    private val partCount = 5
    private val partHeights = listOf(82, 82, 82, 82, 85)

    @Test
    fun `splits a tall page into ordered jpeg parts and removes the original`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val page = disk.savePage(temp, 0, tallDownloadImage(width = 40, height = 413))

        val result = splitter(disk).split(page, pageIndex = 0)

        val split = result as TallImageSplitResult.Split
        split.partCount shouldBe partCount
        Files.exists(page) shouldBe false
        Files.list(temp).use { it.toList() } shouldHaveSize partCount

        val parts = (0 until partCount).map { disk.getSplitPartFile(temp, 0, it) }
        parts.forEach { Files.isRegularFile(it) shouldBe true }
        parts.map { it.fileName.toString() } shouldBe
            listOf("001__001.jpg", "001__002.jpg", "001__003.jpg", "001__004.jpg", "001__005.jpg")

        parts.forEachIndexed { index, part ->
            val decoded = ImageIO.read(part.toFile())
            decoded.width shouldBe 40
            decoded.height shouldBe partHeights[index]
        }
        split.totalBytes shouldBe parts.sumOf { Files.size(it) }
    }

    @Test
    fun `the parts of several pages keep the natural page order`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val splitter = splitter(disk)

        splitter.split(disk.savePage(temp, 0, tallDownloadImage(40, 413)), pageIndex = 0)
            .let { it as TallImageSplitResult.Split }.partCount shouldBe partCount
        disk.savePage(temp, 1, validDownloadImage())

        val names = Files.list(temp).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { !it.endsWith(".part") }
                .toList()
                .sortedWith(mihon.reader.layout.NaturalPageComparator)
        }
        names shouldBe listOf(
            "001__001.jpg",
            "001__002.jpg",
            "001__003.jpg",
            "001__004.jpg",
            "001__005.jpg",
            "002.jpg",
        )
    }

    @Test
    fun `leaves a page with a normal aspect ratio untouched`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val bytes = tallDownloadImage(width = 40, height = 100)
        val page = disk.savePage(temp, 0, bytes)

        splitter(disk).split(page, pageIndex = 0) shouldBe TallImageSplitResult.Untouched

        Files.readAllBytes(page).toList() shouldBe bytes.toList()
        Files.list(temp).use { it.toList() } shouldHaveSize 1
    }

    @Test
    fun `leaves a tall page shorter than the split height untouched`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        // 40x500 is taller than 3x its width but still a single part at OPTIMAL_SPLIT_HEIGHT.
        val page = disk.savePage(temp, 0, tallDownloadImage(width = 40, height = 500))
        val splitter = TallImageSplitter(disk, enabled = { true })

        splitter.split(page, pageIndex = 0) shouldBe TallImageSplitResult.Untouched
        OPTIMAL_SPLIT_HEIGHT shouldBe 4096
        Files.exists(page) shouldBe true
        Files.list(temp).use { it.toList() } shouldHaveSize 1
    }

    @Test
    fun `leaves an animated image untouched`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val bytes = animatedGifImage(width = 40, height = 413, frameCount = 2)
        val page = disk.savePage(temp, 0, bytes)

        splitter(disk).split(page, pageIndex = 0) shouldBe TallImageSplitResult.Untouched

        Files.readAllBytes(page).toList() shouldBe bytes.toList()
        Files.list(temp).use { it.toList() } shouldHaveSize 1
    }

    @Test
    fun `leaves a page the splitter cannot decode untouched`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val bytes = tallDownloadImage(width = 40, height = 413)
        val page = disk.savePage(temp, 0, bytes)
        // ImageIoPageDecoder raises exactly this for a format no ImageIO reader claims.
        val undecodable = object : PageDecoder {
            override suspend fun probe(input: mihon.reader.source.BoundedPageInput) =
                throw ReaderFailure.UnsupportedImage("no ImageIO reader for the stream")

            override suspend fun decodeFull(
                input: mihon.reader.source.BoundedPageInput,
                metadata: mihon.reader.image.ImageMetadata,
                frameId: mihon.reader.model.FrameId,
            ) = throw ReaderFailure.UnsupportedImage("no ImageIO reader for the stream")

            override suspend fun decodeRegion(
                input: mihon.reader.source.BoundedPageInput,
                metadata: mihon.reader.image.ImageMetadata,
                request: mihon.reader.image.TileRequest,
            ) = throw ReaderFailure.UnsupportedImage("no ImageIO reader for the stream")
        }

        TallImageSplitter(disk, enabled = { true }, decoder = undecodable, optimalSplitHeight = 100)
            .split(page, pageIndex = 0) shouldBe TallImageSplitResult.Untouched

        Files.readAllBytes(page).toList() shouldBe bytes.toList()
        Files.list(temp).use { it.toList() } shouldHaveSize 1
    }

    @Test
    fun `a failing encoder removes its parts and keeps the original`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val bytes = tallDownloadImage(width = 40, height = 413)
        val page = disk.savePage(temp, 0, bytes)

        val result = TallImageSplitter(
            disk,
            enabled = { true },
            encodeJpeg = { _, _ -> throw IOException("encoder unavailable") },
            optimalSplitHeight = 100,
        ).split(page, pageIndex = 0)

        result shouldBe TallImageSplitResult.Untouched
        Files.readAllBytes(page).toList() shouldBe bytes.toList()
        Files.list(temp).use { paths -> paths.map { it.fileName.toString() }.toList() } shouldBe
            listOf("001.jpg")
    }

    @Test
    fun `a disabled splitter never touches the page`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        val bytes = tallDownloadImage(width = 40, height = 413)
        val page = disk.savePage(temp, 0, bytes)

        TallImageSplitter(disk).split(page, pageIndex = 0) shouldBe TallImageSplitResult.Untouched

        Files.readAllBytes(page).toList() shouldBe bytes.toList()
        Files.list(temp).use { it.toList() } shouldHaveSize 1
    }

    /** `partCount` is a defaulted manifest field, so an unsplit chapter keeps today's bytes. */
    @Test
    fun `an unsplit chapter manifest records no part count`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        disk.savePage(temp, 0, validDownloadImage())

        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, 1)

        val manifest = Files.readString(published.resolve(".mihon-download.json"))
        manifest.contains("partCount") shouldBe false
        manifest.contains("\"index\":0") shouldBe true
    }

    @Test
    fun `a split chapter manifest records the part count of every page`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        splitter(disk).split(disk.savePage(temp, 0, tallDownloadImage(40, 413)), pageIndex = 0)
        disk.savePage(temp, 1, validDownloadImage())

        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, 2)

        val manifest = Files.readString(published.resolve(".mihon-download.json"))
        manifest.contains("\"partCount\":5") shouldBe true
        (manifest.contains("\"partCount\":1") shouldBe false)
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe true
        disk.inspectChapter(sourceId, mangaTitle, chapterName, listOf(0, 1), 1L, 11L)
            .isComplete shouldBe true
    }

    /** A page whose parts are missing must count as not downloaded. */
    @Test
    fun `a missing part makes the split page count as not downloaded`(@TempDir dir: Path): Unit = runBlocking {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        splitter(disk).split(disk.savePage(temp, 0, tallDownloadImage(40, 413)), pageIndex = 0)

        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, 1)
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe true

        Files.delete(disk.getSplitPartFile(published, 0, 2))

        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe false
        disk.inspectChapter(sourceId, mangaTitle, chapterName, listOf(0), 1L, 11L).isComplete shouldBe false
    }

    /** A page and its parts must sort as one run: this is what makes each part a reader page. */
    @Test
    fun `reader page order keeps a page and its parts together`() {
        val names = listOf("002.jpg", "001__002.jpg", "001.jpg", "001__001.jpg")

        names.sortedWith(mihon.reader.layout.NaturalPageComparator) shouldBe
            listOf("001.jpg", "001__001.jpg", "001__002.jpg", "002.jpg")
    }

    private fun splitter(disk: DownloadDiskProvider) =
        TallImageSplitter(disk, enabled = { true }, optimalSplitHeight = 100)
}
