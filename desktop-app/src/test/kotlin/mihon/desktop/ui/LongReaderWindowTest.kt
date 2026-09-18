package mihon.desktop.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mihon.desktop.DesktopRuntimeFactory
import mihon.desktop.configureDesktopRendering
import mihon.desktop.reader.LongReaderFixture
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.ReaderPageImageStore
import mihon.desktop.ui.reader.ReaderScreen
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.source.ChapterDirection
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.math.ceil

/** Opt-in native reader probe; core actions do not claim keyboard/mouse or GPU presentation coverage. */
@EnabledIfEnvironmentVariable(named = "MIHON_READER_WINDOW_REPORT", matches = ".+")
class LongReaderWindowTest {
    @Test
    fun `decode and display long chapters through the actual reader window`(): Unit = runBlocking {
        val root = Path.of(System.getenv("MIHON_READER_WINDOW_REPORT")).toAbsolutePath()
        Files.createDirectory(root)
        val readerOrigin = Class.forName("mihon.desktop.ui.reader.ReaderScreenKt").protectionDomain.codeSource.location
        val coreOrigin = Class.forName("mihon.reader.session.DefaultReaderSession").protectionDomain.codeSource.location
        System.getenv("MIHON_READER_WINDOW_APP")?.let { expected ->
            listOf(readerOrigin, coreOrigin).forEach { origin ->
                check(Path.of(origin.toURI()).startsWith(Path.of(expected).toAbsolutePath())) {
                    "Expected packaged classes under $expected, got $origin"
                }
            }
        }
        Files.writeString(
            root.resolve("window-identity.json"),
            buildJsonObject {
                put("processId", ProcessHandle.current().pid())
                put("readerOrigin", readerOrigin.toString())
                put("coreOrigin", coreOrigin.toString())
                put(
                    "javaArguments",
                    JsonArray(ManagementFactory.getRuntimeMXBean().inputArguments.map(::JsonPrimitive)),
                )
                put("maxHeapBytes", Runtime.getRuntime().maxMemory())
            }.toString(),
        )
        configureDesktopRendering()
        val mangaRoot = LongReaderFixture.build(root.resolve("fixture"))
        DesktopRuntimeFactory.create(
            arrayOf("--verify-reader=$mangaRoot", "--data-dir=${root.resolve("profile")}"),
            mapOf("MIHON_W_READER_VERIFY" to "1"),
            root.resolve("bin"),
        ).use { runtime ->
            check(
                runtime.localImporter.import(
                    mangaRoot,
                    runtime.localLibraryRoot,
                    System.currentTimeMillis(),
                ).status.name ==
                    "SUCCEEDED",
            )
            val manga = runtime.library.librarySnapshot().single { it.title == LongReaderFixture.TITLE }
            val imported = runtime.library.chapterSnapshot(manga.id)
            check(imported.size == 2)
            val first = imported.single {
                runtime.library.adjacentReadableChapter(it.id, ChapterDirection.PREVIOUS) == null
            }
            val nextId = requireNotNull(
                runtime.library.adjacentReadableChapter(first.id, ChapterDirection.NEXT),
            ).chapterId
            val chapters = listOf(first, imported.single { it.id == nextId })
            val factory = requireNotNull(runtime.readerFactory)
            val handle = factory.createHandle()
            val session = handle.session
            val images = ReaderPageImageStore()
            val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val frames = mutableListOf<Long>()
            val observations = mutableListOf<JsonObject>()
            var attemptedVisit: JsonObject? = null
            lateinit var window: ComposeWindow
            SwingUtilities.invokeAndWait {
                window = ComposeWindow().apply {
                    title = "mihondesk long chapter reader validation"
                    setSize(1280, 800)
                    setLocationRelativeTo(null)
                    setContent {
                        MihonDesktopTheme {
                            ReaderScreen(
                                session = session,
                                title = LongReaderFixture.TITLE,
                                chapterTitle = "256-page directory and CBZ",
                                settingsStore = null,
                                onBack = {},
                                pageImageStore = images,
                                pageContent = { page, _, modifier ->
                                    DecodedReaderPage(handle.content, page, modifier)
                                },
                            )
                            LaunchedEffect(Unit) {
                                val clock = launch {
                                    while (isActive) frames += withFrameNanos { it }
                                }
                                suspend fun ready(chapter: Long) = withTimeout(15_000) {
                                    session.state.first {
                                        it.chapterId == chapter &&
                                            it.loadState is ReaderLoadState.Ready
                                    }
                                }
                                suspend fun visit(chapter: Long, index: Int, phase: String, action: ReaderAction) {
                                    attemptedVisit = buildJsonObject {
                                        put("chapterId", chapter)
                                        put("pageIndex", index)
                                        put("phase", phase)
                                    }
                                    val started = System.nanoTime()
                                    withContext(Dispatchers.Default) { session.dispatch(action) }
                                    val selected = withTimeout(15_000) {
                                        session.state.first {
                                            it.chapterId == chapter && it.selectedIndex == index &&
                                                it.loadState is ReaderLoadState.Ready
                                        }
                                    }
                                    val page = selected.pages[index].id
                                    withTimeout(15_000) {
                                        while (images.get(page) == null) {
                                            session.state.value.error?.let { error ->
                                                throw IllegalStateException("Reader failed: $error", error.cause)
                                            }
                                            delay(5)
                                        }
                                    }
                                    val imageAvailableAfterMs = (System.nanoTime() - started) / 1_000_000.0
                                    repeat(2) { withFrameNanos { } }
                                    check(session.state.value.error == null) {
                                        "Reader failed: ${session.state.value.error}"
                                    }
                                    val core = factory.memoryBudget.metrics
                                    val bridge = factory.bridge.metrics
                                    check(core.reservedBytes + core.cacheBytes <= core.limitBytes)
                                    check(bridge.retainedBytes <= bridge.limitBytes)
                                    observations += buildJsonObject {
                                        put("phase", phase)
                                        put("chapterId", chapter)
                                        put("pageIndex", index)
                                        put("mode", session.state.value.mode.name)
                                        put("startedNanos", started)
                                        put("imageAvailableAfterMs", imageAvailableAfterMs)
                                        put("twoCallbacksAfterImageMs", (System.nanoTime() - started) / 1_000_000.0)
                                        put("coreReservedBytes", core.reservedBytes)
                                        put("coreCacheBytes", core.cacheBytes)
                                        put("bridgeRetainedBytes", bridge.retainedBytes)
                                        put("bridgeHighWaterBytes", bridge.highWaterBytes)
                                        put("composedImages", images.size)
                                    }
                                }
                                try {
                                    withContext(Dispatchers.Default) { session.open(chapters.first().id) }
                                    check(ready(chapters.first().id).pages.size == LongReaderFixture.PAGE_COUNT)
                                    withContext(Dispatchers.Default) {
                                        session.dispatch(ReaderAction.ChangeMode(ReadingMode.SINGLE_LTR))
                                    }
                                    for ((chapterIndex, chapter) in chapters.withIndex()) {
                                        if (chapterIndex > 0) {
                                            visit(chapter.id, 0, "next-chapter", ReaderAction.Next)
                                            check(ready(chapter.id).pages.size == LongReaderFixture.PAGE_COUNT)
                                        }
                                        for (index in 0 until LongReaderFixture.PAGE_COUNT) {
                                            val action = if (index ==
                                                0
                                            ) {
                                                ReaderAction.SelectPage(0)
                                            } else {
                                                ReaderAction.Next
                                            }
                                            visit(chapter.id, index, "forward", action)
                                        }
                                    }
                                    visit(chapters.last().id, 0, "return-start", ReaderAction.SelectPage(0))
                                    visit(
                                        chapters.first().id,
                                        LongReaderFixture.PAGE_COUNT - 1,
                                        "previous-chapter",
                                        ReaderAction.Previous,
                                    )
                                    for (mode in ReadingMode.entries) {
                                        withContext(Dispatchers.Default) {
                                            session.dispatch(ReaderAction.ChangeMode(mode))
                                        }
                                        for (index in listOf(0, 1, 127, 128, 254, 255, 128, 1)) {
                                            visit(
                                                chapters.first().id,
                                                index,
                                                "mode-jumps",
                                                ReaderAction.SelectPage(index),
                                            )
                                        }
                                    }
                                    visit(chapters.first().id, 173, "persist", ReaderAction.SelectPage(173))
                                    withContext(Dispatchers.Default) { session.flushProgress() }
                                } catch (error: Throwable) {
                                    failure.set(error)
                                } finally {
                                    withContext(NonCancellable) {
                                        clock.cancelAndJoin()
                                        done.countDown()
                                    }
                                }
                            }
                        }
                    }
                    isVisible = true
                    toFront()
                    requestFocus()
                }
            }
            try {
                assertTrue(done.await(600, TimeUnit.SECONDS), "Reader window did not finish")
            } finally {
                val lastState = session.state.value
                var renderer = "unknown"
                var displayHz = 0
                SwingUtilities.invokeAndWait {
                    renderer = window.renderApi.toString()
                    displayHz = window.graphicsConfiguration.device.displayMode.refreshRate
                    window.dispose()
                }
                check(done.await(15, TimeUnit.SECONDS)) { "Reader coroutine did not terminate after window disposal" }
                session.closeAndFlush()
                val intervals = frames.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.sorted()
                val report = buildJsonObject {
                    put("windowStageStatus", if (failure.get() == null) "SUCCEEDED" else "FAILED")
                    put(
                        "acceptance",
                        "Window stage only; progress-restored.txt and a successful test exit are also required",
                    )
                    put("failure", failure.get()?.stackTraceToString().orEmpty())
                    put(
                        "lastReaderState",
                        "chapter=${lastState.chapterId}, page=${lastState.selectedIndex}, load=${lastState.loadState}, error=${lastState.error}",
                    )
                    attemptedVisit?.let { put("attemptedVisit", it) }
                    put("processId", ProcessHandle.current().pid())
                    put("javaVersion", System.getProperty("java.version"))
                    put("maxHeapBytes", Runtime.getRuntime().maxMemory())
                    put("readerOrigin", readerOrigin.toString())
                    put("coreOrigin", coreOrigin.toString())
                    put("renderer", renderer)
                    put("displayHz", displayHz)
                    put("callbackCount", frames.size)
                    put("callbackMetric", "display-clock callbacks; not GPU presentation FPS")
                    if (intervals.isNotEmpty()) {
                        put("callbackP95Ms", intervals[ceil(intervals.size * 0.95).toInt() - 1])
                        put("callbackMaxMs", intervals.last())
                    }
                    put("visits", JsonArray(observations))
                }
                Files.writeString(root.resolve("window-report.json"), report.toString())
                Files.write(root.resolve("callback-nanos.txt"), frames.map(Long::toString))
            }
            failure.get()?.let { throw it }
            val reopened = factory.createSession()
            try {
                reopened.open(chapters.first().id)
                val state = withTimeout(15_000) { reopened.state.first { it.loadState is ReaderLoadState.Ready } }
                check(state.selectedIndex == 173) { "Persisted page was not restored: ${state.selectedIndex}" }
                Files.writeString(
                    root.resolve("progress-restored.txt"),
                    "chapter=${state.chapterId}, page=${state.selectedIndex}\n",
                )
            } finally {
                reopened.closeAndFlush()
            }
        }
    }
}
