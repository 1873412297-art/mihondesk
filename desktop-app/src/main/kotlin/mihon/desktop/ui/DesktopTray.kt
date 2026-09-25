package mihon.desktop.ui

import mihon.desktop.i18n.DesktopStrings
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class DesktopTray(
    private val appIcon: Image? = loadAppImage(),
    private val stringsProvider: () -> DesktopStrings,
    private val onShow: () -> Unit,
    private val onQuit: () -> Unit,
) {
    val isSupported: Boolean
        get() = try {
            !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()
        } catch (_: Throwable) {
            false
        }

    @Volatile
    private var trayIcon: TrayIcon? = null

    @Volatile
    private var hasShownNotification: Boolean = false

    @Synchronized
    fun ensureInstalled() {
        if (!isSupported) return
        if (trayIcon != null) return

        EventQueue.invokeLater {
            synchronized(this) {
                if (trayIcon != null) return@invokeLater
                try {
                    val icon = appIcon ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                    val strings = stringsProvider()
                    val popup = PopupMenu().apply {
                        val showItem = MenuItem(strings.trayShow).apply {
                            addActionListener { onShow() }
                        }
                        val quitItem = MenuItem(strings.trayQuit).apply {
                            addActionListener { onQuit() }
                        }
                        add(showItem)
                        addSeparator()
                        add(quitItem)
                    }

                    val newTrayIcon = TrayIcon(icon, strings.appName, popup).apply {
                        isImageAutoSize = true
                        addActionListener { onShow() }
                    }
                    SystemTray.getSystemTray().add(newTrayIcon)
                    trayIcon = newTrayIcon
                } catch (_: Throwable) {
                    // Ignored if system tray installation fails
                }
            }
        }
    }

    fun showNotification(title: String, message: String) {
        EventQueue.invokeLater {
            try {
                trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
            } catch (_: Throwable) {
            }
        }
    }

    fun showNotificationOnce(title: String, message: String) {
        if (hasShownNotification) return
        hasShownNotification = true
        showNotification(title, message)
    }

    @Synchronized
    fun dispose() {
        EventQueue.invokeLater {
            synchronized(this) {
                val icon = trayIcon ?: return@invokeLater
                try {
                    SystemTray.getSystemTray().remove(icon)
                } catch (_: Throwable) {
                } finally {
                    trayIcon = null
                }
            }
        }
    }

    companion object {
        fun loadAppImage(): Image? {
            return try {
                val stream = DesktopTray::class.java.getResourceAsStream("/icon.png")
                    ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
                stream?.use { ImageIO.read(it) }
            } catch (_: Throwable) {
                null
            }
        }

        fun shouldStayInBackground(
            enabled: Boolean,
            traySupported: Boolean,
            windowAvailable: Boolean,
            forceQuit: Boolean = false,
        ): Boolean {
            return !forceQuit && enabled && traySupported && windowAvailable
        }
    }
}
