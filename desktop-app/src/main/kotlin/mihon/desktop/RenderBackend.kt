package mihon.desktop

import mihon.desktop.preferences.DesktopPreferenceStore
import java.util.Properties

/**
 * The renderer a user can force from settings, for drivers that crash or draw garbage.
 *
 * AUTO keeps Skiko's own fallback chain (ANGLE/Direct3D, then OpenGL, then software), which already
 * handles a backend that fails to initialize. The others pin one backend; they exist for drivers
 * that initialize fine and then misbehave, which no automatic probe can detect.
 * Takes effect on the next start: Skiko picks its backend once, before the first window.
 */
enum class RenderBackend(val skikoApi: String?) {
    AUTO(null),
    DIRECT3D("DIRECT3D"),
    OPENGL("OPENGL"),
    SOFTWARE("SOFTWARE_COMPAT"),
    ;

    companion object {
        const val PREFERENCE_KEY = "window.render_backend"

        /** The store already falls back to empty properties when the file cannot be read. */
        fun load(store: DesktopPreferenceStore): RenderBackend =
            store.property(PREFERENCE_KEY)?.let { name -> entries.firstOrNull { it.name == name } } ?: AUTO

        fun save(store: DesktopPreferenceStore, backend: RenderBackend) {
            store.update { setProperty(PREFERENCE_KEY, backend.name) }
        }
    }
}

/**
 * Applies a forced backend before [configureDesktopRendering]. A `SKIKO_RENDER_API` variable or an
 * explicit `-Dskiko.renderApi` still wins, so support instructions that use them keep working.
 */
internal fun applyRenderBackend(
    backend: RenderBackend,
    properties: Properties = System.getProperties(),
    environment: Map<String, String> = System.getenv(),
) {
    // A profile copied from Windows may still carry DIRECT3D; it would break the macOS Skiko start.
    if (backend == RenderBackend.DIRECT3D && !mihon.desktop.platform.DesktopPlatform.isWindows) return
    val api = backend.skikoApi ?: return
    if (!environment["SKIKO_RENDER_API"].isNullOrBlank() || properties.containsKey("skiko.renderApi")) return
    properties.setProperty("skiko.renderApi", api)
    // ANGLE is a Direct3D translation layer; pinning OpenGL or software must not route through it.
    if (backend != RenderBackend.DIRECT3D) properties.setProperty("skiko.rendering.angle.enabled", "false")
}

/** The user's forced backend from `<profile>/preferences.properties`, then the packaged defaults. */
internal fun configureDesktopRendering(profileRoot: java.nio.file.Path) {
    applyRenderBackend(RenderBackend.load(DesktopPreferenceStore(profileRoot.resolve("preferences.properties"))))
    configureDesktopRendering()
}
