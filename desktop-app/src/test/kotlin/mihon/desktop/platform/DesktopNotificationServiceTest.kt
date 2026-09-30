package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment
import java.awt.SystemTray

class DesktopNotificationServiceTest {

    @Test
    fun `disabled tray icon leaves the system tray untouched and stays safe to notify`() {
        assumeTrue(!GraphicsEnvironment.isHeadless() && SystemTray.isSupported())
        val tray = SystemTray.getSystemTray()
        val before = tray.trayIcons.toList()

        val service = DesktopNotificationService(trayIconEnabled = false)
        service.notify("Library Updated", "Found 5 new chapters", NotificationType.WARNING)

        val added = tray.trayIcons.filterNot { it in before }
        added.forEach(tray::remove)
        added shouldBe emptyList()
        service.notifications.value?.message shouldBe "Found 5 new chapters"
    }
}
