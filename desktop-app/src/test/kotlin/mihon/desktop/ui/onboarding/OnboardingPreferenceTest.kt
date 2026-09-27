package mihon.desktop.ui.onboarding

import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OnboardingPreferenceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `first run defaults to onboardingCompleted false`() {
        val file = tempDir.resolve("pref-onboarding.properties")
        val store = DesktopPreferenceStore(file)
        store.load().onboardingCompleted shouldBe false
    }

    @Test
    fun `completing onboarding persists flag across store reload`() {
        val file = tempDir.resolve("pref-onboarding-completed.properties")
        val store = DesktopPreferenceStore(file)
        val initial = store.load()
        initial.onboardingCompleted shouldBe false

        store.save(initial.copy(onboardingCompleted = true))

        val reloaded = DesktopPreferenceStore(file).load()
        reloaded.onboardingCompleted shouldBe true
    }

    @Test
    fun `reshowing onboarding resets flag or can be updated`() {
        val file = tempDir.resolve("pref-onboarding-reshow.properties")
        val store = DesktopPreferenceStore(file)
        store.save(DesktopPreferences(onboardingCompleted = true))
        store.load().onboardingCompleted shouldBe true

        store.save(store.load().copy(onboardingCompleted = false))
        store.load().onboardingCompleted shouldBe false
    }
}
