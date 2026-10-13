package mihon.desktop.extension

import mihon.desktop.extension.compat.ApkIconExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkIconExtractorTest {
    @Test
    fun `heuristic prefers highest density plain launcher over adaptive layers`() {
        val entries = listOf(
            "res/mipmap-anydpi-v26/ic_launcher.xml" to 400L,
            "res/mipmap-mdpi-v4/ic_launcher.png" to 1_000L,
            "res/mipmap-xxxhdpi-v4/ic_launcher.png" to 9_000L,
            "res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png" to 20_000L,
            "res/mipmap-xxhdpi-v4/ic_launcher.webp" to 6_000L,
            "res/drawable-xxxhdpi-v4/notification.png" to 30_000L,
        )
        assertEquals("res/mipmap-xxxhdpi-v4/ic_launcher.png", ApkIconExtractor.heuristicIconPath(entries))
    }

    @Test
    fun `heuristic falls back to any res bitmap and ignores non res entries`() {
        assertEquals(
            "res/drawable-hdpi/logo.webp",
            ApkIconExtractor.heuristicIconPath(listOf("assets/x.png" to 5L, "res/drawable-hdpi/logo.webp" to 3L)),
        )
        assertNull(ApkIconExtractor.heuristicIconPath(listOf("assets/x.png" to 5L, "classes.dex" to 1L)))
    }

    @Test
    fun `extract returns bytes from an apk without resource table`(@TempDir dir: File) {
        val apk = File(dir, "a.apk")
        val png =
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0)
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("res/mipmap-xhdpi-v4/ic_launcher.png"))
            zos.write(png)
            zos.closeEntry()
        }
        val bytes = ZipFile(apk).use { ApkIconExtractor.extract(it) }
        assertNotNull(bytes)
        assertEquals(png.toList(), bytes!!.toList())
    }

    @Test
    fun `manifest without application icon yields null res id`() {
        assertNull(ApkIconExtractor.applicationIconResId(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `real obfuscated keiyoushi apk resolves launcher icon through resources arsc`(@TempDir dir: File) {
        val apk = File(dir, "vomic.apk")
        javaClass.getResourceAsStream("/apk-icon/tachiyomi-zh.vomic-v1.4.6.apk")!!.use { input ->
            apk.outputStream().use { input.copyTo(it) }
        }
        ZipFile(apk).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            val resId = ApkIconExtractor.applicationIconResId(manifest)
            assertNotNull(resId)
            val arsc = zip.getInputStream(zip.getEntry("resources.arsc")).use { it.readBytes() }
            val paths = ApkIconExtractor.resolveFilePaths(arsc, resId!!)
            assertEquals(5, paths.size, "one bitmap per density: $paths")
            // Highest density (xxxhdpi) is the largest bitmap in this APK.
            val largest = zip.entries().asSequence().filter { it.name.startsWith("res/") }.maxBy { it.size }.name
            assertEquals(largest, ApkIconExtractor.resolveIconPath(zip))
            val png = ApkIconExtractor.extract(zip)
            assertNotNull(png)
            assertEquals(0x89.toByte(), png!![0])
        }
    }
}
