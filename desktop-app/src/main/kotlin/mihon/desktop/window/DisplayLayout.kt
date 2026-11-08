package mihon.desktop.window

import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Every connected monitor's usable area (taskbar excluded), primary first.
 *
 * Placement is checked against all of them. Checking only the primary screen moved a window that
 * was saved on a second monitor back to the primary one on every start, even with both connected.
 */
data class DisplayLayout(val screens: List<ScreenBounds>) {
    init {
        require(screens.isNotEmpty()) { "at least one screen is required" }
    }

    val primary: ScreenBounds get() = screens.first()

    /**
     * Keeps the window on the monitor it overlaps most. When it overlaps none (that monitor was
     * unplugged, or the saved values are garbage) it is centred on the primary monitor.
     */
    fun sanitize(placement: WindowPlacement): WindowPlacement {
        val host = screens.maxByOrNull { overlap(placement, it) }?.takeIf { overlap(placement, it) > 0 } ?: primary
        return placement.sanitize(host)
    }

    private fun overlap(placement: WindowPlacement, screen: ScreenBounds): Long {
        val width = minOf(placement.x + placement.width, screen.x + screen.width) - maxOf(placement.x, screen.x)
        val height = minOf(placement.y + placement.height, screen.y + screen.height) - maxOf(placement.y, screen.y)
        return if (width > 0 && height > 0) width.toLong() * height else 0
    }

    companion object {
        fun current(): DisplayLayout {
            if (GraphicsEnvironment.isHeadless()) return DisplayLayout(listOf(ScreenBounds(0, 0, 1280, 800)))
            val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val primary = environment.defaultScreenDevice
            val toolkit = Toolkit.getDefaultToolkit()
            val screens = environment.screenDevices
                .sortedByDescending { it == primary }
                .map { device ->
                    val configuration = device.defaultConfiguration
                    // Both values are in the same user-space units Compose positions windows in.
                    val bounds = configuration.bounds
                    val insets = toolkit.getScreenInsets(configuration)
                    ScreenBounds(
                        x = bounds.x + insets.left,
                        y = bounds.y + insets.top,
                        width = bounds.width - insets.left - insets.right,
                        height = bounds.height - insets.top - insets.bottom,
                    )
                }
                .filter { it.width > 0 && it.height > 0 }
            return DisplayLayout(screens.ifEmpty { listOf(ScreenBounds(0, 0, 1280, 800)) })
        }
    }
}
