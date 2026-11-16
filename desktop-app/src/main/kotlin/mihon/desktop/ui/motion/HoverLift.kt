package mihon.desktop.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Lifts a card slightly while the pointer is over it and settles it back while pressed. Pass the
 * same [interactionSource] to the card's `clickable` so press and hover come from one place. The
 * scale is applied in the layer, so hovering redraws the card without recomposing it.
 */
@Composable
fun Modifier.hoverLift(
    interactionSource: MutableInteractionSource,
    hoverScale: Float = 1.03f,
    pressScale: Float = 0.98f,
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val target = when {
        pressed -> pressScale
        hovered -> hoverScale
        else -> 1f
    }
    val scale by animateFloatAsState(target, motionTween(MotionTokens.SHORT), label = "hoverLift")
    return this
        .hoverable(interactionSource)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** `clickable` for cards: the usual ripple plus [hoverLift], driven by one interaction source. */
@Composable
fun Modifier.liftingClickable(role: Role? = Role.Button, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .hoverLift(interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = role,
            onClick = onClick,
        )
}
