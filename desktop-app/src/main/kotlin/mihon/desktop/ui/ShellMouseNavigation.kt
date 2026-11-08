package mihon.desktop.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The mouse back side button (button 4) goes back anywhere in the window, like Backspace.
 *
 * Runs in the Final pass, after every child has seen the Initial and Main passes. Screens with their own meaning for the button (the
 * reader turns pages, library details close) consume the press, and then this one stays out.
 */
internal fun Modifier.navigateBackOnMouseBack(enabled: () -> Boolean, onBack: () -> Unit): Modifier =
    pointerInput(onBack) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (isUnhandledBackPress(event) && enabled()) {
                    event.changes.forEach { it.consume() }
                    onBack()
                }
            }
        }
    }

internal fun isUnhandledBackPress(event: PointerEvent): Boolean =
    event.type == PointerEventType.Press && event.buttons.isBackPressed && event.changes.none { it.isConsumed }

/** The mouse's back side button closes details, like Esc, and stops there. */
internal fun Modifier.closeOnMouseBack(onClose: (() -> Unit)?): Modifier = if (onClose == null) {
    this
} else {
    pointerInput(onClose) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isBackPressed) {
                    event.changes.forEach { it.consume() }
                    onClose()
                }
            }
        }
    }
}
