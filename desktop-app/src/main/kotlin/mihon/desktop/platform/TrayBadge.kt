package mihon.desktop.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** Paints an unread-count badge onto the tray icon. */
object TrayBadge {
    /** Warm ochre-red that reads on both light and dark taskbars. */
    const val BADGE_RGB: Int = 0xC2410C

    fun label(count: Int): String = if (count > 99) "99+" else count.toString()

    fun render(base: BufferedImage, count: Int): BufferedImage {
        if (count <= 0) return base
        val size = base.width
        val out = BufferedImage(size, base.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.drawImage(base, 0, 0, null)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val diameter = (size * 0.62f).toInt().coerceAtLeast(8)
            val x = size - diameter
            g.color = Color(BADGE_RGB)
            g.fillOval(x, 0, diameter, diameter)
            g.color = Color(0xFFF7ED)
            g.stroke = BasicStroke((size / 32f).coerceAtLeast(1f))
            g.drawOval(x, 0, diameter - 1, diameter - 1)
            val text = if (count > 9) "9+" else count.toString()
            g.font = Font(Font.SANS_SERIF, Font.BOLD, (diameter * 0.72f).toInt().coerceAtLeast(6))
            val metrics = g.fontMetrics
            val tx = x + (diameter - metrics.stringWidth(text)) / 2
            val ty = (diameter - metrics.height) / 2 + metrics.ascent
            g.drawString(text, tx, ty)
        } finally {
            g.dispose()
        }
        return out
    }
}
