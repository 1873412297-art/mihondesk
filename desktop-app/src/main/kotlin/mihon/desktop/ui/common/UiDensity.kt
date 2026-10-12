package mihon.desktop.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shell-wide layout density. Compact scales the whole UI down slightly on top of OS DPI scaling. */
enum class UiDensity(val scale: Float, val contentPadding: Dp) {
    Standard(1f, 32.dp),
    Compact(0.9f, 16.dp),
    ;

    fun toggled(): UiDensity = if (this == Standard) Compact else Standard

    fun apply(base: Density): Density = Density(base.density * scale, base.fontScale)

    companion object {
        const val PREF_KEY = "ui.density"
        const val PREF_KEY_RAIL_COLLAPSED = "ui.navigation_rail_collapsed"

        fun parse(raw: String?): UiDensity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Standard
    }
}

val LocalUiDensity = staticCompositionLocalOf { UiDensity.Standard }
