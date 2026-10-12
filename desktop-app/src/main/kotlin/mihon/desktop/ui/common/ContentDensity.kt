package mihon.desktop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mihon.desktop.preferences.DesktopPreferenceStore

/**
 * Spacing tokens for library/settings/page chrome. Content density only changes spacing,
 * padding and row heights; it never touches [androidx.compose.ui.platform.LocalDensity], so text
 * size and the reader are unaffected.
 */
data class Spacing(
    val pagePadding: Dp,
    val gridScale: Float,
    val gridGap: Dp,
    val gridRowGap: Dp,
    val listRowGap: Dp,
    val listRowPadding: Dp,
    val listCoverHeight: Dp,
    val cardPadding: Dp,
    val sectionGap: Dp,
)

enum class ContentDensity(val spacing: Spacing) {
    Standard(
        Spacing(
            pagePadding = 32.dp,
            gridScale = 1f,
            gridGap = 16.dp,
            gridRowGap = 18.dp,
            listRowGap = 8.dp,
            listRowPadding = 10.dp,
            listCoverHeight = 78.dp,
            cardPadding = 16.dp,
            sectionGap = 16.dp,
        ),
    ),
    Compact(
        Spacing(
            pagePadding = 16.dp,
            gridScale = 0.5f,
            gridGap = 8.dp,
            gridRowGap = 10.dp,
            listRowGap = 4.dp,
            listRowPadding = 6.dp,
            listCoverHeight = 60.dp,
            cardPadding = 12.dp,
            sectionGap = 10.dp,
        ),
    ),
    ;

    companion object {
        const val PREF_KEY = "ui.content_density"

        /** Pre-0.2.30 key that also scaled LocalDensity by 0.9. */
        const val LEGACY_PREF_KEY = "ui.density"

        fun parse(raw: String?): ContentDensity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Standard

        /** Reads the content density, migrating the legacy `ui.density` value once. */
        fun load(store: DesktopPreferenceStore?): ContentDensity {
            if (store == null) return Standard
            store.property(PREF_KEY)?.let { return parse(it) }
            val legacy = store.property(LEGACY_PREF_KEY) ?: return Standard
            val migrated = parse(legacy)
            store.update {
                setProperty(PREF_KEY, migrated.name)
                remove(LEGACY_PREF_KEY)
            }
            return migrated
        }
    }
}

/** Resolved navigation rail presentation. Independent from [ContentDensity]. */
enum class NavRailState {
    Expanded,
    Collapsed,
    OverlayExpanded,
    ;

    /** Collapsed rail is icon-only (labels via tooltip); expanded variants show icon+label. */
    val showsLabels: Boolean get() = this != Collapsed

    companion object {
        const val PREF_KEY_COLLAPSED = "ui.navigation_rail_collapsed"
        val AUTO_COLLAPSE_WIDTH: Dp = 1200.dp

        fun resolve(userCollapsed: Boolean, narrow: Boolean, overlayOpen: Boolean): NavRailState = when {
            narrow -> if (overlayOpen) OverlayExpanded else Collapsed
            userCollapsed -> Collapsed
            else -> Expanded
        }
    }
}

/** App-wide observable layout prefs, shared by the shell, Settings and the Ctrl+B shortcut. */
object DesktopLayoutPrefs {
    private var boundStore: DesktopPreferenceStore? = null
    var contentDensity by mutableStateOf(ContentDensity.Standard)
        private set
    var railCollapsed by mutableStateOf(false)
        private set
    var railToggleRequests by mutableIntStateOf(0)
        private set

    fun bind(store: DesktopPreferenceStore?) {
        if (store === boundStore && store != null) return
        boundStore = store
        contentDensity = ContentDensity.load(store)
        railCollapsed = store?.property(NavRailState.PREF_KEY_COLLAPSED)?.toBooleanStrictOrNull() ?: false
    }

    fun updateContentDensity(store: DesktopPreferenceStore?, density: ContentDensity) {
        contentDensity = density
        store?.update { setProperty(ContentDensity.PREF_KEY, density.name) }
    }

    fun updateRailCollapsed(store: DesktopPreferenceStore?, collapsed: Boolean) {
        railCollapsed = collapsed
        store?.update { setProperty(NavRailState.PREF_KEY_COLLAPSED, collapsed.toString()) }
    }

    fun requestRailToggle() {
        railToggleRequests++
    }
}

val LocalSpacing = staticCompositionLocalOf { ContentDensity.Standard.spacing }

/** Provides spacing only; deliberately leaves LocalDensity alone. */
@Composable
fun ProvideContentDensity(density: ContentDensity, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides density.spacing, content = content)
}
