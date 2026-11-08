package mihon.desktop.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mihon.desktop.library.model.LibraryChapter
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.source.ReaderChapterAsset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal suspend fun PackagedReaderVerifier.verifyInternal(requestedRoot: Path): ReaderVerificationSummary {
    val fixture = validateFixture(requestedRoot)
    val importReport = runtime.localImporter.import(fixture.mangaRoot, runtime.localLibraryRoot, nowMillis())
    check(importReport.status.name == "SUCCEEDED") { "reader fixture import failed" }

    val manga = runtime.library.librarySnapshot().singleOrNull { it.title == READER_FIXTURE_MANGA }
        ?: error("reader fixture manga was not imported")
    val chapters = runtime.library.chapterSnapshot(manga.id).sortedBy { it.name }
    check(chapters.map { it.name }.toSet() == EXPECTED_CHAPTER_NAMES) {
        "reader fixture chapter matrix is incomplete"
    }
    val assets = chapters.associateWith { chapter ->
        runtime.library.chapterAsset(chapter.id) ?: error("reader fixture chapter has no local asset")
    }
    val factory = requireNotNull(runtime.readerFactory) { "reader verification runtime has no reader factory" }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val memoryHighWater = CoreMemoryHighWater { factory.memoryBudget.metrics }
    val pageCounts = linkedMapOf<Long, Int>()
    val verifiedAssets = linkedSetOf<String>()
    val gifHashes = linkedSetOf<String>()
    var decodedTileCount = 0
    var corruptPageObserved = false
    var decodedAfterCorrupt = false
    try {
        val standaloneAsset = ReaderChapterAsset(
            mangaId = manga.id,
            chapterId = STANDALONE_CHAPTER_ID,
            mangaTitle = READER_FIXTURE_MANGA,
            chapterName = "standalone",
            storageRoot = fixture.root,
            relativePath = Path.of(READER_FIXTURE_STANDALONE),
            assetKind = "IMAGE",
            sizeBytes = Files.size(fixture.standalone),
            modifiedAt = Files.getLastModifiedTime(fixture.standalone, LinkOption.NOFOLLOW_LINKS).toMillis(),
            lastPageRead = 0,
            read = false,
        )
        this.verifyAsset("standalone", standaloneAsset, factory, scope).also { result ->
            verifiedAssets += "standalone"
            decodedTileCount += result.decodedTileCount
            gifHashes += result.gifFrameHashes
            corruptPageObserved = corruptPageObserved || result.corruptPageObserved
            decodedAfterCorrupt = decodedAfterCorrupt || result.decodedAfterCorrupt
        }
        assets.entries.sortedBy { it.key.name }.forEach { (chapter, asset) ->
            val label = assetLabel(chapter.name)
            this.verifyAsset(label, asset, factory, scope).also { result ->
                verifiedAssets += label
                decodedTileCount += result.decodedTileCount
                gifHashes += result.gifFrameHashes
                corruptPageObserved = corruptPageObserved || result.corruptPageObserved
                decodedAfterCorrupt = decodedAfterCorrupt || result.decodedAfterCorrupt
                pageCounts[chapter.id] = result.pageCount
            }
        }
        check(verifiedAssets == EXPECTED_ASSET_LABELS) { "reader asset matrix was not fully verified" }
        check(gifHashes.size == 2) { "animated GIF frames did not produce two distinct hashes" }
        check(corruptPageObserved && decodedAfterCorrupt) { "corrupt-page isolation was not observed" }

        val targetBefore = chapters.single { it.name == TARGET_CHAPTER_NAME }
        val targetPageCount = checkNotNull(pageCounts[targetBefore.id])
        check(targetPageCount >= 5) { "progress chapter does not contain enough pages" }
        val (phase, desiredPage) = when {
            targetBefore.read -> "final-completion" to targetPageCount - 1
            targetBefore.lastPageRead == 0L -> "initial-open" to 1
            targetBefore.lastPageRead == 1L -> "reopen-continue" to 2
            else -> "final-completion" to targetPageCount - 1
        }
        exerciseSessions(
            chapters = chapters,
            pageCounts = pageCounts,
            targetChapterId = targetBefore.id,
            expectedRestoredPage = targetBefore.lastPageRead.toInt().coerceAtMost(targetPageCount - 1),
            desiredPage = desiredPage,
            factory = factory,
        )

        val rows = runtime.library.chapterSnapshot(manga.id).sortedBy { it.name }.map { chapter ->
            ReaderProgressRow(
                chapterId = chapter.id,
                chapterName = chapter.name,
                pageIndex = chapter.lastPageRead,
                pageCount = checkNotNull(pageCounts[chapter.id]),
                completed = chapter.read,
            )
        }
        val targetAfter = rows.single { it.chapterId == targetBefore.id }
        check(targetAfter.pageIndex == desiredPage.toLong()) { "reader progress page was not persisted" }
        check(targetAfter.completed == (desiredPage == targetPageCount - 1)) {
            "reader progress completion state was not persisted"
        }
        val soakSeconds = ReaderSoak.seconds(environment)
        val soak = if (soakSeconds > 0) {
            val samples = environment["MIHON_W_READER_SOAK_OUTPUT"]?.let(Path::of)
                ?: fixture.root.resolve("reader-soak-${ProcessHandle.current().pid()}-${System.nanoTime()}.jsonl")
            ReaderSoak.run(soakSeconds, samples, { factory.memoryBudget.metrics }) {
                // Evict unpinned tiles so each cycle performs decoding, not only cache lookup.
                factory.cache.relievePressure()
                var tiles = this.verifyAsset("standalone", standaloneAsset, factory, scope).decodedTileCount
                for ((chapter, asset) in assets) {
                    tiles +=
                        this.verifyAsset(assetLabel(chapter.name), asset, factory, scope).decodedTileCount
                }
                exerciseSessions(chapters, pageCounts, targetBefore.id, desiredPage, desiredPage, factory)
                tiles
            }
        } else {
            null
        }
        val coreHighWater = memoryHighWater.stop()
        check(coreHighWater in 1..factory.memoryBudget.metrics.limitBytes) {
            "core resident and in-flight high-water is outside the reader budget"
        }
        return ReaderVerificationSummary(
            phase = phase,
            fixtureManifestSha256 = fixture.manifestSha256,
            verifiedAssets = verifiedAssets.toList(),
            verifiedModes = ReadingMode.entries.map { it.name },
            decodedTileCount = decodedTileCount,
            gifFrameHashes = gifHashes.toList(),
            cacheResidentHighWaterBytes = factory.cache.metrics.highWaterBytes,
            coreResidentAndInFlightHighWaterBytes = coreHighWater,
            progressRows = rows,
            soak = soak,
        )
    } finally {
        memoryHighWater.close()
        scope.cancel()
    }
}

