package mihon.desktop.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopTrayTest {

    @Test
    fun `shouldStayInBackground returns true when enabled, tray supported, and window available without forceQuit`() {
        assertTrue(
            DesktopTray.shouldStayInBackground(
                enabled = true,
                traySupported = true,
                windowAvailable = true,
                forceQuit = false,
            ),
        )
        assertTrue(
            DesktopTray.shouldStayInBackground(
                enabled = true,
                traySupported = true,
                windowAvailable = true,
            ),
        )
    }

    @Test
    fun `shouldStayInBackground returns false when forceQuit is true`() {
        assertFalse(
            DesktopTray.shouldStayInBackground(
                enabled = true,
                traySupported = true,
                windowAvailable = true,
                forceQuit = true,
            ),
        )
    }

    @Test
    fun `shouldStayInBackground returns false when disabled`() {
        assertFalse(
            DesktopTray.shouldStayInBackground(
                enabled = false,
                traySupported = true,
                windowAvailable = true,
            ),
        )
    }

    @Test
    fun `shouldStayInBackground returns false when tray is not supported`() {
        assertFalse(
            DesktopTray.shouldStayInBackground(
                enabled = true,
                traySupported = false,
                windowAvailable = true,
            ),
        )
    }

    @Test
    fun `shouldStayInBackground returns false when window is null or not available`() {
        assertFalse(
            DesktopTray.shouldStayInBackground(
                enabled = true,
                traySupported = true,
                windowAvailable = false,
            ),
        )
    }
}
