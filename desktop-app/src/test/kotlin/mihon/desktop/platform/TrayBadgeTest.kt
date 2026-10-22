package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class TrayBadgeTest {
    private fun base() = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
        for (x in 0 until 32) for (y in 0 until 32) setRGB(x, y, 0xFF336699.toInt())
    }

    @Test
    fun `zero count leaves the icon untouched`() {
        val image = base()
        TrayBadge.render(image, 0).getRGB(28, 3) shouldBe 0xFF336699.toInt()
    }

    @Test
    fun `a positive count paints the badge in the top-right corner only`() {
        val badged = TrayBadge.render(base(), 4)
        (badged.getRGB(16, 9) and 0xFFFFFF) shouldBe TrayBadge.BADGE_RGB
        badged.getRGB(2, 29) shouldBe 0xFF336699.toInt()
    }

    @Test
    fun `labels cap at 99+`() {
        TrayBadge.label(7) shouldBe "7"
        TrayBadge.label(120) shouldBe "99+"
    }
}
