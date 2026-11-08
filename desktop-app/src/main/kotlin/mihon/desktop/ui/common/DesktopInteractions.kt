package mihon.desktop.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

fun interface TextInputTracker {
    fun setFocused(focused: Boolean)
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalSearchFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }
val LocalTextInputTracker = staticCompositionLocalOf<TextInputTracker> { TextInputTracker {} }

fun Modifier.searchFocusRequester(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) this.focusRequester(focusRequester) else this

@Composable
fun Modifier.trackTextInputFocus(): Modifier {
    val tracker = LocalTextInputTracker.current
    val onFocusChange = androidx.compose.runtime.remember(tracker) {
        { state: androidx.compose.ui.focus.FocusState ->
            tracker.setFocused(state.hasFocus)
        }
    }
    return this.onFocusChanged(onFocusChange)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.onSecondaryClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    if (!enabled) return this
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val onEvent: androidx.compose.ui.input.pointer.AwaitPointerEventScope.(
        androidx.compose.ui.input.pointer.PointerEvent,
    ) -> Unit =
        androidx.compose.runtime.remember {
            { event ->
                if (event.button == PointerButton.Secondary) {
                    currentOnClick()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    return this.onPointerEvent(PointerEventType.Press, onEvent = onEvent)
}

/**
 * Remembers whether a text field below it has focus, still forwarding to the window's tracker.
 * The reader reads it to keep its page-turn keys out of the chapter search box.
 */
@Composable
fun rememberScopedTextInputTracker(): Pair<TextInputTracker, () -> Boolean> {
    val parent = LocalTextInputTracker.current
    return remember(parent) {
        val focused = java.util.concurrent.atomic.AtomicBoolean(false)
        TextInputTracker {
            focused.set(it)
            parent.setFocused(it)
        } to focused::get
    }
}
