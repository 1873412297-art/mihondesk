package mihon.desktop.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore
import java.awt.BasicStroke
import java.awt.EventQueue
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.JWindow

const val FIRST_RUN_SPLASH_TEST_TAG = "first-run-splash"

/** Splash v3 palette: warm paper + ink, with a terracotta ink drop. */
internal data class SplashPalette(val bg: Color, val bg2: Color, val ink: Color, val accent: Color, val page: Color) {
    companion object {
        val Light = SplashPalette(
            Color(0xFFF6F1E7),
            Color(0xFFEFE7D8),
            Color(0xFF2B2622),
            Color(0xFFB0623B),
            Color(0xFFFFFAF1),
        )
        val Dark = SplashPalette(
            Color(0xFF1B1A19),
            Color(0xFF141312),
            Color(0xFFE9E3D8),
            Color(0xFFD98A5F),
            Color(0xFF262422),
        )

        fun of(dark: Boolean) = if (dark) Dark else Light
    }
}

/**
 * The approved splash v3 shown once per profile: the first launch after install (or after upgrading
 * to 0.3.0). Two phases: [showBootWindow] paints the static mark in plain AWT before the heavy runtime
 * initialisation, then [FirstRunSplashOverlay] plays the animation over the first Compose frame and
 * closes the boot window so the hand-off is seamless. Never shown for tray restore, single-instance
 * re-activation, unlock or background commands.
 */
object FirstRunSplash {
    const val SHOWN_KEY = "splash.v3.shown"
    private const val BOOT_WINDOW_SIZE = 320

    @Volatile
    private var bootWindow: JWindow? = null

    fun shouldShow(preferences: DesktopPreferenceStore): Boolean =
        runCatching { preferences.property(SHOWN_KEY) == null }.getOrDefault(false)

    fun markShown(preferences: DesktopPreferenceStore) {
        runCatching { preferences.update { setProperty(SHOWN_KEY, "true") } }
            .onFailure { DesktopLogger.warn("Splash", "Could not persist splash state: ${it.message}") }
    }

    /** Clears the shown flag so the next launch (or an immediate overlay) plays the splash again. */
    fun reset(preferences: DesktopPreferenceStore) {
        runCatching { preferences.update { remove(SHOWN_KEY) } }
            .onFailure { DesktopLogger.warn("Splash", "Could not reset splash state: ${it.message}") }
    }

    /** Phase 0: a tiny undecorated AWT window with the static mark; safe to call from any thread. */
    fun showBootWindow(dark: Boolean) {
        if (GraphicsEnvironment.isHeadless()) return
        runCatching {
            EventQueue.invokeAndWait {
                val palette = SplashPalette.of(dark)
                val window = JWindow()
                window.contentPane = BootMark(palette)
                window.setSize(BOOT_WINDOW_SIZE, BOOT_WINDOW_SIZE)
                window.setLocationRelativeTo(null)
                window.isAlwaysOnTop = false
                window.isVisible = true
                bootWindow = window
            }
        }.onFailure { DesktopLogger.warn("Splash", "Boot splash unavailable: ${it.message}") }
    }

    fun closeBootWindow() {
        val window = bootWindow ?: return
        bootWindow = null
        EventQueue.invokeLater { window.dispose() }
    }

    private class BootMark(private val palette: SplashPalette) : JComponent() {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
                g2.paint = GradientPaint(0f, 0f, palette.bg.awt(), 0f, height.toFloat(), palette.bg2.awt())
                g2.fillRect(0, 0, width, height)
                val markSize = 132.0
                g2.translate((width - markSize) / 2, (height - markSize) / 2)
                listOf(leftPage(), rightPage()).forEach { page ->
                    g2.color = palette.page.awt()
                    g2.fill(page)
                    g2.color = palette.ink.awt()
                    g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(page)
                }
                g2.drawLine(66, 50, 66, 104)
                g2.color = palette.accent.awt()
                g2.fill(Ellipse2D.Double(58.0, 25.0, 16.0, 16.0))
                g2.fill(
                    Path2D.Double().apply {
                        moveTo(66.0, 18.0)
                        lineTo(58.4, 31.0)
                        lineTo(73.6, 31.0)
                        closePath()
                    },
                )
            } finally {
                g2.dispose()
            }
        }

        private fun leftPage() = Path2D.Double().apply {
            moveTo(66.0, 50.0)
            curveTo(52.0, 42.0, 36.0, 42.0, 22.0, 46.0)
            lineTo(22.0, 100.0)
            curveTo(36.0, 96.0, 52.0, 96.0, 66.0, 104.0)
            closePath()
        }

        private fun rightPage() = Path2D.Double().apply {
            moveTo(66.0, 50.0)
            curveTo(80.0, 42.0, 96.0, 42.0, 110.0, 46.0)
            lineTo(110.0, 100.0)
            curveTo(96.0, 96.0, 80.0, 96.0, 66.0, 104.0)
            closePath()
        }

        private fun Color.awt() = java.awt.Color(red, green, blue, alpha)
    }
}

