package mihon.desktop.platform

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopTitleBarPolicyTest {
    @Test
    fun `custom title bar only on JBR without opt-out or high contrast`() {
        assertTrue(DesktopTitleBarPolicy.useCustomTitleBar(true, false, false))
        assertFalse(DesktopTitleBarPolicy.useCustomTitleBar(false, false, false))
        assertFalse(DesktopTitleBarPolicy.useCustomTitleBar(true, true, false))
        assertFalse(DesktopTitleBarPolicy.useCustomTitleBar(true, false, true))
    }

    @Test
    fun `caption strip hides in borderless or fullscreen reading`() {
        assertTrue(DesktopTitleBarPolicy.showCaptionStrip(customTitleBar = true, borderlessOrFullscreen = false))
        assertFalse(DesktopTitleBarPolicy.showCaptionStrip(customTitleBar = true, borderlessOrFullscreen = true))
        assertFalse(DesktopTitleBarPolicy.showCaptionStrip(customTitleBar = false, borderlessOrFullscreen = false))
    }

    @Test
    fun `colour converts to Windows COLORREF`() {
        assertEquals(0x00332211, Color(0xFF112233).toColorRef())
        assertEquals(DESKTOP_TITLE_BAR_HEIGHT_DP, 32)
    }
}
