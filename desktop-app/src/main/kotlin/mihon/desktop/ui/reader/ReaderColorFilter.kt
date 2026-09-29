package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter

// Invert color matrix: r' = 255 - r, g' = 255 - g, b' = 255 - b
internal val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Grayscale matrix using standard luminance weights
internal val GrayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }

// Invert + Grayscale matrix
internal val InvertGrayscaleMatrix = ColorMatrix(
    floatArrayOf(
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Sepia eye-care warm tone matrix
internal val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Night mode matrix: softened brightness with warm bias
internal val NightModeMatrix = ColorMatrix(
    floatArrayOf(
        0.75f, 0f, 0f, 0f, 10f,
        0f, 0.70f, 0f, 0f, 10f,
        0f, 0f, 0.60f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

fun ReaderColorFilter.toComposeColorFilter(): ColorFilter? = when (this) {
    ReaderColorFilter.NONE -> null
    ReaderColorFilter.INVERT -> ColorFilter.colorMatrix(InvertMatrix)
    ReaderColorFilter.GRAYSCALE -> ColorFilter.colorMatrix(GrayscaleMatrix)
    ReaderColorFilter.INVERT_GRAYSCALE -> ColorFilter.colorMatrix(InvertGrayscaleMatrix)
    ReaderColorFilter.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
    ReaderColorFilter.NIGHT -> ColorFilter.colorMatrix(NightModeMatrix)
    ReaderColorFilter.CUSTOM -> ColorFilter.colorMatrix(customColorMatrix(0, 0, 0))
}

fun baseMatrixFor(filter: ReaderColorFilter): ColorMatrix? = when (filter) {
    ReaderColorFilter.NONE -> null
    ReaderColorFilter.INVERT -> InvertMatrix
    ReaderColorFilter.GRAYSCALE -> GrayscaleMatrix
    ReaderColorFilter.INVERT_GRAYSCALE -> InvertGrayscaleMatrix
    ReaderColorFilter.SEPIA -> SepiaMatrix
    ReaderColorFilter.NIGHT -> NightModeMatrix
    ReaderColorFilter.CUSTOM -> null
}

fun customColorMatrix(
    hue: Int = 0,
    brightness: Int = 0,
    contrast: Int = 0,
    baseMatrix: ColorMatrix? = null,
): ColorMatrix {
    val rad = Math.toRadians(hue.toDouble())
    val cosVal = kotlin.math.cos(rad).toFloat()
    val sinVal = kotlin.math.sin(rad).toFloat()

    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f

    val h00 = lr + cosVal * (1f - lr) - sinVal * lr
    val h01 = lg - cosVal * lg - sinVal * lg
    val h02 = lb - cosVal * lb + sinVal * (1f - lb)

    val h10 = lr - cosVal * lr + sinVal * 0.143f
    val h11 = lg + cosVal * (1f - lg) + sinVal * 0.140f
    val h12 = lb - cosVal * lb - sinVal * 0.283f

    val h20 = lr - cosVal * lr - sinVal * (1f - lr)
    val h21 = lg - cosVal * lg + sinVal * lg
    val h22 = lb + cosVal * (1f - lb) + sinVal * lb

    val contrastClamped = contrast.coerceIn(-100, 100)
    val c = (100f + contrastClamped) / 100f

    val brightnessClamped = brightness.coerceIn(-100, 100)
    val bOffset = brightnessClamped * 2.55f
    val offset = 128f * (1f - c) + bOffset

    val customValues = floatArrayOf(
        c * h00, c * h01, c * h02, 0f, offset,
        c * h10, c * h11, c * h12, 0f, offset,
        c * h20, c * h21, c * h22, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )

    val finalValues = if (baseMatrix != null) {
        multiplyColorMatrices(customValues, baseMatrix.values)
    } else {
        customValues
    }
    return ColorMatrix(finalValues)
}

internal fun multiplyColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0..3) {
        val rowOffset = row * 5
        for (col in 0..3) {
            result[rowOffset + col] =
                a[rowOffset + 0] * b[0 * 5 + col] +
                a[rowOffset + 1] * b[1 * 5 + col] +
                a[rowOffset + 2] * b[2 * 5 + col] +
                a[rowOffset + 3] * b[3 * 5 + col]
        }
        result[rowOffset + 4] =
            a[rowOffset + 0] * b[0 * 5 + 4] +
            a[rowOffset + 1] * b[1 * 5 + 4] +
            a[rowOffset + 2] * b[2 * 5 + 4] +
            a[rowOffset + 3] * b[3 * 5 + 4] +
            a[rowOffset + 4]
    }
    return result
}

fun mihon.desktop.reader.DesktopReaderSettings.toComposeColorFilter(): ColorFilter? = when (colorFilter) {
    ReaderColorFilter.NONE -> {
        if (customHue != 0 || customBrightness != 0 || customContrast != 0) {
            ColorFilter.colorMatrix(customColorMatrix(customHue, customBrightness, customContrast))
        } else {
            null
        }
    }
    ReaderColorFilter.CUSTOM -> {
        ColorFilter.colorMatrix(customColorMatrix(customHue, customBrightness, customContrast))
    }
    else -> {
        val base = baseMatrixFor(colorFilter)
        if (customHue != 0 || customBrightness != 0 || customContrast != 0) {
            ColorFilter.colorMatrix(customColorMatrix(customHue, customBrightness, customContrast, base))
        } else {
            colorFilter.toComposeColorFilter()
        }
    }
}

fun ReaderBackgroundColor.toComposeColor(): Color = when (this) {
    ReaderBackgroundColor.DARK_GRAY -> Color(0xff101010)
    ReaderBackgroundColor.BLACK -> Color(0xff000000)
    ReaderBackgroundColor.WHITE -> Color(0xffffffff)
    ReaderBackgroundColor.WARM_CREAM -> Color(0xfff5efeb)
}