private val EnterEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)
private val DropEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

/**
 * Phase 1: the animated mark (1.1s fade+scale, ink drop from 0.7s for 0.9s, ~1.9s total, then a
 * 400ms fade). Click or Esc skips. With reduced motion the static mark is shown briefly instead.
 */
@Composable
fun FirstRunSplashOverlay(
    dark: Boolean,
    reducedMotion: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SplashPalette.of(dark)
    val mark = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val drop = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val bloom = remember { Animatable(if (reducedMotion) 0.55f else 0f) }
    val overlay = remember { Animatable(1f) }
    val skip = remember { kotlinx.coroutines.CompletableDeferred<Unit>() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // The Compose frame is up: hand off from the AWT boot window.
        FirstRunSplash.closeBootWindow()
        runCatching { focusRequester.requestFocus() }
        val play = launch {
            if (reducedMotion) {
                delay(600)
            } else {
                coroutineScope {
                    launch {
                        bloom.animateTo(1f, tween(720, easing = LinearEasing))
                        bloom.animateTo(0.55f, tween(1080, easing = LinearEasing))
                    }
                    launch { mark.animateTo(1f, tween(1100, delayMillis = 150, easing = EnterEasing)) }
                    launch { drop.animateTo(1f, tween(900, delayMillis = 700, easing = DropEasing)) }
                    delay(1900)
                }
            }
        }
        kotlinx.coroutines.selects.select {
            play.onJoin {}
            skip.onAwait {}
        }
        play.cancel()
        overlay.animateTo(0f, tween(if (reducedMotion) 0 else 400))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlay.value)
            .background(Brush.radialGradient(listOf(palette.bg, palette.bg2)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                skip.complete(Unit)
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.Escape) skip.complete(Unit)
                true
            }
            .semantics { contentDescription = "mihondesk" }
            .testTag(FIRST_RUN_SPLASH_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(132.dp)) {
            val unit = size.minDimension / 132f
            val glowRadius = size.minDimension / 2f + 60.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to palette.accent.copy(alpha = if (dark) 0.18f else 0.16f),
                    0.65f to Color.Transparent,
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                alpha = bloom.value,
            )
            val markScale = 0.94f + 0.06f * mark.value
            scale(markScale, pivot = center) {
                drawBook(palette, unit, alpha = mark.value)
            }
            val dropScale = 0.6f + 0.4f * drop.value
            translate(top = (1f - drop.value) * -8f * unit) {
                scale(dropScale, pivot = Offset(66f * unit, 30f * unit)) {
                    drawPath(dropPath(unit), palette.accent, alpha = drop.value)
                }
            }
        }
    }
}

private fun DrawScope.drawBook(palette: SplashPalette, unit: Float, alpha: Float) {
    val stroke = Stroke(width = 2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    listOf(pagePath(unit, left = true), pagePath(unit, left = false)).forEach { page ->
        drawPath(page, palette.page, alpha = alpha)
        drawPath(page, palette.ink, alpha = alpha, style = stroke)
    }
    drawLine(
        palette.ink,
        Offset(66f * unit, 50f * unit),
        Offset(66f * unit, 104f * unit),
        strokeWidth = 2.2f * unit,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
}

private fun pagePath(unit: Float, left: Boolean): Path {
    fun x(v: Float) = (if (left) v else 132f - v) * unit
    fun y(v: Float) = v * unit
    return Path().apply {
        moveTo(x(66f), y(50f))
        cubicTo(x(52f), y(42f), x(36f), y(42f), x(22f), y(46f))
        lineTo(x(22f), y(100f))
        cubicTo(x(36f), y(96f), x(52f), y(96f), x(66f), y(104f))
        close()
    }
}

private fun dropPath(unit: Float): Path = Path().apply {
    // SVG: M66 18 C66 18 58 28 58 33 a8 8 0 0 0 16 0 C74 28 66 18 66 18Z
    moveTo(66f * unit, 18f * unit)
    cubicTo(66f * unit, 18f * unit, 58f * unit, 28f * unit, 58f * unit, 33f * unit)
    arcTo(
        androidx.compose.ui.geometry.Rect(58f * unit, 25f * unit, 74f * unit, 41f * unit),
        startAngleDegrees = 180f,
        sweepAngleDegrees = -180f,
        forceMoveTo = false,
    )
    cubicTo(74f * unit, 28f * unit, 66f * unit, 18f * unit, 66f * unit, 18f * unit)
    close()
}
