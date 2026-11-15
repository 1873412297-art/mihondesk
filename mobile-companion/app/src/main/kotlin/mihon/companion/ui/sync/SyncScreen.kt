package mihon.companion.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import mihon.companion.R
import mihon.companion.ui.components.PageHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BACKUP_MIME = "application/octet-stream"

@Composable
fun SyncScreen(state: SyncUiState, onAction: (SyncAction) -> Unit) {
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        onAction(SyncAction.ConsentResult(it.resultCode, it.data))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAction(SyncAction.Import(uri))
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) onAction(SyncAction.Export(uri))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onAction(SyncAction.PickMihonFolder(uri))
    }
    LaunchedEffect(state.consent) {
        val intent = state.consent ?: return@LaunchedEffect
        onAction(SyncAction.ConsentLaunched)
        consentLauncher.launch(IntentSenderRequest.Builder(intent).build())
    }
    MessageSnackbar(state.message, onShown = { onAction(SyncAction.MessageShown) }) { host ->
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader(stringResource(R.string.sync_title)) }
            item { AccountSection(state, onAction) }
            item {
                MihonFolderSection(
                    status = state.mihon,
                    enabled = !state.busy,
                    onPick = { folderLauncher.launch(null) },
                    onClear = { onAction(SyncAction.ClearMihonFolder) },
                )
            }
            item {
                MihonSection(
                    enabled = !state.busy,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { exportLauncher.launch(backupFileName()) },
                )
            }
            item { AdvancedSection(state.deviceId, onRepull = { onAction(SyncAction.Repull) }) }
        }
        SnackbarHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MessageSnackbar(
    message: UiMessage?,
    onShown: () -> Unit,
    content: @Composable BoxScope.(SnackbarHostState) -> Unit,
) {
    val host = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        onShown()
        host.showSnackbar(context.getString(message.text, *message.args.toTypedArray()))
    }
    Box(Modifier.fillMaxSize()) { content(host) }
}

private fun backupFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
    return "mihondesk_$date.tachibk"
}
