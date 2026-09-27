package mihon.desktop.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.preferences.ThemeMode
import java.io.File
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(
    preferences: DesktopPreferences,
    repositories: List<String>,
    onAddRepository: (String) -> Unit,
    onUpdatePreferences: (DesktopPreferences) -> Unit,
    onFinish: () -> Unit,
    onGoToBrowse: () -> Unit,
) {
    val strings = LocalStrings.current
    var currentStep by remember { mutableStateOf(1) }
    var repoUrlInput by remember { mutableStateOf("") }

    AlertDialog(
        modifier = Modifier.testTag("onboarding-dialog"),
        onDismissRequest = { /* Modal: require explicit skip or finish */ },
        title = {
            Column {
                Text(
                    text = strings.onboardingWelcomeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$currentStep/3: " + when (currentStep) {
                        1 -> strings.onboardingStepLanguageAndTheme
                        2 -> strings.onboardingStepRepo
                        else -> strings.onboardingStepBackup
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                when (currentStep) {
                    1 -> StepLanguageAndTheme(
                        preferences = preferences,
                        onUpdatePreferences = onUpdatePreferences,
                    )
                    2 -> StepRepositories(
                        repoUrlInput = repoUrlInput,
                        onRepoUrlChange = { repoUrlInput = it },
                        onAddRepo = {
                            if (repoUrlInput.isNotBlank()) {
                                onAddRepository(repoUrlInput.trim())
                                repoUrlInput = ""
                            }
                        },
                        repositories = repositories,
                        onGoToBrowse = {
                            onFinish()
                            onGoToBrowse()
                        },
                    )
                    3 -> StepBackupAndStorage(
                        preferences = preferences,
                        onUpdatePreferences = onUpdatePreferences,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep < 3) {
                    Button(
                        onClick = { currentStep++ },
                        modifier = Modifier.testTag("onboarding-next-btn"),
                    ) {
                        Text(strings.onboardingBtnNext)
                    }
                } else {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.testTag("onboarding-finish-btn"),
                    ) {
                        Text(strings.onboardingBtnFinish)
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.testTag("onboarding-prev-btn"),
                    ) {
                        Text(strings.onboardingBtnPrev)
                    }
                }
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding-skip-btn"),
                ) {
                    Text(strings.onboardingBtnSkip)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepLanguageAndTheme(
    preferences: DesktopPreferences,
    onUpdatePreferences: (DesktopPreferences) -> Unit,
) {
    val strings = LocalStrings.current
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("onboarding-step-1"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = strings.onboardingWelcomeDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Language Selection
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.settingsLanguageTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ExposedDropdownMenuBox(
                expanded = languageDropdownExpanded,
                onExpandedChange = { languageDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = preferences.language.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("onboarding-lang-selector"),
                )
                ExposedDropdownMenu(
                    expanded = languageDropdownExpanded,
                    onDismissRequest = { languageDropdownExpanded = false },
                ) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName) },
                            onClick = {
                                onUpdatePreferences(preferences.copy(language = lang))
                                languageDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Theme Mode Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = strings.settingsThemeModeTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val modeLabel = when (mode) {
                        ThemeMode.System -> strings.settingsThemeSystem
                        ThemeMode.Light -> strings.settingsThemeLight
                        ThemeMode.Dark -> strings.settingsThemeDark
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onUpdatePreferences(preferences.copy(themeMode = mode)) },
                    ) {
                        RadioButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onUpdatePreferences(preferences.copy(themeMode = mode)) },
                            modifier = Modifier.testTag("onboarding-theme-${mode.name.lowercase()}"),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(modeLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Pure Black Dark Theme (AMOLED)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onUpdatePreferences(preferences.copy(themeDarkAmoled = !preferences.themeDarkAmoled))
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = preferences.themeDarkAmoled,
                onCheckedChange = { onUpdatePreferences(preferences.copy(themeDarkAmoled = it)) },
                modifier = Modifier.testTag("onboarding-amoled-checkbox"),
            )
            Text(
                text = strings.settingsThemeAmoledTitle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StepRepositories(
    repoUrlInput: String,
    onRepoUrlChange: (String) -> Unit,
    onAddRepo: () -> Unit,
    repositories: List<String>,
    onGoToBrowse: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().testTag("onboarding-step-2"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = strings.onboardingStepRepoDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = repoUrlInput,
                onValueChange = onRepoUrlChange,
                placeholder = { Text(strings.onboardingRepoUrlPlaceholder) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("onboarding-repo-input"),
            )
            Button(
                onClick = onAddRepo,
                enabled = repoUrlInput.isNotBlank(),
                modifier = Modifier.testTag("onboarding-repo-add-btn"),
            ) {
                Text(strings.onboardingRepoAdd)
            }
        }

        if (repositories.isNotEmpty()) {
            Text(
                text = "${strings.browseManageRepositories} (${repositories.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            repositories.take(3).forEach { repo ->
                Text(
                    text = "• $repo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onGoToBrowse,
            modifier = Modifier.fillMaxWidth().testTag("onboarding-go-to-browse-btn"),
        ) {
            Text(strings.onboardingGoToBrowse)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepBackupAndStorage(
    preferences: DesktopPreferences,
    onUpdatePreferences: (DesktopPreferences) -> Unit,
) {
    val strings = LocalStrings.current
    var intervalDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("onboarding-step-3"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = strings.onboardingStepBackupDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Backup Storage Path
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.onboardingBackupPathLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = preferences.backupStoragePath,
                    onValueChange = { onUpdatePreferences(preferences.copy(backupStoragePath = it)) },
                    placeholder = { Text(strings.backupLocationDefault) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("onboarding-backup-path-input"),
                )
                OutlinedButton(
                    onClick = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = strings.backupLocation
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            onUpdatePreferences(preferences.copy(backupStoragePath = chooser.selectedFile.absolutePath))
                        }
                    },
                    modifier = Modifier.testTag("onboarding-backup-browse-btn"),
                ) {
                    Text(strings.onboardingBackupPathBrowse)
                }
            }
        }

        // Auto Backup Interval
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.onboardingBackupIntervalLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val intervalOptions = listOf(
                0 to strings.backupIntervalOff,
                6 to strings.backupInterval6Hours,
                12 to strings.backupInterval12Hours,
                24 to strings.backupIntervalDaily,
                168 to strings.backupIntervalWeekly,
            )
            val currentLabel = intervalOptions.find { it.first == preferences.backupIntervalHours }?.second
                ?: strings.backupIntervalOff

            ExposedDropdownMenuBox(
                expanded = intervalDropdownExpanded,
                onExpandedChange = { intervalDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("onboarding-backup-interval-selector"),
                )
                ExposedDropdownMenu(
                    expanded = intervalDropdownExpanded,
                    onDismissRequest = { intervalDropdownExpanded = false },
                ) {
                    intervalOptions.forEach { (hours, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onUpdatePreferences(preferences.copy(backupIntervalHours = hours))
                                intervalDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
