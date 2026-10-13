package mihon.desktop.ui.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression: Backspace in an untracked search box (Browse > Extensions) navigated back to the library
 * because the window preview pass matched the global Backspace=back chord before the text field saw it.
 */
@OptIn(ExperimentalTestApi::class)
class ShellKeyRoutingTest {
    private fun shellRoot(onBack: () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) =
        @androidx.compose.runtime.Composable {
            fun handle(action: ShellShortcutAction?): Boolean {
                if (action == ShellShortcutAction.NavigateBack) onBack()
                return action != null
            }
            Box(
                Modifier
                    .requiredSize(600.dp, 400.dp)
                    // Same two passes as the Window: preview for chords, post-dispatch for plain keys.
                    .onPreviewKeyEvent { handle(DesktopShortcutMatcher.matchPreview(it, isTextInputFocused = false)) }
                    .onKeyEvent { handle(DesktopShortcutMatcher.matchAfterDispatch(it, isTextInputFocused = false)) },
            ) { content() }
        }

    @Test
    fun `backspace in an untracked text field deletes text and never navigates back`() = runComposeUiTest {
        var backs = 0
        setContent {
            shellRoot(onBack = { backs++ }) {
                var text by mutableStateOf("ab")
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag("field"))
            }()
        }
        onNodeWithTag("field").performClick()
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
            pressKey(Key.Backspace)
            pressKey(Key.Backspace)
            pressKey(Key.Backspace) // empty field still consumes Backspace
        }
        // The field consumed every Backspace (the post-dispatch pass would otherwise have navigated back;
        // this harness reports no text focus). Text editing itself is verified live: synthetic test key
        // events do not drive the platform text-input pipeline.
        assertEquals(0, backs)
        onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun `backspace outside text fields still navigates back and ctrl chords stay global`() = runComposeUiTest {
        var backs = 0
        val focus = FocusRequester()
        setContent {
            shellRoot(onBack = { backs++ }) {
                Column {
                    Text("page", Modifier.testTag("page").focusRequester(focus).focusable())
                }
            }()
        }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("page").performKeyInput { pressKey(Key.Backspace) }
        assertEquals(1, backs)
        onNodeWithTag("page").performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }
        assertEquals(2, backs)
    }

    @Test
    fun `plain keys are never matched while a text field is focused`() {
        assertEquals(true, DesktopShortcutMatcher.isPlainKey(Key.Backspace, ctrl = false, alt = false))
        assertEquals(false, DesktopShortcutMatcher.isPlainKey(Key.F5, ctrl = false, alt = false))
        assertEquals(
            null,
            DesktopShortcutMatcher.matchAction(Key.Escape, isTextInputFocused = true),
        )
    }
}
