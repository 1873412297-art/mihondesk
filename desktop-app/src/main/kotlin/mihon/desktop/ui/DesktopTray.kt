package mihon.desktop.ui

import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.platform.SharedTrayIcon
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

class DesktopTray(
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
                    val strings = stringsProvider()
                    val popup = PopupMenu().apply {
                        // Heavyweight AWT menus use the logical "Dialog" font, whose Windows mapping
                        // has no CJK glyphs under the bundled runtime: Chinese labels rendered as boxes.
                        trayMenuFont(strings.trayShow + strings.trayQuit)?.let { font = it }
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

                    // Reuse the process icon the notification services already show instead of adding a second one.
                    val sharedIcon = SharedTrayIcon.acquire() ?: return@invokeLater
                    SharedTrayIcon.configure(strings.appName, popup, onShow)
                    trayIcon = sharedIcon
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
                if (trayIcon == null) return@invokeLater
                // The icon is shared with the notification services, so only detach the menu and click action.
                SharedTrayIcon.configure("mihondesk", popup = null, onAction = null)
                trayIcon = null
            }
        }
    }

    companion object {
        private val CJK_FONT_CANDIDATES =
            listOf("Microsoft YaHei UI", "Microsoft YaHei", "Microsoft JhengHei UI", "SimSun")

        /** A system font able to display [text], or null when the default font already can. */
        internal fun trayMenuFont(text: String, size: Int = 12): java.awt.Font? = runCatching {
            if (text.all { it.code < 0x80 }) return@runCatching null
            val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
            CJK_FONT_CANDIDATES.asSequence()
                .filter { it in families }
                .map { java.awt.Font(it, java.awt.Font.PLAIN, size) }
                .firstOrNull { it.canDisplayUpTo(text) == -1 }
        }.getOrNull()

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
