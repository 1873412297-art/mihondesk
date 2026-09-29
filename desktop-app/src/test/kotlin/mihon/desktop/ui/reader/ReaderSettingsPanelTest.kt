package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderPageTransition
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderSettingsPanelTest {

    @Test
    fun `reader settings panel allows selecting page transitions and saves`() = runComposeUiTest {
        var savedSettings: DesktopReaderSettings? = null
        val initial = DesktopReaderSettings(pageTransition = ReaderPageTransition.NONE)

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderSettingsPanel(
                        settings = initial,
                        onSave = { savedSettings = it },
                        onDismiss = {},
                    )
                }
            }
        }

        // Initially in Reading tab
        onNodeWithTag("reader-setting-transition").assertIsDisplayed()
        onNodeWithTag("reader-setting-transition").performClick()

        // Click SLIDE option
        onNodeWithTag("reader-setting-transition-SLIDE").assertIsDisplayed()
        onNodeWithTag("reader-setting-transition-SLIDE").performClick()

        // Save
        onNodeWithTag("reader-settings-save").performClick()
        savedSettings?.pageTransition shouldBe ReaderPageTransition.SLIDE
    }

    @Test
    fun `reader settings panel can select fade transition`() = runComposeUiTest {
        var savedSettings: DesktopReaderSettings? = null
        val initial = DesktopReaderSettings(pageTransition = ReaderPageTransition.NONE)

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderSettingsPanel(
                        settings = initial,
                        onSave = { savedSettings = it },
                        onDismiss = {},
                    )
                }
            }
        }

        onNodeWithTag("reader-setting-transition").performClick()
        onNodeWithTag("reader-setting-transition-FADE").performClick()
        onNodeWithTag("reader-settings-save").performClick()
        savedSettings?.pageTransition shouldBe ReaderPageTransition.FADE
    }
}
