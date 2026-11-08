package mihon.desktop

import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties

class RenderBackendTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `automatic leaves skiko's own fallback chain alone`() {
        val properties = Properties()
        applyRenderBackend(RenderBackend.AUTO, properties, emptyMap())
        properties.isEmpty shouldBe true
    }

    @Test
    fun `software compatibility mode pins the software renderer and skips angle`() {
        val properties = Properties()
        applyRenderBackend(RenderBackend.SOFTWARE, properties, emptyMap())
        properties.getProperty("skiko.renderApi") shouldBe "SOFTWARE_COMPAT"
        properties.getProperty("skiko.rendering.angle.enabled") shouldBe "false"
    }

    @Test
    fun `opengl does not route through angle and direct3d keeps it`() {
        val opengl = Properties().also { applyRenderBackend(RenderBackend.OPENGL, it, emptyMap()) }
        opengl.getProperty("skiko.renderApi") shouldBe "OPENGL"
        opengl.getProperty("skiko.rendering.angle.enabled") shouldBe "false"
        val direct3d = Properties().also { applyRenderBackend(RenderBackend.DIRECT3D, it, emptyMap()) }
        direct3d.getProperty("skiko.renderApi") shouldBe "DIRECT3D"
        direct3d.getProperty("skiko.rendering.angle.enabled") shouldBe null
    }

    @Test
    fun `an explicit override from the environment or command line wins`() {
        val fromEnv = Properties()
        applyRenderBackend(RenderBackend.SOFTWARE, fromEnv, mapOf("SKIKO_RENDER_API" to "OPENGL"))
        fromEnv.isEmpty shouldBe true
        val fromArgs = Properties().apply { setProperty("skiko.renderApi", "METAL") }
        applyRenderBackend(RenderBackend.SOFTWARE, fromArgs, emptyMap())
        fromArgs.getProperty("skiko.renderApi") shouldBe "METAL"
    }

    @Test
    fun `the choice round-trips and unknown values fall back to automatic`() {
        val store = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        RenderBackend.load(store) shouldBe RenderBackend.AUTO
        RenderBackend.save(store, RenderBackend.OPENGL)
        RenderBackend.load(DesktopPreferenceStore(tempDir.resolve("preferences.properties"))) shouldBe
            RenderBackend.OPENGL
        store.update { setProperty(RenderBackend.PREFERENCE_KEY, "VULKAN") }
        RenderBackend.load(store) shouldBe RenderBackend.AUTO
    }
}
