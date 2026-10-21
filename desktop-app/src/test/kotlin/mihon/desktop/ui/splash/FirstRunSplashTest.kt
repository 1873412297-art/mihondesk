package mihon.desktop.ui.splash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    @Test
    fun `holds on the final frame until ready, then finishes`() = runComposeUiTest {
        var finished = 0
        var ready by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            StartupSplash(dark = false, reducedMotion = true, ready = ready, onFinished = { finished++ })
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(
                androidx.compose.ui.test.hasTestTag("startup-splash-progress"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        finished shouldBe 0
        ready = true
        waitUntil(timeoutMillis = 5_000) { finished == 1 }
    }

    @Test
    fun `skip before ready reports skipped and waits`() = runComposeUiTest {
        var result: Boolean? = null
        mainClock.autoAdvance = false
        var ready by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            StartupSplash(dark = true, reducedMotion = false, ready = ready, fadeOut = false, onFinished = {
                result = it
            })
        }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(FIRST_RUN_SPLASH_TEST_TAG).performClick()
        mainClock.advanceTimeBy(3_000)
        result shouldBe null
        mainClock.autoAdvance = true
        ready = true
        waitUntil(timeoutMillis = 5_000) { result != null }
        result shouldBe true
    }
}
