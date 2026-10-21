package mihon.desktop.ui.splash

import androidx.compose.runtime.mutableStateOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartupHandoffTest {
    @Test
    fun `handoff created while the boot splash plays is held until released`() {
        val boot = mutableStateOf<Boolean?>(null)
        val handoff = StartupHandoff(boot) {}
        assertFalse(handoff.released)
        boot.value = true
        assertTrue(handoff.released)
        assertTrue(handoff.skipped)
    }

    @Test
    fun `legacy handoff is released immediately`() {
        val handoff = StartupHandoff(skipped = false) {}
        assertTrue(handoff.released)
        assertFalse(handoff.skipped)
    }
}
