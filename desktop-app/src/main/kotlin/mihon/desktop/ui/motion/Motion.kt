package mihon.desktop.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import mihon.desktop.platform.WindowsReducedMotion

/**
 * Shared motion vocabulary. Durations and easings follow Material 3's emphasized curves, kept short
 * for a desktop app driven by mouse and keyboard. Every helper snaps when the user has turned off
 * Windows animations ("Show animations in Windows"), so motion never stands between the user and
 * the content.
 */
object MotionTokens {
    const val SHORT = 120
    const val MEDIUM = 220
    const val LONG = 320

    /** Things arriving on screen: fast start, gentle landing. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving the screen: gentle start, fast exit. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Small state changes (hover, press) that should neither lag nor overshoot. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** True when the OS asks for reduced motion. Tests and previews may provide their own value. */
val LocalReducedMotion = staticCompositionLocalOf { WindowsReducedMotion.prefersReducedMotion }

/** [tween] with the given timing, or an immediate [snap] under reduced motion. */
@Composable
@ReadOnlyComposable
fun <T> motionTween(
    durationMillis: Int,
    easing: Easing = MotionTokens.Standard,
    delayMillis: Int = 0,
): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else tween(durationMillis, delayMillis, easing)

/**
 * Material "fade through" for switching between top-level pages: the old page fades out quickly,
 * then the new one fades in while rising slightly into place.
 */
fun fadeThrough(reduced: Boolean): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    val outgoing = MotionTokens.SHORT
    val enter = fadeIn(tween(MotionTokens.MEDIUM, delayMillis = outgoing, easing = MotionTokens.EmphasizedDecelerate)) +
        slideInVertically(
            tween(MotionTokens.LONG, delayMillis = outgoing, easing = MotionTokens.EmphasizedDecelerate),
        ) {
            it / 40
        } +
        scaleIn(
            tween(MotionTokens.LONG, delayMillis = outgoing, easing = MotionTokens.EmphasizedDecelerate),
            initialScale = 0.985f,
        )
    val exit = fadeOut(tween(outgoing, easing = MotionTokens.EmphasizedAccelerate))
    return enter togetherWith exit
}

/** A plain cross-fade, e.g. between the shell and the reader. */
fun crossFade(reduced: Boolean, durationMillis: Int = MotionTokens.MEDIUM): ContentTransform =
    if (reduced) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(tween(durationMillis, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
            fadeOut(tween(durationMillis, easing = MotionTokens.EmphasizedAccelerate))
    }
