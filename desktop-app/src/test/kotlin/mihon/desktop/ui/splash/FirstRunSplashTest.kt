package mihon.desktop.ui.splash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import java.nio.file.Files

@OptIn(ExperimentalTestApi::class)
class FirstRunSplashTest {
    @Test
    fun `splash plays on every launch by default`() {
        val store = DesktopPreferenceStore(Files.createTempDirectory("splash").resolve("preferences.properties"))
        store.load().splashEveryLaunch shouldBe true
        FirstRunSplash.markShown(store)
        FirstRunSplash.shouldShow(store) shouldBe true
    }

    @Test
    fun `splash is shown once per profile when every-launch is off`() {
        val store = DesktopPreferenceStore(Files.createTempDirectory("splash").resolve("preferences.properties"))
        store.updatePreferences { it.copy(splashEveryLaunch = false) }
        FirstRunSplash.shouldShow(store) shouldBe true
        FirstRunSplash.markShown(store)
        FirstRunSplash.shouldShow(store) shouldBe false
        DesktopPreferenceStore(store.let { Files.createTempDirectory("splash2").resolve("p.properties") })
            .let { FirstRunSplash.shouldShow(it) } shouldBe true
    }

    @Test
    fun `reset makes the splash play again`() {
        val store = DesktopPreferenceStore(Files.createTempDirectory("splash").resolve("preferences.properties"))
        FirstRunSplash.markShown(store)
        FirstRunSplash.reset(store)
        FirstRunSplash.shouldShow(store) shouldBe true
    }

    @Test
    fun `click skips the splash`() = runComposeUiTest {
        var finished = 0
        setContent {
            FirstRunSplashOverlay(dark = false, reducedMotion = false, onFinished = { finished++ })
        }
        onNodeWithTag(FIRST_RUN_SPLASH_TEST_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) { finished == 1 }
    }

    @Test
    fun `reduced motion finishes without the full animation`() = runComposeUiTest {
        var finished = 0
        setContent {
            FirstRunSplashOverlay(dark = true, reducedMotion = true, onFinished = { finished++ })
        }
        waitUntil(timeoutMillis = 5_000) { finished == 1 }
    }
}
