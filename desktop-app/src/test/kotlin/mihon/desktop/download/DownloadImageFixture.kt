package mihon.desktop.download

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO

internal fun validDownloadImage(): ByteArray = ByteArrayOutputStream().use { output ->
    ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
    output.toByteArray()
}

/** A real PNG whose aspect ratio makes it a candidate for splitting. */
internal fun tallDownloadImage(width: Int, height: Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width, height / 2)
    } finally {
        graphics.dispose()
    }
    return ByteArrayOutputStream().use { output ->
        ImageIO.write(image, "png", output)
        output.toByteArray()
    }
}

internal fun animatedGifImage(width: Int, height: Int, frameCount: Int): ByteArray =
    ByteArrayOutputStream().use { output ->
        val writers = ImageIO.getImageWritersByFormatName("gif")
        val writer = if (writers.hasNext()) {
            writers.next()
        } else {
            error("no GIF writer is available")
        }
        ImageIO.createImageOutputStream(output).use { imageOutput ->
            writer.output = imageOutput
            writer.prepareWriteSequence(null)
            repeat(frameCount) { index ->
                val frame = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                val graphics = frame.createGraphics()
                try {
                    graphics.color = if (index == 0) Color.RED else Color.BLUE
                    graphics.fillRect(0, 0, width, height)
                } finally {
                    graphics.dispose()
                }
                writer.writeToSequence(IIOImage(frame, null, null), writer.defaultWriteParam)
            }
            writer.endWriteSequence()
            writer.dispose()
        }
        output.toByteArray()
    }
