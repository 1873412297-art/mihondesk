package mihon.desktop.notification

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DesktopNotificationServiceTest {

    @Test
    fun `page progress stays in app and only the chapter result reaches Windows`() {
        val systemMessages = mutableListOf<Triple<String, String, Boolean>>()
        val service = WindowsDesktopNotificationService(
            systemMessageSink = { title, message, isError -> systemMessages.add(Triple(title, message, isError)) },
        )
        System.getenv("MIHON_NOTIFICATION_INSTALLED_APP")?.let { installedApp ->
            val origin = Path.of(service.javaClass.protectionDomain.codeSource.location.toURI())
            origin.startsWith(Path.of(installedApp)) shouldBe true
            println("Notification service loaded from $origin")
        }

        repeat(145) { page ->
            service.notifyDownloadProgress("Test Manga", "Gallery", (page + 1) / 145f)
        }

        systemMessages shouldHaveSize 0
        service.recentNotifications.value.first().progress shouldBe 1f

        service.notifyDownloadComplete("Test Manga", "Gallery")
        systemMessages shouldBe listOf(Triple("Download Complete", "Test Manga - Gallery", false))

        service.notifyDownloadError("Test Manga", "Chapter 2", "Connection interrupted")
        systemMessages shouldHaveSize 2
        systemMessages.last() shouldBe Triple(
            "Download Failed",
            "Test Manga - Chapter 2: Connection interrupted",
            true,
        )
    }

    @Test
    fun `Windows delivery respects disabled notifications and hidden chapter content`() {
        var enabled = false
        val systemMessages = mutableListOf<String>()
        val service = WindowsDesktopNotificationService(
            enabledProvider = { enabled },
            hideContentProvider = { true },
            systemMessageSink = { _, message, _ -> systemMessages.add(message) },
        )

        service.notifyDownloadComplete("Private title", "Chapter 1")
        service.notifyDownloadError("Private title", "Chapter 1", "Private error")
        service.notifyDownloadProgress("Private title", "Chapter 1", 0.5f)
        systemMessages shouldHaveSize 0

        enabled = true
        service.notifyDownloadComplete("Private title", "Chapter 1")
        service.notifyDownloadError("Private title", "Chapter 1", "Private error")
        systemMessages shouldBe listOf("A chapter download completed", "A chapter download failed")
    }

    @Test
    fun `dispatches and stores in-app notifications without crashing in headless environment`() {
        val service = WindowsDesktopNotificationService()

        service.notifyDownloadComplete("One Piece", "Chapter 1000")
        service.notifyDownloadError("Bleach", "Chapter 1", "HTTP 404")
        service.notifyLibraryUpdate(5, 2)

        val notifications = service.recentNotifications.value
        notifications shouldHaveSize 3

        notifications[0].title shouldBe "Library Updated"
        notifications[0].message shouldBe "Found 5 new chapters across 2 manga"
        notifications[0].isError shouldBe false

        notifications[1].title shouldBe "Download Failed"
        notifications[1].isError shouldBe true

        notifications[2].title shouldBe "Download Complete"
        notifications[2].isError shouldBe false

        service.clearNotifications()
        service.recentNotifications.value shouldHaveSize 0
    }

    @Test
    fun `respects disabled and hide-content preferences for every notification kind`() {
        var enabled = false
        var hideContent = true
        val service = WindowsDesktopNotificationService(
            enabledProvider = { enabled },
            hideContentProvider = { hideContent },
        )

        service.notifyDownloadComplete("Secret Manga", "Chapter 9")
        service.recentNotifications.value shouldHaveSize 0

        enabled = true
        service.notifyDownloadProgress("Secret Manga", "Chapter 9", 0.5f)
        service.notifyDownloadError("Secret Manga", "Chapter 9", "private path")
        service.notifyLibraryUpdate(3, 1)
        service.notifyExtensionUpdatePending(2)

        val notifications = service.recentNotifications.value
        notifications shouldHaveSize 4
        notifications.all { "Secret Manga" !in it.message } shouldBe true
        notifications.all { "private path" !in it.message } shouldBe true
        notifications.first().title shouldBe "Extension Updates Available"

        hideContent = false
        service.notifyDownloadComplete("Visible Manga", "Chapter 1")
        service.recentNotifications.value.first().message shouldBe "Visible Manga - Chapter 1"
    }
}
