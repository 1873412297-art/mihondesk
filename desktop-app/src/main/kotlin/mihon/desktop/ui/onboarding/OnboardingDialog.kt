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
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
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
    onStartAction: ((StartAction) -> Unit)? = null,
    startActions: List<StartAction> = StartAction.entries,
) {
    val strings = LocalStrings.current
    var currentStep by remember { mutableStateOf(1) }
    // The skippable "where to start" step only appears when the host can run those actions.
    val totalSteps = if (onStartAction != null && startActions.isNotEmpty()) 4 else 3
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
                    text = "$currentStep/$totalSteps: " + when (currentStep) {
                        1 -> strings.onboardingStepLanguageAndTheme
                        2 -> strings.onboardingStepRepo
                        3 -> strings.onboardingStepBackup
                        else -> strings.text(UiText.StartWhereTitle)
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
                    4 -> if (onStartAction != null) {
                        StepWhereToStart(actions = startActions, onAction = onStartAction)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep < totalSteps) {
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
                    Text(
                        if (currentStep == 4) strings.text(UiText.StartSkipStep) else strings.onboardingBtnSkip,
                    )
                }
            }
        },
    )
}
