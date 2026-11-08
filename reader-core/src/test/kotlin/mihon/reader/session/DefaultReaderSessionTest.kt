package mihon.reader.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.cache.CacheMetrics
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ChapterSource
import mihon.reader.source.ChapterSourceFactory
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderChapterCatalog
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultReaderSessionTest {
    @Test
    fun `runtime generation source gives every session opening a unique increasing token`() {
        val generations = AtomicReaderGenerationSource()

        generations.nextGeneration() shouldBe 0L
        generations.nextGeneration() shouldBe 1L
        generations.nextGeneration() shouldBe 2L
    }

    @Test
    fun `reader state reducer rejects invalid zoom and preserves persisted page identity for rtl`() {
        val initial = ReaderState.ready(
            chapterId = 7L,
            pages = pages(7L, 3),
            selectedIndex = 1,
            viewport = ReaderViewport(100, 200),
        )

        val rtl = initial.reduce(ReaderAction.ChangeMode(mihon.reader.model.ReadingMode.SINGLE_RTL))

        rtl.selectedIndex shouldBe 1
        shouldThrow<IllegalArgumentException> { rtl.reduce(ReaderAction.SetZoom(Float.NaN)) }
        shouldThrow<IllegalArgumentException> {
            rtl.reduce(ReaderAction.SetPan(ReaderPan(Float.POSITIVE_INFINITY, 0f)))
        }
    }

    @Test
    fun `open clamps long persistence before converting and requests its restored visible page`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = Long.MAX_VALUE)
        val source = FakeSource(asset, pages(7, 3))
        val requested = mutableListOf<mihon.reader.model.PageId>()
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = object : ReaderChapterCatalog {
                override fun chapterAsset(chapterId: Long) = asset.takeIf { it.chapterId == chapterId }

                override fun adjacentReadableChapter(
                    chapterId: Long,
                    direction: mihon.reader.source.ChapterDirection,
                ): ReaderChapterAsset? = null
            },
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            visibleContent = { page ->
                requested += page
                null
            },
        )

        session.open(7)
        runCurrent()

        session.state.value.selectedIndex shouldBe 2
        session.state.value.visiblePages.shouldBe(listOf(source.descriptors[2].id))
        requested.shouldBe(listOf(source.descriptors[2].id))
    }

    @Test
    fun `cancelling chapter image discovery closes partial resources without reporting a failure`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 3))
        val pipeline = SuspendingOpeningPipeline()
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            contentPipeline = pipeline,
        )

        val opening = launch { session.open(7) }
        runCurrent()
        opening.cancelAndJoin()

        session.state.value.loadState.shouldBeInstanceOf<ReaderLoadState.Loading>()
        pipeline.chapterCloses shouldBe 1
        source.closeRequests shouldBe 1
    }

    @Test
    fun `cancel without flush returns the cleanup job that closes the chapter source`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 3))
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
        )
        session.open(7)
        runCurrent()

        session.cancelWithoutFlush().join()

        source.closeRequests shouldBe 1
        session.state.value.loadState shouldBe ReaderLoadState.Closed
    }

    @Test
    fun `animation loops with probed durations and pauses when hidden`() = runTest {
        val frames = mutableListOf<mihon.reader.model.FrameId>()
        val coordinator = AnimationCoordinator(backgroundScope, loadFrame = { frame ->
            frames += frame
            null
        })
        val page = mihon.reader.model.PageId("7", "animated.gif")

        coordinator.start(page, AnimationProbe(frameCount = 2, frameDurationsMillis = listOf(10, 20)))
        runCurrent()
        coordinator.selectedFrame.value shouldBe mihon.reader.model.FrameId(page, 0)
        advanceTimeBy(10)
        runCurrent()
        coordinator.selectedFrame.value shouldBe mihon.reader.model.FrameId(page, 1)
        coordinator.setContentVisible(false)
        advanceTimeBy(100)
        runCurrent()

        frames.shouldBe(listOf(mihon.reader.model.FrameId(page, 0), mihon.reader.model.FrameId(page, 1)))
        coordinator.selectedFrame.value shouldBe null
    }

    @Test
    fun `empty chapter has a typed empty chapter reader error`() {
        mihon.reader.model.ReaderErrorCode.EMPTY_CHAPTER shouldBe mihon.reader.model.ReaderErrorCode.EMPTY_CHAPTER
    }

    @Test
    fun `dual page next advances to the next logical spread`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 5))
        val requested = mutableListOf<mihon.reader.model.PageId>()
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            settings = ReaderSettings(mode = mihon.reader.model.ReadingMode.DUAL_LTR),
            visibleContent = { page ->
                requested += page
                null
            },
        )
        session.open(7)

        session.dispatch(ReaderAction.Next)
        runCurrent()

        session.state.value.selectedIndex shouldBe 2
        requested.last() shouldBe source.descriptors[2].id
    }

    @Test
    fun `intrinsic wide page size keeps dual page navigation aligned with split spreads`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 3))
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            settings = ReaderSettings(mode = ReadingMode.DUAL_LTR),
        )
        session.open(7)
        session.dispatch(ReaderAction.SetPageIntrinsicSize(source.descriptors[1].id, 1600, 900))
        runCurrent()

        session.state.value.pages[1].width shouldBe 1600
        session.dispatch(ReaderAction.Next)
        runCurrent()
        session.state.value.selectedIndex shouldBe 1
        session.dispatch(ReaderAction.Next)
        runCurrent()
        session.state.value.selectedIndex shouldBe 2
    }

    @Test
    fun `retry reopens a chapter after visible page decoding failed`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val descriptors = pages(7, 3)
        var shouldFail = true
        var sourceCreations = 0
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory {
                sourceCreations++
                FakeSource(asset, descriptors)
            },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            visibleContent = {
                if (shouldFail) error("decode failed")
                null
            },
            invalidateContent = { shouldFail = false },
        )

        session.open(7)
        runCurrent()
        session.state.value.loadState.shouldBeInstanceOf<ReaderLoadState.Failed>()

        session.retry(descriptors.first().id)
        runCurrent()

        session.state.value.loadState shouldBe ReaderLoadState.Ready
        session.state.value.error shouldBe null
        session.state.value.selectedIndex shouldBe 0
        sourceCreations shouldBe 2
    }

    @Test
    fun `session routes open visibility direction warmup metrics retry and close through content pipeline`() = runTest {
        val current = asset(chapterId = 7, lastPageRead = 0)
        val next = asset(chapterId = 8, lastPageRead = 0)
        val source = FakeSource(current, pages(7, 8))
        val pipeline = RecordingContentPipeline()
        val chapterCatalog = object : ReaderChapterCatalog {
            override fun chapterAsset(chapterId: Long) = when (chapterId) {
                7L -> current
                8L -> next
                else -> null
            }

            override fun adjacentReadableChapter(
                chapterId: Long,
                direction: mihon.reader.source.ChapterDirection,
            ) = next.takeIf { chapterId == 7L && direction == mihon.reader.source.ChapterDirection.NEXT }
        }
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = chapterCatalog,
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            contentPipeline = pipeline,
        )

        session.open(7)
        runCurrent()
        session.dispatch(ReaderAction.SelectPage(4))
        runCurrent()
        session.dispatch(ReaderAction.SetVisiblePages(listOf(source.descriptors[4].id, source.descriptors[5].id)))
        runCurrent()
        session.dispatch(ReaderAction.SelectPage(3))
        runCurrent()
        session.retry(source.descriptors[3].id)
        runCurrent()
        session.closeAndFlush()

        pipeline.opened shouldBe listOf(7L)
        pipeline.positions.first() shouldBe ReaderContentPosition(
            selectedIndex = 0,
            visiblePages = listOf(source.descriptors[0].id),
            mode = ReadingMode.SINGLE_LTR,
            direction = NavigationDirection.FORWARD,
            foreground = true,
            contentVisible = true,
        )
        pipeline.positions.any {
            it.selectedIndex == 4 && it.visiblePages == listOf(source.descriptors[4].id, source.descriptors[5].id)
        } shouldBe true
        pipeline.positions.last { it.selectedIndex == 3 }.direction shouldBe NavigationDirection.BACKWARD
        val nextWarmup = pipeline.warmups.last { it?.asset?.chapterId == 8L }!!
        nextWarmup.preloadFirstUnit shouldBe true
        nextWarmup.fromEnd shouldBe false
        nextWarmup.preloadCount shouldBe 3
        pipeline.retried shouldBe listOf(source.descriptors[3].id)
        session.state.value.cacheMetrics shouldBe pipeline.cacheMetrics
        pipeline.chapterCloses shouldBe 1
        pipeline.closed shouldBe 1
    }

    @Test
    fun `a failed chapter open clears the previous chapter and retries the attempted chapter`() = runTest {
        val first = asset(chapterId = 7, lastPageRead = 0)
        val attempted = asset(chapterId = 99, lastPageRead = 0)
        var failAttempt = true
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = object : ReaderChapterCatalog {
                override fun chapterAsset(chapterId: Long) = when (chapterId) {
                    7L -> first
                    99L -> attempted
                    else -> null
                }

                override fun adjacentReadableChapter(
                    chapterId: Long,
                    direction: mihon.reader.source.ChapterDirection,
                ): ReaderChapterAsset? = null
            },
            sourceFactory = ChapterSourceFactory { requested ->
                if (requested.chapterId == 99L && failAttempt) {
                    failAttempt = false
                    error("source unavailable")
                }
                FakeSource(requested, pages(requested.chapterId, 2))
            },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
        )

        session.open(7)
        runCurrent()
        session.state.value.chapterId shouldBe 7L

        session.open(99)
        runCurrent()

        val failed = session.state.value
        failed.loadState.shouldBeInstanceOf<ReaderLoadState.Failed>()
        failed.chapterId shouldBe 99L
        failed.pages.shouldBeEmpty()
        failed.selectedIndex shouldBe 0

        // The retry may carry a stale page id from the old chapter; it must still reopen chapter 99.
        session.retry(PageId("7", "page-0.jpg"))
        runCurrent()

        val reopened = session.state.value
        reopened.loadState shouldBe ReaderLoadState.Ready
        reopened.chapterId shouldBe 99L
        reopened.pages.size shouldBe 2
    }

    @Test
    fun `a malformed action does not kill the reader action consumer`() = runTest {
        val asset = asset(chapterId = 7, lastPageRead = 0)
        val source = FakeSource(asset, pages(7, 3))
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = catalog(asset),
            sourceFactory = ChapterSourceFactory { source },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
        )

        session.open(7)
        runCurrent()
        session.dispatch(ReaderAction.SetPageIntrinsicSize(source.descriptors[0].id, width = 0, height = 0))
        runCurrent()
        session.dispatch(ReaderAction.SelectPage(1))
        runCurrent()

        session.state.value.selectedIndex shouldBe 1
    }

    @Test
    fun `opening the next chapter waits for adjacent warmup to release`() = runTest {
        val current = asset(chapterId = 1, lastPageRead = 0)
        val next = asset(chapterId = 2, lastPageRead = 0)
        val events = mutableListOf<String>()
        val pipeline = object : RecordingContentPipeline() {
            override fun closeChapter() {
                super.closeChapter()
                events += "close"
            }

            override suspend fun awaitBackgroundRelease() {
                events += "release"
            }
        }
        val session = DefaultReaderSession(
            scope = backgroundScope,
            catalog = object : ReaderChapterCatalog {
                override fun chapterAsset(chapterId: Long) = when (chapterId) {
                    1L -> current
                    2L -> next
                    else -> null
                }

                override fun adjacentReadableChapter(
                    chapterId: Long,
                    direction: mihon.reader.source.ChapterDirection,
                ) = next.takeIf { chapterId == 1L && direction == mihon.reader.source.ChapterDirection.NEXT }
            },
            sourceFactory = ChapterSourceFactory { requested ->
                events += "create"
                FakeSource(requested, pages(requested.chapterId, 1))
            },
            progressSink = ReaderProgressSink { ProgressWriteResult.APPLIED },
            generationSource = AtomicReaderGenerationSource(),
            contentPipeline = pipeline,
        )

        session.open(1)
        runCurrent()
        session.open(2)
        runCurrent()

        events shouldBe listOf("release", "create", "close", "release", "create")
        session.closeAndFlush()
    }

    private fun pages(chapterId: Long, count: Int) =
        (0 until count).map { index ->
            mihon.reader.model.PageDescriptor(
                id = mihon.reader.model.PageId(chapterId.toString(), "page-$index.jpg"),
                width = 100,
                height = 200,
            )
        }

    private fun asset(chapterId: Long, lastPageRead: Long) = ReaderChapterAsset(
        mangaId = 1,
        chapterId = chapterId,
        mangaTitle = "manga",
        chapterName = "chapter",
        storageRoot = Path.of("."),
        relativePath = Path.of("chapter-$chapterId"),
        assetKind = "directory",
        sizeBytes = 1,
        modifiedAt = 1,
        lastPageRead = lastPageRead,
        read = false,
    )

    private fun catalog(asset: ReaderChapterAsset) = object : ReaderChapterCatalog {
        override fun chapterAsset(chapterId: Long) = asset.takeIf { it.chapterId == chapterId }

        override fun adjacentReadableChapter(
            chapterId: Long,
            direction: mihon.reader.source.ChapterDirection,
        ): ReaderChapterAsset? = null
    }

    private class FakeSource(
        override val asset: ReaderChapterAsset,
        val descriptors: List<mihon.reader.model.PageDescriptor>,
    ) : ChapterSource {
        var closeRequests = 0

        override suspend fun pages() = descriptors

        override suspend fun open(pageId: mihon.reader.model.PageId) =
            BoundedPageInput(ByteArrayInputStream(byteArrayOf()), 0)

        override fun close() {
            closeRequests++
        }
    }

    private class SuspendingOpeningPipeline : ReaderContentPipeline {
        var chapterCloses = 0

        override suspend fun open(source: ChapterSource): List<mihon.reader.model.PageDescriptor> = awaitCancellation()

        override fun updatePosition(position: ReaderContentPosition) = Unit

        override suspend fun loadVisible(pageId: PageId): AutoCloseable? = null

        override suspend fun retry(pageId: PageId): AutoCloseable? = null

        override fun metrics() = CacheMetrics(0, 0, 0, 0, 0, 0, 0, 0)

        override fun warmAdjacent(request: AdjacentChapterWarmup?) = Unit

        override fun closeChapter() {
            chapterCloses++
        }

        override fun close() = Unit
    }

    private open class RecordingContentPipeline : ReaderContentPipeline {
        val opened = mutableListOf<Long>()
        val positions = mutableListOf<ReaderContentPosition>()
        val warmups = mutableListOf<AdjacentChapterWarmup?>()
        val retried = mutableListOf<PageId>()
        val cacheMetrics = CacheMetrics(32, 16, 1, 2, 3, 4, 0, 32)
        var chapterCloses = 0
        var closed = 0

        override suspend fun open(source: ChapterSource): List<mihon.reader.model.PageDescriptor> {
            opened += source.asset.chapterId
            return source.pages()
        }

        override fun updatePosition(position: ReaderContentPosition) {
            positions += position
        }

        override suspend fun loadVisible(pageId: PageId): AutoCloseable? = null

        override suspend fun retry(pageId: PageId): AutoCloseable? {
            retried += pageId
            return null
        }

        override fun metrics() = cacheMetrics

        override fun warmAdjacent(request: AdjacentChapterWarmup?) {
            warmups += request
        }

        override fun closeChapter() {
            chapterCloses++
        }

        override fun close() {
            closed++
        }
    }
}
