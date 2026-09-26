package mihon.desktop.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * Encodes [uri] as a QR code and returns an [ImageBitmap], or null when [uri] is blank
 * or encoding fails.
 *
 * This is a pure function – it has no side-effects and is safe to call from unit tests.
 *
 * @param uri  The URI string to encode (e.g. `mihonsync://host:port#token`).
 * @param size Pixel dimension of the square QR code bitmap.
 */
fun generateQrCodeBitmap(uri: String, size: Int = 400): ImageBitmap? {
    if (uri.isBlank()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix: BitMatrix = MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { idx ->
            val x = idx % size
            val y = idx / size
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val px = pixels[i]
            bytes[i * 4 + 0] = ((px shr 16) and 0xFF).toByte() // R
            bytes[i * 4 + 1] = ((px shr 8) and 0xFF).toByte() // G
            bytes[i * 4 + 2] = (px and 0xFF).toByte() // B
            bytes[i * 4 + 3] = ((px shr 24) and 0xFF).toByte() // A
        }
        SkiaImage.makeRaster(
            imageInfo = org.jetbrains.skia.ImageInfo.makeN32Premul(size, size),
            bytes = bytes,
            rowBytes = size * 4,
        ).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

/**
 * Composable that renders a QR code for [uri].
 *
 * Displays nothing when [uri] is blank. The bitmap is generated off the UI thread
 * (recomputed only when [uri] changes); the [sizeInDp] space is reserved while it loads.
 *
 * @param uri        The URI to encode into a QR code.
 * @param sizeInDp   Side length of the displayed image in density-independent pixels.
 * @param modifier   Additional modifiers.
 */
@Composable
fun QrCodeImage(
    uri: String,
    sizeInDp: Dp = 200.dp,
    modifier: Modifier = Modifier,
) {
    if (uri.isBlank()) return
    // ZXing encoding a 400px matrix plus the Skia raster copy is tens of ms of CPU;
    // keep it off the UI thread so opening a screen containing a QR code doesn't jank.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.Default) { generateQrCodeBitmap(uri) }
    }
    // Reserve the space immediately so the layout doesn't jump when the bitmap arrives.
    Box(modifier = modifier.size(sizeInDp).testTag("sync-server-qr")) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(sizeInDp),
            )
        }
    }
}
