package mihon.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.common.LocalSnackbarHostState

@Composable
internal fun GeneralSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    onOpenOnboarding: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val preferences = presenter.preferences.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionGeneral,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.settingsLanguageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        AppLanguage.System to strings.settingsLanguageSystem,
                        AppLanguage.SimplifiedChinese to strings.settingsLanguageSimplifiedChinese,
                        AppLanguage.TraditionalChinese to strings.settingsLanguageTraditionalChinese,
                        AppLanguage.English to strings.settingsLanguageEnglish,
                    ).forEach { (lang, label) ->
                        val isSelected = preferences.language == lang
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = presenter.updatePreferences { it.copy(language = lang) }

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("language-button-${lang.name}"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Incognito Mode Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.incognitoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.incognitoDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.incognitoMode,
                    onCheckedChange = { isChecked ->
                        val updated = presenter.updatePreferences { it.copy(incognitoMode = isChecked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("incognito-switch"),
                )
            }
        }

        // Run In Background on Close Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.runInBackgroundTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.runInBackgroundSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.runInBackgroundOnClose,
                    onCheckedChange = { isChecked ->
                        val updated = presenter.updatePreferences {
                            it.copy(runInBackgroundOnClose = isChecked)
                        }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("run-in-background-switch"),
                )
            }
        }

        // Show NSFW Sources Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsShowNsfw,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsShowNsfwDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = preferences.showNsfwSources,
                    onCheckedChange = { isChecked ->
                        val updated = presenter.updatePreferences { it.copy(showNsfwSources = isChecked) }
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("settings-show-nsfw-switch"),
                )
            }
        }

        // Reset Hidden Sources Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsResetHiddenSources,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsResetHiddenSourcesDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val updated = presenter.updatePreferences { it.copy(hiddenSourceIds = emptySet()) }
                        onPreferencesChanged?.invoke(updated)
                        scope.launch {
                            snackbarHostState?.showSnackbar(strings.settingsResetHiddenSourcesSuccess)
                        }
                    },
                    modifier = Modifier.testTag("settings-reset-hidden-sources"),
                ) {
                    Text(strings.settingsResetHiddenSources)
                }
            }
        }

        // Re-show Onboarding Card
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        strings.settingsReshowOnboarding,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.settingsReshowOnboardingDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenOnboarding,
                    modifier = Modifier.testTag("settings-reshow-onboarding-btn"),
                ) {
                    Text(strings.settingsReshowOnboarding)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    strings.settingsAppInfoTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.settingsVersionLabel(mihon.desktop.updates.DesktopAppUpdateService.CURRENT_VERSION),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    strings.settingsPlatformLabel("Windows x64"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
