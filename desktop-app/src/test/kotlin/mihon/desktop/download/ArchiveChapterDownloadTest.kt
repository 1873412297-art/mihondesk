package mihon.desktop.download

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ArchiveChapterDownloadTest {

    private val sourceId = 7L
    private val mangaTitle = "Archive Manga"
    private val chapterName = "Chapter 1"

    @Test
    fun `archive mode publishes one cbz and registers the archive asset`(@TempDir dir: Path) {
        DesktopLibraryDatabaseFactory.open(dir.resolve("library.db")).use { repository ->
            val mangaId = insertManga(repository)
            val chapterId = insertChapter(repository, mangaId)
            val disk = DownloadDiskProvider(dir.resolve("downloads"), saveAsCbz = { true })
            val pages = listOf(validDownloadImage(), validDownloadImage())
            val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            pages.forEachIndexed { index, bytes -> disk.savePage(temp, index, bytes) }

            val published = disk.finalizeChapter(
                sourceId,
                mangaId,
                chapterId,
                mangaTitle,
                chapterName,
                pages.size,
                repository,
            )

            published.fileName.toString() shouldBe "chapter-$chapterId.cbz"
            Files.isRegularFile(published) shouldBe true
            Files.exists(published.resolveSibling("chapter-$chapterId")) shouldBe false
            Files.exists(published.resolveSibling("chapter-$chapterId.cbz_tmp")) shouldBe false
            Files.exists(temp) shouldBe false
            Files.list(published.parent).use { paths -> paths.toList() } shouldBe listOf(published)

            val asset = requireNotNull(repository.chapterAsset(chapterId))
            asset.assetKind shouldBe "ARCHIVE"
            asset.relativePath.toString() shouldBe "chapter-$chapterId.cbz"
            asset.storageRoot shouldBe published.parent
            asset.sizeBytes shouldBe pages.sumOf { it.size.toLong() }

            disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, mangaId, chapterId) shouldBe true
            repository.isLocalChapterAssetRegistered(
                mangaId = mangaId,
                storagePath = published.parent.toAbsolutePath().toString(),
                chapterId = chapterId,
                relativePath = "chapter-$chapterId.cbz",
                sizeBytes = pages.sumOf { it.size.toLong() },
                assetKind = "ARCHIVE",
            ) shouldBe true
        }
    }

    @Test
    fun `published archive holds the pages and the manifest`(@TempDir dir: Path) {
        val disk = DownloadDiskProvider(dir.resolve("downloads"), saveAsCbz = { true })
        val pages = listOf(validDownloadImage(), validDownloadImage())
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        pages.forEachIndexed { index, bytes -> disk.savePage(temp, index, bytes) }

        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, pages.size)

        ZipFile(published.toFile()).use { zip ->
            val entries = zip.entries().asSequence().map(ZipEntry::getName).sorted().toList()
            entries shouldBe listOf(".mihon-download.json", "001.jpg", "002.jpg")
            pages.forEachIndexed { index, bytes ->
                val entry = zip.getEntry(disk.getPageFile(temp, index).fileName.toString())
                entry.method shouldBe ZipEntry.STORED
                zip.getInputStream(entry).use { it.readBytes().toList() } shouldBe bytes.toList()
            }
        }
    }

    @Test
    fun `archive chapter is reported downloaded, inspected and deletable`(@TempDir dir: Path) {
        val disk = DownloadDiskProvider(dir.resolve("downloads"), saveAsCbz = { true })
        val pages = listOf(validDownloadImage(), validDownloadImage())
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        pages.forEachIndexed { index, bytes -> disk.savePage(temp, index, bytes) }
        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, pages.size)

        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe true
        val inspection = disk.inspectChapter(sourceId, mangaTitle, chapterName, listOf(0, 1), 1L, 11L)
        inspection.isComplete shouldBe true
        inspection.totalBytes shouldBe pages.sumOf { it.size.toLong() }

        disk.findChapterEntry(sourceId, mangaTitle, chapterName, 1L, 11L)?.path shouldBe published
        disk.deleteChapter(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe true
        Files.exists(published) shouldBe false
        Files.exists(published.parent) shouldBe false
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe false
    }

    @Test
    fun `default mode still publishes a chapter directory`(@TempDir dir: Path) {
        val disk = DownloadDiskProvider(dir.resolve("downloads"))
        val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, 1L, 11L)
        disk.savePage(temp, 0, validDownloadImage())

        val published = disk.finalizeChapter(sourceId, 1L, 11L, mangaTitle, chapterName, 1)

        Files.isDirectory(published) shouldBe true
        published.fileName.toString() shouldBe "chapter-11"
        Files.isRegularFile(published.resolve("001.jpg")) shouldBe true
        disk.findChapterEntry(sourceId, mangaTitle, chapterName, 1L, 11L)?.archive shouldBe false
        disk.isChapterDownloaded(sourceId, mangaTitle, chapterName, 1L, 11L) shouldBe true
    }

    @Test
    fun `registration failure during archive publish keeps the previous chapter and stays retryable`(
        @TempDir dir: Path,
    ) {
        DesktopLibraryDatabaseFactory.open(dir.resolve("library.db")).use { repository ->
            val mangaId = insertManga(repository)
            // The queued chapter is missing from the database, so registration must fail.
            val missingChapterId = 999L
            val disk = DownloadDiskProvider(dir.resolve("downloads"), saveAsCbz = { true })
            val previous = disk.getChapterDir(sourceId, mangaTitle, chapterName, mangaId, missingChapterId)
            Files.createDirectories(previous)
            Files.writeString(previous.resolve("previous-marker"), "keep")
            val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, missingChapterId)
            val newPage = disk.savePage(temp, 0, validDownloadImage())
            val pageBytes = Files.readAllBytes(newPage).toList()

            shouldThrow<Exception> {
                disk.finalizeChapter(sourceId, mangaId, missingChapterId, mangaTitle, chapterName, 1, repository)
            }

            val target = previous.resolveSibling("chapter-$missingChapterId.cbz")
            Files.exists(target) shouldBe false
            Files.exists(previous.resolveSibling("chapter-$missingChapterId.cbz_tmp")) shouldBe false
            Files.exists(previous.resolveSibling("chapter-$missingChapterId.cbz.previous")) shouldBe false
            Files.exists(previous.resolveSibling("chapter-$missingChapterId.previous")) shouldBe false
            Files.readString(previous.resolve("previous-marker")) shouldBe "keep"
            Files.readAllBytes(newPage).toList() shouldBe pageBytes
            repository.localMangaStoragePaths() shouldBe emptySet()
        }
    }

    @Test
    fun `startup recovery re-registers an archive chapter`(@TempDir dir: Path): Unit = runBlocking {
        DesktopLibraryDatabaseFactory.open(dir.resolve("library.db")).use { repository ->
            val mangaId = insertManga(repository)
            val chapterId = insertChapter(repository, mangaId)
            val disk = DownloadDiskProvider(dir.resolve("downloads"), saveAsCbz = { true })
            val pages = listOf(validDownloadImage(), validDownloadImage())
            val temp = disk.getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            pages.forEachIndexed { index, bytes -> disk.savePage(temp, index, bytes) }
            val published = disk.finalizeChapter(
                sourceId,
                mangaId,
                chapterId,
                mangaTitle,
                chapterName,
                pages.size,
                repository,
            )
            // Simulate a lost offline registration with the files still published on disk.
            repository.deleteLocalChapterAsset(mangaId, chapterId)
            val store = DownloadStore(dir.resolve("downloads.json"))
            store.save(
                listOf(
                    DesktopDownload(
                        chapterId = chapterId,
                        mangaId = mangaId,
                        sourceId = sourceId,
                        mangaTitle = mangaTitle,
                        chapterName = chapterName,
                        chapterUrl = "/chapter/1",
                        pages = List(pages.size) {
                            DownloadPage(it, "/image", status = PageStatus.READY, progress = 1f)
                        },
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        storageLayoutVersion = 1,
                    ),
                ),
            )
            val network = DesktopNetworkHelper()
            DesktopDownloader(store, disk, network, mutationPort = repository).use { downloader ->
                try {
                    downloader.awaitStartupRecovery()
                    downloader.queueState.value.single().status shouldBe DownloadStatus.COMPLETED
                    val asset = requireNotNull(repository.chapterAsset(chapterId))
                    asset.assetKind shouldBe "ARCHIVE"
                    asset.relativePath.toString() shouldBe published.fileName.toString()
                    // The downloader deletes the published archive through the archive layout.
                    downloader.deleteDownloadedChapter(sourceId, mangaId, mangaTitle, chapterId, chapterName) shouldBe
                        true
                    Files.exists(published) shouldBe false
                } finally {
                    network.close()
                }
            }
        }
    }

    private fun insertManga(repository: mihon.desktop.library.db.SqlDelightLibraryRepository): Long =
        repository.insertManga(MangaRecord(sourceId = sourceId, url = "/manga/1", title = mangaTitle))

    private fun insertChapter(
        repository: mihon.desktop.library.db.SqlDelightLibraryRepository,
        mangaId: Long,
    ): Long = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/chapter/1", name = chapterName))
}
