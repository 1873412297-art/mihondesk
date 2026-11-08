package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.common.ContentDensity
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry

@Composable
internal fun AppearanceSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    contentDensity: ContentDensity,
    onContentDensityChange: (ContentDensity) -> Unit,
) {
    val strings = LocalStrings.current
    val preferences = presenter.preferences.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sectionGap),
    ) {
        Text(
            strings.settingsSectionAppearance,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // 1. Theme Mode (System / Light / Dark)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(LocalSpacing.current.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(strings.settingsThemeModeTitle, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        ThemeMode.System to strings.settingsThemeSystem,
                        ThemeMode.Light to strings.settingsThemeLight,
                        ThemeMode.Dark to strings.settingsThemeDark,
                    ).forEach { (mode, label) ->
                        val isSelected = preferences.themeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = presenter.updatePreferences { it.copy(themeMode = mode) }

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("theme-button-${mode.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        InterfaceDensityCard(contentDensity, onContentDensityChange)

        // 2. AMOLED Theme Switch
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(strings.settingsThemeAmoledTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsThemeAmoledSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.themeDarkAmoled,
                    onCheckedChange = { isChecked ->
                        val updated = presenter.updatePreferences { it.copy(themeDarkAmoled = isChecked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("amoled-switch"),
                )
            }
        }

        // 2b. Startup splash on every launch
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(strings.settingsSplashEveryLaunchTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsSplashEveryLaunchSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.splashEveryLaunch,
                    onCheckedChange = { isChecked ->
                        val updated = presenter.updatePreferences { it.copy(splashEveryLaunch = isChecked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("splash-every-launch-switch"),
                )
            }
        }

        // 3. Theme Palette Grid Selector
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(LocalSpacing.current.cardPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(strings.settingsAppThemeTitle, fontWeight = FontWeight.Bold)

                val themes = DesktopAppTheme.entries
                val isDark = when (preferences.themeMode) {
                    ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowThemes.forEach { theme ->
                                val isSelected = preferences.appTheme == theme
                                val scheme = ThemeRegistry.getColorScheme(theme)
                                val previewColors = scheme.getColorScheme(
                                    isDark = isDark,
                                    isAmoled = preferences.themeDarkAmoled,
                                )

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val updated = presenter.updatePreferences {
                                                it.copy(appTheme = theme)
                                            }

                                            onPreferencesChanged?.invoke(updated)
                                        }
                                        .testTag("theme-palette-${theme.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    },
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = strings.appThemeName(theme),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Primary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.primary),
                                            )
                                            // Secondary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.secondary),
                                            )
                                            // Tertiary bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(previewColors.tertiary),
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowThemes.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
