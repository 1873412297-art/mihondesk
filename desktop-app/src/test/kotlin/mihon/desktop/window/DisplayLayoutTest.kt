package mihon.desktop.window

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DisplayLayoutTest {
    // A 1920x1040 primary (taskbar excluded) with a 2560x1400 monitor to its right.
    private val primary = ScreenBounds(0, 0, 1920, 1040)
    private val secondary = ScreenBounds(1920, 0, 2560, 1400)
    private val left = ScreenBounds(-1600, 100, 1600, 860)

    @Test
    fun `a window on a connected second monitor stays there`() {
        val saved = WindowPlacement(2200, 120, 1600, 1000, maximized = false)
        DisplayLayout(listOf(primary, secondary)).sanitize(saved) shouldBe saved
    }

    @Test
    fun `a window on a monitor left of the primary keeps its negative coordinates`() {
        val saved = WindowPlacement(-1400, 200, 1200, 700, maximized = false)
        DisplayLayout(listOf(primary, left)).sanitize(saved) shouldBe saved
    }

    @Test
    fun `a window on an unplugged monitor is centred on the primary one`() {
        val saved = WindowPlacement(2200, 120, 1600, 1000, maximized = false)
        DisplayLayout(listOf(primary)).sanitize(saved) shouldBe WindowPlacement(160, 20, 1600, 1000, maximized = false)
    }

    @Test
    fun `a window straddling two monitors is kept on the one it covers most`() {
        val saved = WindowPlacement(1700, 100, 1200, 800, maximized = false)
        // 980 of its 1200 pixels are on the secondary, so it is pulled fully onto that one.
        DisplayLayout(listOf(primary, secondary)).sanitize(saved) shouldBe
            WindowPlacement(1920, 100, 1200, 800, maximized = false)
    }

    @Test
    fun `a window larger than the remaining monitor is shrunk to fit it`() {
        val saved = WindowPlacement(2000, 0, 3000, 1600, maximized = true)
        DisplayLayout(listOf(primary)).sanitize(saved) shouldBe WindowPlacement(0, 0, 1920, 1040, maximized = true)
    }

    @Test
    fun `the taskbar area is not counted as usable`() {
        // Bottom edge exactly at the taskbar on a 1080-pixel screen with a 40-pixel taskbar.
        val saved = WindowPlacement(100, 300, 1280, 780, maximized = false)
        DisplayLayout(listOf(primary)).sanitize(saved).let { (it.y + it.height) <= 1040 } shouldBe true
    }
}
