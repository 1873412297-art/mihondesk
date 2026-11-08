package mihon.desktop.ui.browse

import androidx.compose.runtime.Immutable
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourceState
import mihon.desktop.library.model.LibraryManga
import mihon.extension.model.SourceDescriptor
import java.io.File

enum class BrowseTab {
    Sources,
    Extensions,
    Migration,
}

enum class ExtensionInstallPhase { Downloading, Installing, Cancelling }

@Immutable
data class BrowseUiState(
    val selectedTab: BrowseTab = BrowseTab.Sources,
    val installedExtensions: List<InstalledExtension> = emptyList(),
    val availableExtensions: List<ExtensionStoreItem> = emptyList(),
    val repositories: List<String> = emptyList(),
    val sources: List<SourceDescriptor> = emptyList(),
    val sourceStates: List<SourceState> = emptyList(),
    val extensionSourceStates: Map<String, List<SourceState>> = emptyMap(),
    val incognitoExtensionPackages: Set<String> = emptySet(),
    val sourcePreferenceDefinitions: Map<Long, List<SourcePreferenceDefinition>> = emptyMap(),
    val sourcePreferenceValues: Map<Long, Map<String, String>> = emptyMap(),
    val pinnedSourceIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isInstalling: Boolean = false,
    val installingPkg: String? = null,
    val installingName: String = "",
    val installPhase: ExtensionInstallPhase? = null,
    val installationCancelled: Boolean = false,
    val errorMessage: String? = null,
    /** Set when the last install/update attempt failed, so the UI can explain which step failed. */
    val installFailure: ExtensionInstallFailure? = null,
    val searchQuery: String = "",
    // Migration state
    val sourcesWithMangaCounts: List<SourceWithMangaCount> = emptyList(),
    val selectedMigrationSource: SourceWithMangaCount? = null,
    val mangasForSelectedMigrationSource: List<LibraryManga> = emptyList(),
    val batchMigrationState: mihon.desktop.ui.browse.migration.BatchMigrationState =
        mihon.desktop.ui.browse.migration.BatchMigrationState.Idle,
    // Global search
    val isGlobalSearchOpen: Boolean = false,
    val globalSearchQuery: String = "",
    val isGlobalSearching: Boolean = false,
    val globalSearchResults: List<GlobalSearchSourceResult> = emptyList(),
    val globalSearchOnlyPinned: Boolean = false,
    // Phase 2d: Filtering & Preferences
    val hiddenSourceIds: Set<Long> = emptySet(),
    val showNsfw: Boolean = false,
    val sourceLanguageFilter: String? = null,
)

fun isSourceNsfw(
    sourceId: Long,
    installedExtensions: List<InstalledExtension>,
    availableExtensions: List<ExtensionStoreItem> = emptyList(),
): Boolean {
    if (installedExtensions.any { ext -> ext.manifest.isNsfw && ext.manifest.sources.any { it.id == sourceId } }) {
        return true
    }
    if (availableExtensions.any { item -> item.isNsfw && item.sources.any { it.id == sourceId } }) {
        return true
    }
    return false
}

fun chooseMextFile(title: String = "Select Extension Package (.mext, .apk, .jar)"): File? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        title,
        java.awt.FileDialog.LOAD,
    )
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".mext", ignoreCase = true) ||
            name.endsWith(".apk", ignoreCase = true) ||
            name.endsWith(".jar", ignoreCase = true)
    }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, file)
}
