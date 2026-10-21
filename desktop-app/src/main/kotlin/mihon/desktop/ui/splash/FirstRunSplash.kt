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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore

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
 * Splash v3: always on the first launch of a profile, and on every launch while the Appearance
 * setting "play splash on every launch" is on (the default). It is painted inside the real main
 * window frame from the very first frame (see [StartupWindow]); slow runtime initialisation runs in
 * the background and the splash crossfades into the library once both the animation and the
 * runtime are done. Never shown for tray restore, single-instance re-activation or background commands.
 */
object FirstRunSplash {
    const val SHOWN_KEY = "splash.v3.shown"

    fun shouldShow(preferences: DesktopPreferenceStore): Boolean =
        runCatching {
            preferences.property(SHOWN_KEY) == null || preferences.load().splashEveryLaunch
        }.getOrDefault(false)

    fun markShown(preferences: DesktopPreferenceStore) {
        runCatching { preferences.update { setProperty(SHOWN_KEY, "true") } }
            .onFailure { DesktopLogger.warn("Splash", "Could not persist splash state: ${it.message}") }
    }

    /** Clears the shown flag so the next launch (or an immediate overlay) plays the splash again. */
    fun reset(preferences: DesktopPreferenceStore) {
        runCatching { preferences.update { remove(SHOWN_KEY) } }
            .onFailure { DesktopLogger.warn("Splash", "Could not reset splash state: ${it.message}") }
    }
}

/** Where a splash resumes when it is handed from the startup window to the main window. */
enum class SplashResume {
    /** The animation already played: show its final frame. */
    FinalFrame,

    /** The animation was skipped or disabled: show the minimal loading state. */
    Loading,
}

/** Hand-off from the startup window: [onVisible] runs once the main window painted the splash. */
class StartupHandoff(val skipped: Boolean, val onVisible: () -> Unit)

private val EnterEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)
private val DropEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

/** In-app replay (onboarding): plays the animation, then fades out. */
@Composable
fun FirstRunSplashOverlay(
    dark: Boolean,
    reducedMotion: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StartupSplash(
        dark = dark,
        reducedMotion = reducedMotion,
        ready = true,
        onFinished = { onFinished() },
        modifier = modifier,
    )
}

/**
 * The animated mark (1.1s fade+scale, ink drop from 0.7s for 0.9s, ~1.9s total). Click or any key
 * skips. While [ready] is false after the animation the final frame holds with a subtle progress
 * ring (or just the ring when skipped). With reduced motion the static mark is shown instead.
 * [fadeOut] crossfades the splash away (400ms) before [onFinished]; without it [onFinished] runs
 * immediately so another window can take over the same frame.
 */
@Composable
fun StartupSplash(
    dark: Boolean,
    reducedMotion: Boolean,
    ready: Boolean,
    onFinished: (skipped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    resumeFrom: SplashResume? = null,
    fadeOut: Boolean = true,
    onShown: () -> Unit = {},
) {
    val palette = SplashPalette.of(dark)
    val startsStatic = reducedMotion || resumeFrom == SplashResume.FinalFrame
    val startsEmpty = resumeFrom == SplashResume.Loading || (resumeFrom == null && !animate)
    val initialMark = if (startsEmpty) {
        0f
    } else if (startsStatic) {
        1f
    } else {
        0f
    }
    val mark = remember { Animatable(initialMark) }
    val drop = remember { Animatable(initialMark) }
    val bloom = remember {
        Animatable(
            if (startsEmpty) {
                0f
            } else if (startsStatic) {
                0.55f
            } else {
                0f
            },
        )
    }
    val overlay = remember { Animatable(1f) }
    val skip = remember { kotlinx.coroutines.CompletableDeferred<Unit>() }
    val focusRequester = remember { FocusRequester() }
    val currentReady by rememberUpdatedState(ready)
    var waiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        // Let the frame reach the screen before anyone takes the previous window away.
        withFrameNanos {}
        withFrameNanos {}
        onShown()
        var skipped = startsEmpty
        if (resumeFrom == null && animate) {
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
                skip.onAwait { skipped = true }
            }
            play.cancel()
            if (skipped && !currentReady) {
                // Skipped before the app is ready: drop the mark for the minimal loading state.
                coroutineScope {
                    launch { mark.animateTo(0f, tween(if (reducedMotion) 0 else 150)) }
                    launch { drop.animateTo(0f, tween(if (reducedMotion) 0 else 150)) }
                    launch { bloom.animateTo(0f, tween(if (reducedMotion) 0 else 150)) }
                }
            }
        }
        if (!currentReady) {
            waiting = true
            snapshotFlow { currentReady }.first { it }
            waiting = false
        }
        if (fadeOut) overlay.animateTo(0f, tween(if (reducedMotion) 0 else 400))
        onFinished(skipped)
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
            .onKeyEvent { _ ->
                // Esc or any other key skips.
                skip.complete(Unit)
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
        if (waiting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .size(18.dp)
                    .testTag("startup-splash-progress"),
                color = palette.accent.copy(alpha = 0.7f),
                trackColor = Color.Transparent,
                strokeWidth = 2.dp,
            )
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
