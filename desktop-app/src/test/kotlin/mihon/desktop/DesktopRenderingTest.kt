package mihon.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class DesktopRenderingTest {
    @TempDir lateinit var directory: Path

    private fun packagedProperties() = Properties().apply {
        setProperty("os.name", "Windows 11")
        setProperty("skiko.library.path", directory.toString())
        listOf("libEGL.dll", "libGLESv2.dll", "d3dcompiler_47.dll").forEach {
            Files.write(directory.resolve(it), byteArrayOf(1))
        }
    }

    @Test
    fun `packaged Windows rendering prefers ANGLE without disabling display sync`() {
        val properties = packagedProperties()
        configureDesktopRendering(properties, emptyMap())
        assertEquals("true", properties.getProperty("skiko.rendering.angle.enabled"))
        assertNull(properties.getProperty("skiko.vsync.enabled"))
        assertNull(properties.getProperty("skiko.renderApi"))
    }

    @Test
    fun `renderer overrides and explicit ANGLE opt out are preserved`() {
        listOf("skiko.renderApi" to "OPENGL", "skiko.rendering.angle.enabled" to "false").forEach { (key, value) ->
            val properties = packagedProperties().apply { setProperty(key, value) }
            val before = Properties().apply { putAll(properties) }
            configureDesktopRendering(properties, emptyMap())
            assertEquals(before, properties)
        }
        val properties = packagedProperties()
        configureDesktopRendering(properties, mapOf("SKIKO_RENDER_API" to "SOFTWARE_COMPAT"))
        assertNull(properties.getProperty("skiko.rendering.angle.enabled"))
    }

    @Test
    fun `missing native payload and other platforms retain Skiko defaults`() {
        val properties = packagedProperties()
        Files.delete(directory.resolve("libGLESv2.dll"))
        configureDesktopRendering(properties, emptyMap())
        assertNull(properties.getProperty("skiko.rendering.angle.enabled"))
        val linux = packagedProperties().apply { setProperty("os.name", "Linux") }
        configureDesktopRendering(linux, emptyMap())
        assertNull(linux.getProperty("skiko.rendering.angle.enabled"))
    }
}
