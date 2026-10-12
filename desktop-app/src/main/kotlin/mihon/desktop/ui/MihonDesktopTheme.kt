package mihon.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry

@Composable
fun MihonDesktopTheme(
    themeMode: ThemeMode = ThemeMode.System,
    appTheme: DesktopAppTheme = DesktopAppTheme.DEFAULT,
    isAmoled: Boolean = false,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeMode)
    val baseScheme = ThemeRegistry.getColorScheme(appTheme)
    val colorScheme = baseScheme.getColorScheme(isDark = dark, isAmoled = isAmoled)

    MaterialTheme(colorScheme = colorScheme) {
        if (topBar == null) {
            content()
        } else {
            // Window caption (custom title bar) stacked above the app content, sharing the theme.
            Column(Modifier.fillMaxSize()) {
                topBar()
                Box(Modifier.weight(1f).fillMaxSize()) { content() }
            }
        }
    }
}

/** Whether [themeMode] currently resolves to a dark scheme; shared by the theme and the native title bar. */
@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
