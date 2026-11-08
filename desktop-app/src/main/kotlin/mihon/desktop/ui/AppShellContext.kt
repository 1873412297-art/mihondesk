package mihon.desktop.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.ui.browse.BrowseRequests
import mihon.desktop.ui.common.LayoutPrefsModel
import mihon.desktop.ui.library.BackupRestorePresenter
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore

/**
 * Long-lived collaborators of the main shell, created once by [MihonDesktopApp] and passed to the
 * extracted shell pieces (dialogs, overlays, routes) instead of a dozen separate parameters.
 * Screen state lives in [AppSessionState] and the presenters; [layoutPrefs] is the single injected
 * owner of the shell layout preferences (content density, rail collapse).
 */
@Immutable
internal class AppShellContext(
    val runtime: DesktopRuntime,
    val session: AppSessionState,
    val navigator: DesktopNavigator,
    val libraryPresenter: LibraryPresenter,
    val backupRestore: BackupRestorePresenter,
    val appLockController: DesktopAppLockController,
    val keyBindingStore: DesktopKeyBindingStore,
    val layoutPrefs: LayoutPrefsModel,
    val snackbarHostState: SnackbarHostState,
    val presenterScope: CoroutineScope,
    val startImportBackup: () -> Unit,
    val startImportLocal: () -> Unit,
    val startPairPhoneSync: () -> Unit,
    val strings: DesktopStrings,
    val browseRequests: BrowseRequests,
)

@Composable
internal fun appShellContext(app: AppSession, runtime: DesktopRuntime): AppShellContext {
    val strings = DesktopStrings.resolve(app.state.preferences.language)
    val keyBindingStore = remember(runtime.preferences) { DesktopKeyBindingStore(runtime.preferences) }
    val layoutPrefs = remember(runtime.preferences) { LayoutPrefsModel(runtime.preferences) }
    return remember(app, runtime, strings, keyBindingStore, layoutPrefs) {
        AppShellContext(
            runtime = runtime,
            session = app.state,
            navigator = app.navigator,
            libraryPresenter = app.library,
            backupRestore = app.backup,
            appLockController = app.lock,
            keyBindingStore = keyBindingStore,
            layoutPrefs = layoutPrefs,
            snackbarHostState = app.snackbar,
            strings = strings,
            browseRequests = app.browseRequests,

            presenterScope = app.scope,
            startImportBackup = {
                mihon.desktop.ui.library.chooseAndroidBackup(strings.libraryImportBackup)?.let {
                    app.state.pendingImportPath = it
                }
            },
            startImportLocal = {
                app.scope.launch {
                    app.state.importState = ImportActionState.Running
                    app.state.importState = app.imports.chooseAndImportLocal()
                }
            },
            startPairPhoneSync = {
                app.state.settingsSectionRequest = mihon.desktop.ui.settings.SettingsSection.Backup
                app.navigator.navigate(mihon.desktop.navigation.DesktopDestination.Settings)
            },
        )
    }
}
