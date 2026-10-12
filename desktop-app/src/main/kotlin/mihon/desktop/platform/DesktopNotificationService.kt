package mihon.desktop.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.TrayIcon
import java.awt.TrayIcon.MessageType

enum class NotificationType {
    INFO,
    WARNING,
    ERROR,
}

data class DesktopNotificationEvent(
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val timestamp: Long = System.currentTimeMillis(),
)

class DesktopNotificationService(
    private val enabledProvider: () -> Boolean = { true },
    private val hideContentProvider: () -> Boolean = { false },
    private val trayIconEnabled: Boolean = true,
) {
    private val _notifications = MutableStateFlow<DesktopNotificationEvent?>(null)
    val notifications: StateFlow<DesktopNotificationEvent?> = _notifications.asStateFlow()

    private var trayIcon: TrayIcon? = null

    init {
        if (trayIconEnabled) initTray()
    }

    private fun initTray() {
        // Shared with the other notification service and the background tray: one icon per process.
        trayIcon = SharedTrayIcon.acquire()
    }

    fun notify(
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
    ) {
        if (!enabledProvider()) return

        val visibleMessage = if (hideContentProvider()) "Open mihondesk to view details" else message
        val event = DesktopNotificationEvent(title = title, message = visibleMessage, type = type)
        _notifications.value = event

        val icon = trayIcon
        if (icon != null) {
            try {
                val messageType = when (type) {
                    NotificationType.INFO -> MessageType.INFO
                    NotificationType.WARNING -> MessageType.WARNING
                    NotificationType.ERROR -> MessageType.ERROR
                }
                icon.displayMessage(title, visibleMessage, messageType)
            } catch (_: Throwable) {
                // Ignore tray display error
            }
        }
    }

    fun clearLastNotification() {
        _notifications.value = null
    }
}
