package mihon.desktop.activity

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.ui.MihonDesktopTheme
import org.junit.jupiter.api.Test

/** The title bar is composed outside the app's string provider, so the entry must localize itself. */
@OptIn(ExperimentalTestApi::class)
class ActivityCenterLocalizationTest {
    @Test
    fun `title bar entry uses the app language, not the English default`() = runComposeUiTest {
        val center = ActivityCenter()
        center.begin("sync", ActivityKind.SYNC)
        center.succeed("sync")
        setContent { MihonDesktopTheme { TitleBarActivityCenter(center, AppLanguage.SimplifiedChinese) } }
        onNodeWithTag(ACTIVITY_CENTER_BUTTON_TAG).performClick()
        onNodeWithText("后台任务").assertIsDisplayed()
        onNodeWithText("清除已完成").assertIsDisplayed()
        onNodeWithText("Clear finished").assertDoesNotExist()
    }
}
