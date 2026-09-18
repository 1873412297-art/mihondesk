package mihon.desktop.reader

import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Generated artwork only; distinct pages make accidental page reuse visible. */
internal object LongReaderFixture {
    const val PAGE_COUNT = 256
    const val TITLE = "long-reader-fixture"

    fun build(root: Path): Path {
        Files.createDirectories(root)
        val manga = Files.createDirectory(root.resolve(TITLE))
        val directory = Files.createDirectory(manga.resolve("01-directory"))
        val archive = manga.resolve("02-pages.cbz")
        val manifest = mutableListOf<String>()
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            repeat(PAGE_COUNT) { index ->
                val name = "%04d.jpg".format(index + 1)
                val bytes = page(index)
                val target = directory.resolve(name)
                Files.write(target, bytes)
                manifest += "${sha(bytes)}  $TITLE/01-directory/$name"
                zip.putNextEntry(ZipEntry(name).apply { time = 946684800000L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        manifest += "${sha(Files.readAllBytes(archive))}  $TITLE/02-pages.cbz"
        Files.write(root.resolve("SHA256SUMS.txt"), manifest)
        return manga
    }

    private fun page(index: Int): ByteArray {
        val width = if (index % 2 == 0) 800 else 1200
        val height = width * 3 / 2
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            repeat(height) { y ->
                graphics.color = Color((index * 31 + y / 8) % 256, (y / 7) % 256, (index * 17) % 256)
                graphics.drawLine(0, y, width, y)
            }
            graphics.color = Color.WHITE
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 64)
            graphics.drawString("PAGE ${index + 1}", 60, 110)
            repeat(24) { line ->
                val x = 40 + (index * 13 + line * 37) % (width - 80)
                graphics.drawLine(x, 180, width - x, height - 60)
            }
        } finally {
            graphics.dispose()
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(ImageIO.write(image, "jpeg", output))
                output.toByteArray()
            }
        } finally {
            image.flush()
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
