package mihon.desktop.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.updates.AppUpdatePhase
import mihon.desktop.updates.AppUpdateState
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateCard(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val strings = LocalStrings.current
    Card(Modifier.fillMaxWidth().animateContentSize().testTag("app-update-card")) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.text(UiText.AppUpdateTitle), style = MaterialTheme.typography.titleMedium)
            Text(
                strings.aboutVersion(
                    state.release?.currentVersion ?: mihon.desktop.updates.DesktopAppUpdateService.CURRENT_VERSION,
                ),
            )
            val status = when (state.phase) {
                AppUpdatePhase.Idle, AppUpdatePhase.Available -> null
                AppUpdatePhase.Checking -> UiText.AppUpdateChecking
                AppUpdatePhase.Current -> UiText.AppUpdateCurrent
                AppUpdatePhase.Downloading -> UiText.AppUpdatePreparing
                AppUpdatePhase.Publishing -> UiText.AppUpdatePublishing
                AppUpdatePhase.Cancelling -> UiText.AppUpdateCancelling
                AppUpdatePhase.Cancelled -> UiText.AppUpdateCancelled
                AppUpdatePhase.Failed -> UiText.AppUpdateFailed
                AppUpdatePhase.Ready -> null
            }
            status?.let { Text(strings.text(it), Modifier.testTag("app-update-status")) }
            state.release?.let { release ->
                Text(strings.text(UiText.AppUpdateAvailable, release.release.version))
                if (release.release.releaseNotes.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            release.release.releaseNotes,
                            Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
                if (release.matchedAsset == null) {
                    Text(strings.text(UiText.AppUpdateUnsupported))
                } else {
                    Text(release.matchedAsset.name, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.busy) {
                if (state.phase == AppUpdatePhase.Downloading && state.total > 0) {
                    LinearProgressIndicator(progress = {
                        (state.received.toFloat() / state.total).coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth())
                    Text(strings.text(UiText.AppUpdateProgress, mib(state.received), mib(state.total)))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            state.savedFile?.let { file ->
                SelectionContainer {
                    Text(strings.text(UiText.AppUpdateReady, file), Modifier.testTag("app-update-saved"))
                }
                Text(strings.text(UiText.AppUpdateManual))
            }
            if (state.openFailed) {
                Text(
                    strings.text(UiText.AppUpdateOpenFailed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.busy) {
                    TextButton(
                        onCancel,
                        enabled =
                        state.phase in setOf(AppUpdatePhase.Checking, AppUpdatePhase.Downloading),
                        modifier = Modifier.testTag("app-update-cancel"),
                    ) {
                        Text(strings.text(UiText.AppUpdateCancel))
                    }
                } else {
                    if (state.release?.matchedAsset != null && state.savedFile == null) {
                        Button(onDownload, Modifier.testTag("app-update-download")) {
                            Text(strings.text(UiText.AppUpdateDownload))
                        }
                    }
                    if (state.savedFile != null) {
                        Button(onOpenFolder, Modifier.testTag("app-update-folder")) {
                            Text(strings.text(UiText.AppUpdateFolder))
                        }
                    }
                    TextButton(onCheck, Modifier.testTag("app-update-check")) {
                        Text(strings.text(UiText.AppUpdateCheck))
                    }
                }
                TextButton(onOpenRelease) { Text(strings.text(UiText.AppUpdateRelease)) }
            }
        }
    }
}

private fun mib(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / 1048576.0)

fun chooseAppUpdateDestination(name: String, title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = name
        val downloads = Path.of(System.getProperty("user.home"), "Downloads")
        if (java.nio.file.Files.isDirectory(downloads)) directory = downloads.toString()
    }
    return try {
        dialog.isVisible = true
        val directory = dialog.directory ?: return null
        val selected = dialog.file ?: return null
        val extension = "." + name.substringAfterLast('.')
        Path.of(directory, if (selected.endsWith(extension, ignoreCase = true)) selected else selected + extension)
    } finally {
        dialog.dispose()
    }
}
