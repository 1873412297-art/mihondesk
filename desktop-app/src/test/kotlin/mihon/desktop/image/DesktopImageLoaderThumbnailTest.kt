package mihon.desktop.image

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class DesktopImageLoaderThumbnailTest {
    @TempDir
    lateinit var tempDir: Path

    private fun writePng(path: Path, width: Int, height: Int) {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(Color.makeRGB(200, 40, 40))
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!
        Files.write(path, data.bytes)
    }

    private fun thumbFiles(cache: Path): List<Path> {
        val dir = cache.resolve(DesktopImageLoader.THUMBNAIL_DIR)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { it.toList() }
    }

    @Test
    fun `thumbnail requests are downscaled and persisted to the disk cache`() = runBlocking {
        val cover = tempDir.resolve("cover.png")
        writePng(cover, 1200, 1800)
        val cache = tempDir.resolve("cache")
        val loader = DesktopImageLoader(cache)

        val bitmap = loader.load(ImageRequest(uri = cover.toString(), maxThumbnailSize = 300))

        assertNotNull(bitmap)
        assertEquals(300, bitmap!!.height)
        assertEquals(200, bitmap.width)
        assertEquals(1, thumbFiles(cache).size)
    }

    @Test
    fun `a fresh loader serves the cached thumbnail without decoding the original`() = runBlocking {
        val cover = tempDir.resolve("cover.png")
        writePng(cover, 1200, 1800)
        val cache = tempDir.resolve("cache")
        DesktopImageLoader(cache).load(ImageRequest(uri = cover.toString(), maxThumbnailSize = 300))

        // Corrupt the original but keep its mtime: only the thumbnail cache can satisfy the request now.
        val mtime = Files.getLastModifiedTime(cover)
        Files.write(cover, byteArrayOf(1, 2, 3))
        Files.setLastModifiedTime(cover, mtime)

        val bitmap = DesktopImageLoader(cache).load(ImageRequest(uri = cover.toString(), maxThumbnailSize = 300))
        assertNotNull(bitmap)
        assertEquals(300, bitmap!!.height)
    }

    @Test
    fun `a changed original invalidates the cached thumbnail`() = runBlocking {
        val cover = tempDir.resolve("cover.png")
        writePng(cover, 1200, 1800)
        val cache = tempDir.resolve("cache")
        DesktopImageLoader(cache).load(ImageRequest(uri = cover.toString(), maxThumbnailSize = 300))

        Files.write(cover, byteArrayOf(1, 2, 3))
        Files.setLastModifiedTime(cover, FileTime.fromMillis(System.currentTimeMillis() + 60_000))

        assertNull(DesktopImageLoader(cache).load(ImageRequest(uri = cover.toString(), maxThumbnailSize = 300)))
    }

    @Test
    fun `full size requests are unchanged`() = runBlocking {
        val cover = tempDir.resolve("cover.png")
        writePng(cover, 1200, 1800)
        val cache = tempDir.resolve("cache")
        val bitmap = DesktopImageLoader(cache).load(ImageRequest(uri = cover.toString()))
        assertEquals(1800, bitmap!!.height)
        assertTrue(thumbFiles(cache).isEmpty())
    }
}
