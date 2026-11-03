package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.desktop.extension.OnlineFetchPriority
import mihon.desktop.extension.OnlineImageCache
import mihon.desktop.extension.OnlinePageCacheFiles
import mihon.desktop.extension.onlineImagePriority
import mihon.extension.ipc.RequestPriority
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.prefetch.PageLoadPriority
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class MangaOnlineCacheTest {
    @Test
    fun `opening a chapter prefetches the next chapter and the previous tail in the background`(
        @TempDir tempDir: Path,
    ) = runTest {
        val root = tempDir.toFile()
        val opened = mutableListOf<Long>()
        val priorities = mutableListOf<Int>()
        val catalog = neighborCatalog()
        val cache = MangaOnlineCache(
            root = root,
            scope = this,
            catalog = catalog,
            sourceFactory = factory(root, pageCount = 10, opened = opened, priorities = priorities),
        )

        cache.chapterOpened(5L)
        advanceUntilIdle()

        opened.count { it == 5L } shouldBe 0
        opened.count { it == 6L } shouldBe 10
        opened.count { it == 4L } shouldBe OnlinePageCacheFiles.PREVIOUS_TAIL_PAGES
        priorities.distinct() shouldBe listOf(RequestPriority.BACKGROUND)
        cache.clear(1L)
    }

    @Test
    fun `opening the prefetched chapter does not cancel its download`(
        @TempDir tempDir: Path,
    ) = runTest {
        val root = tempDir.toFile()
        val gate = CompletableDeferred<Unit>()
        var enteredNext = false
        val catalog = neighborCatalog()
        val cache = MangaOnlineCache(
            root = root,
            scope = this,
            catalog = catalog,
            sourceFactory = factory(root, pageCount = 2, opened = null, priorities = null) { asset, _ ->
                if (asset.chapterId == 6L && !enteredNext) {
                    enteredNext = true
                    gate.await()
                }
            },
        )

        cache.chapterOpened(5L)
        runCurrent()
        enteredNext shouldBe true
        cache.chapterOpened(6L)
        gate.complete(Unit)
        advanceUntilIdle()

        File(root, "manga-1").listFiles()?.count { it.name.startsWith("6_") } shouldBe 2
        cache.clear(1L)
    }

    @Test
    fun `leaving the window deletes that chapter and leaving the manga deletes only its directory`(
        @TempDir tempDir: Path,
    ) = runTest {
        val root = tempDir.toFile()
        val catalog = neighborCatalog()
        val cache = MangaOnlineCache(
            root = root,
            scope = this,
            catalog = catalog,
            sourceFactory = factory(root, pageCount = 2, opened = null, priorities = null),
        )
        val other = File(root, "manga-2").apply { mkdirs() }
        File(other, "keep.img").writeBytes(byteArrayOf(1))

        cache.chapterOpened(5L)
        advanceUntilIdle()
        File(root, "manga-1").listFiles()?.any { it.name.startsWith("4_") } shouldBe true

        cache.chapterOpened(6L)
        advanceUntilIdle()
        File(root, "manga-1").listFiles()?.none { it.name.startsWith("4_") } shouldBe true
        File(other, "keep.img").isFile shouldBe true

        cache.clear(1L)
        File(root, "manga-1").exists() shouldBe false
        File(other, "keep.img").isFile shouldBe true
    }

    @Test
    fun `visible page stays at reader priority ahead of background prefetch`() {
        onlineImagePriority(kotlin.coroutines.EmptyCoroutineContext) shouldBe RequestPriority.READER
        onlineImagePriority(PageLoadPriority { false }) shouldBe RequestPriority.NORMAL
        onlineImagePriority(OnlineFetchPriority(RequestPriority.BACKGROUND) + PageLoadPriority { true }) shouldBe
            RequestPriority.BACKGROUND
    }

    private fun neighborCatalog(): ReaderChapterCatalog = object : ReaderChapterCatalog {
        override fun chapterAsset(chapterId: Long): ReaderChapterAsset? = asset(chapterId)

        override fun adjacentReadableChapter(chapterId: Long, direction: ChapterDirection): ReaderChapterAsset? =
            when (direction) {
                ChapterDirection.NEXT -> asset(chapterId + 1)
                ChapterDirection.PREVIOUS -> asset(chapterId - 1).takeIf { chapterId > 1L }
            }
    }

    private fun factory(
        root: File,
        pageCount: Int,
        opened: MutableList<Long>?,
        priorities: MutableList<Int>?,
        beforeOpen: suspend (ReaderChapterAsset, PageId) -> Unit = { _, _ -> },
    ): ChapterSourceFactory = ChapterSourceFactory { asset ->
        DiskSource(root, asset, pageCount, opened, priorities, beforeOpen)
    }

    private fun asset(chapterId: Long) = ReaderChapterAsset(
        mangaId = 1L,
        chapterId = chapterId,
        mangaTitle = "manga",
        chapterName = "chapter-$chapterId",
        storageRoot = Path.of("."),
        relativePath = Path.of("chapter-$chapterId"),
        assetKind = ONLINE_CHAPTER_ASSET_KIND,
        sizeBytes = 1L,
        modifiedAt = 1L,
        lastPageRead = 0L,
        read = false,
    )

    private class DiskSource(
        private val root: File,
        override val asset: ReaderChapterAsset,
        private val pageCount: Int,
        private val opened: MutableList<Long>?,
        private val priorities: MutableList<Int>?,
        private val beforeOpen: suspend (ReaderChapterAsset, PageId) -> Unit,
    ) : ChapterSource, OnlineImageCache {
        override suspend fun pages(): List<PageDescriptor> =
            (0 until pageCount).map { index ->
                PageDescriptor(PageId(asset.chapterId.toString(), "page_%04d.jpg".format(index)), 1, 1)
            }

        override suspend fun open(pageId: PageId): BoundedPageInput {
            beforeOpen(asset, pageId)
            opened?.add(asset.chapterId)
            priorities?.add(onlineImagePriority(coroutineContext))
            val index = pageId.entryName.substringAfter("page_").substringBefore('.').toInt()
            val file = File(OnlinePageCacheFiles.mangaDirectory(root, asset.mangaId), "${asset.chapterId}_$index.img")
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(1))
            return BoundedPageInput(ByteArrayInputStream(byteArrayOf(1)), 1)
        }

        override fun cachedImageFiles(): List<File> = chapterFiles()

        override fun deleteCachedImages() {
            chapterFiles().forEach { it.delete() }
        }

        override fun close() = Unit

        private fun chapterFiles(): List<File> =
            OnlinePageCacheFiles.mangaDirectory(root, asset.mangaId).listFiles()
                ?.filter { it.isFile && it.name.startsWith("${asset.chapterId}_") }
                .orEmpty()
    }
}
