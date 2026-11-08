package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.ui.library.BackupRestoreState

/** Offers to install the extensions a just-restored backup needs. */
@Composable
internal fun MissingSourcesDialog(ctx: AppShellContext, backupRestoreState: BackupRestoreState) {
    if (backupRestoreState is BackupRestoreState.Idle) {
        ctx.session.pendingMissingSources?.let { MissingSourcesContent(ctx, it) }
    }
}

@Composable
private fun MissingSourcesContent(ctx: AppShellContext, missing: List<MissingSourceInfo>) {
    val session = ctx.session
    val strings = ctx.strings
    val installable = missing.mapNotNull { it.extension }.distinctBy { it.pkg }
    val missingTotal = missing.sumOf { it.mangaCount }
    val unresolved = missing.filter { it.extension == null }.sumOf { it.mangaCount }
    AlertDialog(
        onDismissRequest = { if (!session.isInstallingMissingBatch) session.pendingMissingSources = null },
        modifier = Modifier.testTag("missing-source-dialog"),
        title = { Text(strings.missingSourceDialogTitle(missingTotal, installable.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installable.isNotEmpty()) Text(installable.joinToString(", ") { it.name })
                if (unresolved > 0) Text(strings.missingSourceDialogNotFound(unresolved))
                if (session.isInstallingMissingBatch) {
                    Row {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(strings.missingSourceInstalling)
                    }
                }
            }
        },
        confirmButton = {
            if (installable.isNotEmpty()) {
                TextButton(
                    onClick = { installMissingSources(ctx, installable) },
                    enabled = !session.isInstallingMissingBatch,
                    modifier = Modifier.testTag("missing-source-dialog-install-all"),
                ) { Text(strings.missingSourceDialogInstallAll) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { session.pendingMissingSources = null },
                enabled = !session.isInstallingMissingBatch,
                modifier = Modifier.testTag("missing-source-dialog-later"),
            ) { Text(strings.missingSourceDialogLater) }
        },
    )
}

private fun installMissingSources(ctx: AppShellContext, extensions: List<ExtensionStoreItem>) {
    ctx.presenterScope.launch {
        ctx.session.isInstallingMissingBatch = true
        try {
            for (extension in extensions) {
                ctx.runtime.extensionInstaller.downloadAndInstall(
                    downloadUrl = extension.downloadUrl,
                    expectedSha256 = extension.sha256,
                    repoUrl = extension.repoUrl,
                    storeItem = extension,
                )
            }
            ctx.libraryPresenter.refreshDetails()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.warn(
                "MissingSourcesDialog",
                "Failed to install missing source extensions",
                error,
            )
            ctx.snackbarHostState.showSnackbar(error.message ?: "Failed to install extensions")
        } finally {
            ctx.session.isInstallingMissingBatch = false
            ctx.session.pendingMissingSources = null
        }
    }
}
