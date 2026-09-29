package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter
import org.junit.jupiter.api.Test

class ReaderColorFilterTest {

    @Test
    fun `none filter returns null compose ColorFilter for zero overhead`() {
        ReaderColorFilter.NONE.toComposeColorFilter().shouldBeNull()
    }

    @Test
    fun `all non-none filters return valid ColorFilter instances`() {
        val nonNone = listOf(
            ReaderColorFilter.INVERT,
            ReaderColorFilter.GRAYSCALE,
            ReaderColorFilter.INVERT_GRAYSCALE,
            ReaderColorFilter.SEPIA,
            ReaderColorFilter.NIGHT,
            ReaderColorFilter.CUSTOM,
        )

        nonNone.forEach { filter ->
            filter.toComposeColorFilter().shouldNotBeNull()
        }
    }

    @Test
    fun `custom color matrix with zero adjustments equals identity matrix`() {
        val matrix = customColorMatrix(hue = 0, brightness = 0, contrast = 0)
        val v = matrix.values
        v.size shouldBe 20

        // Diagonal elements should be 1
        v[0] shouldBe 1f
        v[6] shouldBe 1f
        v[12] shouldBe 1f
        v[18] shouldBe 1f

        // Translation offsets should be 0
        v[4] shouldBe 0f
        v[9] shouldBe 0f
        v[14] shouldBe 0f
        v[19] shouldBe 0f

        // Non-diagonal color entries should be 0
        v[1] shouldBe 0f
        v[2] shouldBe 0f
        v[5] shouldBe 0f
        v[7] shouldBe 0f
        v[10] shouldBe 0f
        v[11] shouldBe 0f
    }

    @Test
    fun `custom brightness scales color channel offsets linearly`() {
        val bright = customColorMatrix(brightness = 50).values
        bright[4] shouldBe 127.5f
        bright[9] shouldBe 127.5f
        bright[14] shouldBe 127.5f

        val dark = customColorMatrix(brightness = -50).values
        dark[4] shouldBe -127.5f
        dark[9] shouldBe -127.5f
        dark[14] shouldBe -127.5f
    }

    @Test
    fun `custom contrast centers around mid gray`() {
        val high = customColorMatrix(contrast = 50).values
        high[0] shouldBe 1.5f
        high[6] shouldBe 1.5f
        high[12] shouldBe 1.5f
        high[4] shouldBe -64f
        high[9] shouldBe -64f
        high[14] shouldBe -64f

        val zero = customColorMatrix(contrast = -100).values
        zero[0] shouldBe 0f
        zero[6] shouldBe 0f
        zero[12] shouldBe 0f
        zero[4] shouldBe 128f
        zero[9] shouldBe 128f
        zero[14] shouldBe 128f
    }

    @Test
    fun `custom hue rotation and extreme inputs remain numerically stable and clamped`() {
        val m1 = customColorMatrix(hue = 180, brightness = 20, contrast = -10)
        val m2 = customColorMatrix(hue = 180, brightness = 20, contrast = -10)
        m1.values shouldBe m2.values

        val extreme = customColorMatrix(hue = 720, brightness = 999, contrast = -999).values
        extreme.forEach { value ->
            value.isNaN() shouldBe false
            value.isInfinite() shouldBe false
        }
        // Brightness clamped to 100 -> offset contribution 255f
        // Contrast clamped to -100 -> c = 0, offset contribution 128f -> total offset 383f
        extreme[4] shouldBe 383f
    }

    @Test
    fun `desktop reader settings toComposeColorFilter obeys filter and custom adjustments`() {
        mihon.desktop.reader.DesktopReaderSettings(
            colorFilter = ReaderColorFilter.NONE,
        ).toComposeColorFilter().shouldBeNull()

        mihon.desktop.reader.DesktopReaderSettings(
            colorFilter = ReaderColorFilter.CUSTOM,
            customHue = 90,
        ).toComposeColorFilter().shouldNotBeNull()

        mihon.desktop.reader.DesktopReaderSettings(
            colorFilter = ReaderColorFilter.NONE,
            customBrightness = 30,
        ).toComposeColorFilter().shouldNotBeNull()

        mihon.desktop.reader.DesktopReaderSettings(
            colorFilter = ReaderColorFilter.SEPIA,
            customBrightness = 20,
        ).toComposeColorFilter().shouldNotBeNull()
    }

    @Test
    fun `reader background colors map to expected compose colors`() {
        ReaderBackgroundColor.DARK_GRAY.toComposeColor() shouldBe Color(0xff101010)
        ReaderBackgroundColor.BLACK.toComposeColor() shouldBe Color(0xff000000)
        ReaderBackgroundColor.WHITE.toComposeColor() shouldBe Color(0xffffffff)
        ReaderBackgroundColor.WARM_CREAM.toComposeColor() shouldBe Color(0xfff5efeb)
    }

    @Test
    fun `invert color matrix has negative diagonals`() {
        val values = InvertMatrix.values
        values.size shouldBe 20
        // R = 255 - R
        values[0] shouldBe -1f
        values[4] shouldBe 255f
        // G = 255 - G
        values[6] shouldBe -1f
        values[9] shouldBe 255f
        // B = 255 - B
        values[12] shouldBe -1f
        values[14] shouldBe 255f
    }
}
