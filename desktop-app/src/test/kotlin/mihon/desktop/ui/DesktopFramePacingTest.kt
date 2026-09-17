package mihon.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jdk.jfr.Recording
import mihon.desktop.configureDesktopRendering
import org.jetbrains.skiko.SkiaLayer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Opt-in real-window measurement; uses the display clock, never Compose's virtual test clock. */
@EnabledIfEnvironmentVariable(named = "MIHON_FRAME_REPORT", matches = ".+")
class DesktopFramePacingTest {
    @Test
    fun `measure continuous animation on the current display`() {
        val renderers = System.getenv("MIHON_FRAME_RENDERER")?.split(',') ?: listOf(null)
        renderers.forEachIndexed { index, renderer ->
            renderer?.let { System.setProperty("skiko.renderApi", it) }
            measure(index, renderers.size)
        }
    }

    private fun measure(index: Int, count: Int) {
        System.getenv("MIHON_FRAME_NATIVE_DIR")?.let { System.setProperty("skiko.library.path", it) }
        if (System.getenv("MIHON_FRAME_APPLICATION_DEFAULTS") == "1") {
            configureDesktopRendering()
            val origin = Path.of(
                Class.forName("mihon.desktop.DesktopRenderingKt").protectionDomain.codeSource.location.toURI(),
            )
            System.getenv("MIHON_FRAME_INSTALLED_APP")?.let {
                assertTrue(origin.startsWith(Path.of(it)), "Expected installed rendering configuration, got $origin")
            }
            println("RENDERING_CONFIGURATION_ORIGIN=$origin")
        }
        val recording = System.getenv("MIHON_FRAME_PROFILING")?.let {
            Recording().apply {
                enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1))
                enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(1))
                enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1))
                enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1))
                enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1))
                start()
            }
        }
        val done = CountDownLatch(1)
        val samples = mutableListOf<Long>()
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                title = "mihondesk frame pacing probe"
                setSize(1280, 800)
                setLocationRelativeTo(null)
                setContent {
                    val position = remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(Unit) {
                        repeat(480) { index ->
                            val frame = withFrameNanos { it }
                            if (index >= 120) samples += frame
                            position.floatValue = (index % 120) / 120f
                        }
                        done.countDown()
                    }
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(0xFF151515))
                            drawCircle(
                                Color(0xFFADC8FF),
                                24f,
                                Offset(size.width * position.floatValue, size.height / 2),
                            )
                        }
                        if (System.getenv("MIHON_FRAME_TEXT_SMOKE") == "1") {
                            Text(
                                "动画与文字渲染验证 · Animation rendering check",
                                modifier = Modifier.padding(32.dp),
                                color = Color.White,
                                fontSize = 24.sp,
                            )
                        }
                    }
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }
        try {
            assertTrue(done.await(40, TimeUnit.SECONDS), "Animation did not produce frames")
            fun layers(container: Container): List<SkiaLayer> = container.components.flatMap {
                when (it) {
                    is SkiaLayer -> listOf(it)
                    is Container -> layers(it)
                    else -> emptyList()
                }
            }
            println("RENDER_INFO=" + layers(window).joinToString { it.renderInfo })
            println("WINDOW_FOCUSED=" + window.isFocused)
            System.getenv("MIHON_FRAME_EXPECTED_API")?.let {
                assertTrue(window.renderApi.toString() == it, "Expected $it, got ${window.renderApi}")
            }
            val intervals = samples.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.sorted()
            val mean = intervals.average()
            val report = """
                {
                  "displayHz": ${window.graphicsConfiguration.device.displayMode.refreshRate},
                  "renderer": "${window.renderApi}",
                  "frames": ${samples.size},
                  "meanMs": $mean,
                  "p50Ms": ${intervals[intervals.size / 2]},
                  "p95Ms": ${intervals[(intervals.size * 0.95).toInt()]},
                  "maxMs": ${intervals.last()},
                  "effectiveFps": ${1000 / mean},
                  "over20ms": ${intervals.count { it > 20 }}
                }
            """.trimIndent()
            val base = Path.of(System.getenv("MIHON_FRAME_REPORT"))
            val output = if (count == 1) base else base.resolveSibling("${base.fileName}-$index.json")
            Files.createDirectories(output.toAbsolutePath().parent)
            Files.writeString(output, report)
            println(report)
            System.getenv("MIHON_FRAME_HOLD_SECONDS")?.toLongOrNull()?.let {
                CountDownLatch(1).await(it, TimeUnit.SECONDS)
            }
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
            recording?.run {
                stop()
                dump(Path.of(System.getenv("MIHON_FRAME_PROFILING")))
                close()
            }
        }
    }
}
