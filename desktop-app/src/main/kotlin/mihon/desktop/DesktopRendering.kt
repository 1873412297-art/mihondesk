package mihon.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Prefer the packaged ANGLE backend; Skiko retains its Direct3D/OpenGL/software fallback chain. */
internal fun configureDesktopRendering(
    properties: Properties = System.getProperties(),
    environment: Map<String, String> = System.getenv(),
) {
    if (!properties.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return
    if (!environment["SKIKO_RENDER_API"].isNullOrBlank() || properties.containsKey("skiko.renderApi") ||
        properties.containsKey("skiko.rendering.angle.enabled")
    ) {
        return
    }
    val nativeDirectory = properties.getProperty("skiko.library.path")
        ?.let { runCatching { Path.of(it) }.getOrNull() } ?: return
    if (listOf("libEGL.dll", "libGLESv2.dll", "d3dcompiler_47.dll").all {
            Files.isRegularFile(nativeDirectory.resolve(it))
        }
    ) {
        properties.setProperty("skiko.rendering.angle.enabled", "true")
    }
}
