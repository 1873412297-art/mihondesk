package mihon.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import mihon.desktop.logging.DesktopLogger
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.roundToInt

/**
 * Downscales [bitmap] to [maxSize] on its longest side. The intermediate Skia image and surface are
 * closed deterministically; the returned bitmap keeps its own snapshot alive.
 */
internal fun downscaleImage(bitmap: ImageBitmap, maxSize: Int): ImageBitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxSize) return bitmap
    val scale = maxSize.toFloat() / longest
    val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val source = Image.makeFromBitmap(bitmap.asSkiaBitmap())
    val surface = Surface.makeRasterN32Premul(w, h)
    return try {
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true,
        )
        surface.makeImageSnapshot().use { image -> image.toComposeImageBitmap() }
    } finally {
        source.close()
        surface.close()
    }
}

/** Writes a JPEG thumbnail, closing the intermediate Skia objects even when encoding fails. */
internal fun writeThumbnailJpeg(target: Path, bitmap: ImageBitmap) {
    try {
        Files.createDirectories(target.parent)
        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        try {
            val bytes = image.encodeToData(EncodedImageFormat.JPEG, 88)?.use { it.bytes } ?: return
            val tmp = Files.createTempFile(target.parent, "thumb-", ".tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            image.close()
        }
    } catch (error: Exception) {
        DesktopLogger.warn("ImageLoader", "Failed to write thumbnail $target", error)
    }
}
