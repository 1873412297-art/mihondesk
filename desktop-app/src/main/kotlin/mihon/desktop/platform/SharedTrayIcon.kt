package mihon.desktop.platform

import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The one notification-area icon of this process.
 *
 * Both notification services and the run-in-background tray used to add their own
 * [TrayIcon], so a single running window showed up to three identical mihondesk icons (and
 * Windows kept a separate NotifyIconSettings registration for each UID). Everything now shares
 * this icon: the notification services only need it for balloons, and [DesktopTray][mihon.desktop.ui.DesktopTray]
 * attaches its menu and click action to it while the window is hidden.
 */
object SharedTrayIcon {
    private val instance = CreateOnce(::install)

    @Volatile
    private var actionListener: ActionListener? = null

    @Volatile
    private var baseImage: BufferedImage? = null

    @Volatile
    private var baseTooltip: String = "mihondesk"

    /**
     * Invoked when the icon or one of its balloons is clicked. AWT reports a balloon click on
     * Windows as an action event on the icon, which is how a notification can jump to a chapter.
     */
    @Volatile
    var onNotificationClick: (() -> Unit)? = null

    /** Returns the process tray icon, installing it on first use; null when no tray is available. */
    fun acquire(): TrayIcon? = instance.get()

    /** Shows [count] as a badge on the icon (0 clears it) and puts [tooltip] on hover. */
    @Synchronized
    fun setBadge(count: Int, tooltip: String?) {
        val icon = instance.peek() ?: return
        runCatching {
            val base = baseImage ?: return@runCatching
            icon.image = TrayBadge.render(base, count)
            icon.toolTip = if (count > 0 && tooltip != null) tooltip else baseTooltip
        }
    }

    /** Attaches (or with nulls, detaches) the background-mode menu and click action. */
    @Synchronized
    fun configure(tooltip: String, popup: PopupMenu?, onAction: (() -> Unit)?) {
        val icon = instance.peek() ?: return
        runCatching {
            icon.toolTip = tooltip
            baseTooltip = tooltip
            icon.popupMenu = popup
            actionListener?.let(icon::removeActionListener)
            actionListener = onAction?.let { action -> ActionListener { action() } }
            actionListener?.let(icon::addActionListener)
        }
    }

    private fun install(): TrayIcon? = try {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            null
        } else {
            val image = loadImage()
            baseImage = image
            TrayIcon(image, "mihondesk").apply {
                isImageAutoSize = true
                addActionListener { onNotificationClick?.invoke() }
                SystemTray.getSystemTray().add(this)
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun loadImage(): BufferedImage = runCatching {
        SharedTrayIcon::class.java.getResourceAsStream("/icon.png")?.use(ImageIO::read)?.let(::toArgb)
    }.getOrNull() ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)

    private fun toArgb(image: Image): BufferedImage {
        if (image is BufferedImage && image.type == BufferedImage.TYPE_INT_ARGB) return image
        val out = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(image, 0, 0, null)
            dispose()
        }
        return out
    }
}

/** Memoises the first non-null result of [create]; a failed (null) attempt is retried next time. */
internal class CreateOnce<T : Any>(private val create: () -> T?) {
    private var value: T? = null

    @Synchronized
    fun get(): T? = value ?: create()?.also { value = it }

    @Synchronized
    fun peek(): T? = value
}