internal suspend fun PackagedReaderVerifier.exerciseSessions(
    chapters: List<LibraryChapter>,
    pageCounts: Map<Long, Int>,
    targetChapterId: Long,
    expectedRestoredPage: Int,
    desiredPage: Int,
    factory: mihon.desktop.reader.DesktopReaderFactory,
) {
    check(chapters.size == ReadingMode.entries.size) { "fixture must map one chapter to each reading mode" }
    val session = factory.createSession()
    try {
        // The committed RAR's only page is deliberately undecodable. Opening it publishes Ready and
        // then, once the visible page decode fails, Failed(PAGE_DECODE_FAILED) — so waiting for a
        // stable Ready on it raced the decoder and timed out intermittently on CI. Modes are
        // exercised on the decodable chapters and the RAR chapter on its deterministic outcome.
        val undecodable = chapters.single { assetLabel(it.name) == UNDECODABLE_SESSION_ASSET }
        val decodable = chapters - undecodable
        ReadingMode.entries.forEachIndexed { index, mode ->
            val chapter = decodable[index % decodable.size]
            session.open(chapter.id)
            val ready = session.awaitReady(chapter.id)
            check(ready.pages.size == pageCounts.getValue(chapter.id)) { "session/source page counts disagree" }
            session.dispatch(ReaderAction.ChangeMode(mode))
            session.awaitState {
                it.chapterId == chapter.id && it.mode == mode &&
                    it.loadState is ReaderLoadState.Ready
            }
        }
        session.open(undecodable.id)
        val failed = session.awaitState {
            it.chapterId == undecodable.id &&
                (it.loadState as? ReaderLoadState.Failed)?.error?.code == ReaderErrorCode.PAGE_DECODE_FAILED
        }
        check(failed.pages.size == pageCounts.getValue(undecodable.id)) { "session/source page counts disagree" }

        val transition = chapters.single { it.name == TRANSITION_CHAPTER_NAME }
        session.open(transition.id)
        val transitionReady = session.awaitReady(transition.id)
        check(transitionReady.hasNextChapter) { "transition chapter has no next readable chapter" }
        session.dispatch(ReaderAction.SelectPage(transitionReady.pages.lastIndex))
        session.awaitState { it.chapterId == transition.id && it.selectedIndex == transitionReady.pages.lastIndex }
        session.dispatch(ReaderAction.Next)
        // The next chapter can be the undecodable RAR, which settles on its typed decode failure
        // rather than a stable Ready; accept that deterministic outcome instead of racing it.
        session.awaitState {
            it.chapterId != transition.id && it.chapterId != null &&
                (
                    it.loadState is ReaderLoadState.Ready ||
                        (
                            it.chapterId == undecodable.id &&
                                (it.loadState as? ReaderLoadState.Failed)?.error?.code ==
                                ReaderErrorCode.PAGE_DECODE_FAILED
                            )
                    )
        }

        session.open(targetChapterId)
        val restored = session.awaitReady(targetChapterId)
        check(restored.selectedIndex == expectedRestoredPage) { "reader did not restore persisted progress" }
        session.dispatch(ReaderAction.SelectPage(desiredPage))
        session.awaitState { it.chapterId == targetChapterId && it.selectedIndex == desiredPage }
        session.flushProgress()
    } finally {
        session.closeAndFlush()
    }

    val reopened = factory.createSession()
    try {
        reopened.open(targetChapterId)
        check(reopened.awaitReady(targetChapterId).selectedIndex == desiredPage) {
            "reader did not reopen at the flushed page"
        }
    } finally {
        reopened.closeAndFlush()
    }
}
