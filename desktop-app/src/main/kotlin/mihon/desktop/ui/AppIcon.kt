package mihon.desktop.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import mihon.desktop.logging.DesktopLogger
import org.jetbrains.skia.Image

/** Window / title-bar icon from the bundled `icon.png`, or null when it cannot be decoded. */
internal fun loadAppIcon(): BitmapPainter? = try {
    val stream = AppSessionState::class.java.getResourceAsStream("/icon.png")
        ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
    stream?.use {
        BitmapPainter(Image.makeFromEncoded(it.readAllBytes()).use { image -> image.toComposeImageBitmap() })
    }
} catch (error: Exception) {
    DesktopLogger.warn("MainWindow", "Cannot load the application icon", error)
    null
}
