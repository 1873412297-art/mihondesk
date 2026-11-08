package mihon.desktop.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.settings.SettingsSection
import mihon.desktop.ui.splash.StartupHandoff
import mihon.extension.model.SourceDescriptor
import java.nio.file.Path

/**
 * Window-session state of the main app shell: the current preferences snapshot, first-run flow
 * and which dialogs are open. One instance is remembered by [MihonDesktopApp] and handed to the
 * extracted shell pieces, so no shell composable keeps its own business-level `mutableStateOf`.
 */
@Stable
internal class AppSessionState(
    initialPreferences: DesktopPreferences,
    showFirstRunSplash: Boolean,
    startupHandoff: StartupHandoff?,
) {
    var preferences by mutableStateOf(initialPreferences)

    // First run
    var showOnboarding by mutableStateOf(!initialPreferences.onboardingCompleted)
    var showFirstRunSplash by mutableStateOf(showFirstRunSplash)
    var pendingStartupHandoff by mutableStateOf(startupHandoff)

    // Backup / import
    var pendingExportPath by mutableStateOf<Path?>(null)
    var pendingImportPath by mutableStateOf<Path?>(null)
    var importState: ImportActionState by mutableStateOf(ImportActionState.Idle)
    var pendingMissingSources by mutableStateOf<List<MissingSourceInfo>?>(null)
    var isInstallingMissingBatch by mutableStateOf(false)

    // Manga organisation
    var isTrackingDialogOpen by mutableStateOf(false)
    var isManageCategoriesDialogOpen by mutableStateOf(false)
    var isEditMangaCategoriesDialogOpen by mutableStateOf(false)
    var pendingMangaOrganizationAction by mutableStateOf<MangaOrganizationAction?>(null)
    var isMangaLibraryActionRunning by mutableStateOf(false)
    var isMangaSourceRefreshing by mutableStateOf(false)

    // Shell overlays and cross-screen requests
    var showShortcutsHelp by mutableStateOf(false)
    var showCommandPalette by mutableStateOf(false)
    var settingsSectionRequest by mutableStateOf<SettingsSection?>(null)

    /** Chapter "Open in WebView": resolved source descriptor and absolute URL. */
    var chapterWebViewRequest by mutableStateOf<Pair<SourceDescriptor, String>?>(null)
}

/** Adding a manga to the library first is required before these organisation actions. */
internal enum class MangaOrganizationAction {
    Categories,
    Tracking,
}
