package mihon.desktop.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class OnboardingStartStepTest {
    @Test
    fun `where to start step is the skippable last step and reports the chosen action`() = runComposeUiTest {
        var chosen: StartAction? = null
        var finished = 0
        setContent {
            ProvideDesktopStrings(AppLanguage.SimplifiedChinese) {
                MaterialTheme {
                    OnboardingDialog(
                        preferences = DesktopPreferences(),
                        repositories = emptyList(),
                        onAddRepository = {},
                        onUpdatePreferences = {},
                        onFinish = { finished++ },
                        onGoToBrowse = {},
                        onStartAction = { chosen = it },
                    )
                }
            }
        }

        repeat(3) { onNodeWithTag("onboarding-next-btn").performClick() }
        onNodeWithTag("onboarding-step-4").assertExists()
        onNodeWithText("4/4: 从哪里开始").assertExists()
        onNodeWithTag("onboarding-skip-btn").assertTextContains("跳过这一步")
        onNodeWithTag("onboarding-start-pair-phone").performClick()
        chosen shouldBe StartAction.PairPhone

        onNodeWithTag("onboarding-skip-btn").performClick()
        finished shouldBe 1
    }

    @Test
    fun `onboarding keeps three steps when the host offers no start actions`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                OnboardingDialog(
                    preferences = DesktopPreferences(),
                    repositories = emptyList(),
                    onAddRepository = {},
                    onUpdatePreferences = {},
                    onFinish = {},
                    onGoToBrowse = {},
                )
            }
        }

        repeat(2) { onNodeWithTag("onboarding-next-btn").performClick() }
        onNodeWithTag("onboarding-step-3").assertExists()
        onNodeWithTag("onboarding-finish-btn").assertExists()
        onNodeWithTag("onboarding-next-btn").assertDoesNotExist()
    }
}
