package mihon.desktop.activity

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.ui.MihonDesktopTheme
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ActivityCenterButtonTest {
    @Test
    fun `failed task shows a badge, its reason, and retries in one click`() = runComposeUiTest {
        val center = ActivityCenter()
        var retried = 0
        center.begin("sync", ActivityKind.SYNC)
        center.fail("sync", reason = "Connection refused", retry = { retried++ })
        center.begin("lib", ActivityKind.LIBRARY_UPDATE, subject = "One Piece")
        center.progress("lib", current = 3, total = 10)

        setContent { MihonDesktopTheme { ActivityCenterButton(center) } }

        onNodeWithTag(ACTIVITY_CENTER_FAILED_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(ACTIVITY_CENTER_BUTTON_TAG).performClick()
        onNodeWithTag(ACTIVITY_CENTER_PANEL_TAG).assertIsDisplayed()
        onNodeWithText("Connection refused").assertIsDisplayed()
        onNodeWithText("One Piece", substring = true).assertIsDisplayed()
        onNodeWithTag("activity-retry-sync").performClick()
        runOnIdle { retried shouldBe 1 }
    }

    @Test
    fun `idle center opens an empty state`() = runComposeUiTest {
        val center = ActivityCenter()
        setContent { MihonDesktopTheme { ActivityCenterButton(center) } }
        onNodeWithTag(ACTIVITY_CENTER_FAILED_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ACTIVITY_CENTER_BUTTON_TAG).performClick()
        onNodeWithTag("activity-center-empty").assertIsDisplayed()
    }
}
