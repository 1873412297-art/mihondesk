package mihon.desktop.ui.browse

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class GlobalSearchExperienceTest {
    @Test
    fun `typing before first search does not claim there are no sources and Enter searches once`() = runComposeUiTest {
        var searches = 0
        val running = mutableStateOf(false)
        setContent {
            GlobalSearchScreen("Manga", {}, { searches++ }, {}, running.value, emptyList(), { _, _ -> }, {})
        }
        onNodeWithText(mihon.desktop.i18n.EnglishStrings.globalSearchEnterQuery).assertExists()
        onNodeWithText(mihon.desktop.i18n.EnglishStrings.globalSearchNoSources).assertDoesNotExist()
        onNodeWithTag("global-search-input").performClick().performKeyInput { pressKey(Key.Enter) }
        searches shouldBe 1
        runOnIdle { running.value = true }
        onNodeWithTag("global-search-input").performKeyInput { pressKey(Key.Enter) }
        searches shouldBe 1
    }

    @Test
    fun `TLS source error excludes a fake HTTP zero status`() = runComposeUiTest {
        setContent {
            androidx.compose.material3.Text(
                sourceNetworkFailureText(
                    mihon.extension.ipc.NetworkFailure(mihon.extension.ipc.NetworkFailureKind.TLS, 0, "images.example"),
                ),
            )
        }
        onNodeWithText("Secure connection failed", substring = true).assertExists()
        onNodeWithText("HTTP 0", substring = true).assertDoesNotExist()
    }
}
