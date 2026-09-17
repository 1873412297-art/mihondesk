package mihon.desktop.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.updates.AppUpdatePresenter
import mihon.desktop.updates.DesktopAppUpdateService
import java.awt.Desktop
import java.net.URI

@Composable
fun AppUpdatePanel(presenter: AppUpdatePresenter, service: DesktopAppUpdateService) {
    val state by presenter.state.collectAsState()
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    fun open(action: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (
                error: CancellationException,
            ) {
                throw error
            } catch (_: Exception) {
                presenter.reportOpenFailure()
            }
        }
    }
    AppUpdateCard(
        state = state,
        onCheck = presenter::check,
        onCancel = presenter::cancel,
        onDownload = {
            try {
                state.release?.matchedAsset?.let { asset ->
                    chooseAppUpdateDestination(asset.name, strings.text(UiText.AppUpdateSave))?.let(presenter::download)
                }
            } catch (_: Exception) {
                presenter.reportOpenFailure()
            }
        },
        onOpenFolder = {
            state.savedFile?.toAbsolutePath()?.parent?.let { folder ->
                open { Desktop.getDesktop().open(folder.toFile()) }
            }
        },
        onOpenRelease = {
            val tag = state.release?.release?.tagName
            val url = "https://github.com/${service.repository}/releases" + (tag?.let { "/tag/$it" } ?: "/latest")
            open { Desktop.getDesktop().browse(URI(url)) }
        },
    )
}
