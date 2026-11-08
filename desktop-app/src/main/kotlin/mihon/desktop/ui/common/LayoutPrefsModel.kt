package mihon.desktop.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mihon.desktop.preferences.DesktopPreferenceStore

/**
 * Observable, injected home for the shell layout preferences that used to live in a process-wide
 * mutable object: content density, the collapsed navigation rail and Ctrl+B toggle requests.
 * [mihon.desktop.ui.appShellContext] creates one instance and shares it through
 * [mihon.desktop.ui.AppShellContext], so the shell, Settings and the shortcut routers all talk to
 * the same object without a mutable singleton.
 */
internal class LayoutPrefsModel(private val store: DesktopPreferenceStore?) {
    var contentDensity by mutableStateOf(ContentDensity.load(store))
        private set
    var railCollapsed by mutableStateOf(store.loadRailCollapsed())
        private set
    var railToggleRequests by mutableIntStateOf(0)
        private set

    fun updateContentDensity(density: ContentDensity) {
        contentDensity = density
        store?.update { setProperty(ContentDensity.PREF_KEY, density.name) }
    }

    fun updateRailCollapsed(collapsed: Boolean) {
        railCollapsed = collapsed
        store?.update { setProperty(NavRailState.PREF_KEY_COLLAPSED, collapsed.toString()) }
    }

    fun requestRailToggle() {
        railToggleRequests++
    }
}

private fun DesktopPreferenceStore?.loadRailCollapsed(): Boolean =
    this?.property(NavRailState.PREF_KEY_COLLAPSED)?.toBooleanStrictOrNull() ?: false
