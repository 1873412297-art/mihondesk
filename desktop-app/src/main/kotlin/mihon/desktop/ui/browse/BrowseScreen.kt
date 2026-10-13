package mihon.desktop.ui.browse

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourceState
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.extension.extensionStoreLabel
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.i18n.trustStatusLabel
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.onSecondaryClick
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
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

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onTabSelected: (BrowseTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit = { _, _ -> },
    onTogglePinSource: (Long) -> Unit = {},
    onInstallExtension: (ExtensionStoreItem) -> Unit = {},
    onInstallFromFile: (File) -> Unit = {},
    onCancelInstallation: () -> Unit = {},
    onUninstallExtension: (String) -> Unit = {},
    onToggleExtensionEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onExtensionSelected: (InstalledExtension) -> Unit = {},
    onAddRepository: (String) -> Unit = {},
    onRemoveRepository: (String) -> Unit = {},
    onUpdateAllPending: () -> Unit = {},
    onRefresh: () -> Unit = {},
    // Global search handlers
    onOpenGlobalSearch: () -> Unit = {},
    onCloseGlobalSearch: () -> Unit = {},
    onGlobalSearchQueryChange: (String) -> Unit = {},
    onPerformGlobalSearch: () -> Unit = {},
    onToggleGlobalSearchOnlyPinned: (Boolean) -> Unit = {},
    onGlobalMangaSelected: (SourceDescriptor, SManga) -> Unit = { _, _ -> },
    // Phase 2d: Source hide & language filter handlers
    onHideSource: (Long) -> Unit = {},
    onUnhideSource: (Long) -> Unit = {},
    onSourceLanguageFilterChange: (String?) -> Unit = {},
    // Migration handlers
    onSelectMigrationSource: (SourceWithMangaCount?) -> Unit = {},
    onSearchTargetMigrationSource: suspend (sourceId: Long, query: String) -> List<SManga> = { _, _ -> emptyList() },
    onPerformMigration: (
        oldManga: LibraryManga,
        targetSource: SourceDescriptor,
        targetManga: SManga,
    ) -> Unit = { _, _, _ -> },
    onAutoMatchTargetSource: (
        suspend (
            sourceId: Long,
            manga: LibraryManga,
        ) -> mihon.desktop.ui.browse.migration.MigrationMatcher.MatchEvaluation
    )? = null,
    onStartBatchMigration: ((targetSource: SourceDescriptor) -> Unit)? = null,
    onCancelBatchMigration: (() -> Unit)? = null,
    onDismissBatchReport: (() -> Unit)? = null,
    // Extension trust state/actions
    extensionTrustStatuses: Map<String, ExtensionTrustStatus>? = null,
    trustStatuses: Map<String, ExtensionTrustStatus>? = extensionTrustStatuses,
    extensionTrustStates: Map<String, ExtensionTrustStatus>? = null,
    trustStates: Map<String, ExtensionTrustStatus>? = extensionTrustStates,
    onTrustExtension: ((InstalledExtension) -> Unit)? = null,
    onTrust: ((InstalledExtension) -> Unit)? = onTrustExtension,
    onRevokeExtension: ((InstalledExtension) -> Unit)? = null,
    onRevoke: ((InstalledExtension) -> Unit)? = onRevokeExtension,
    onTrustPackage: ((String) -> Unit)? = null,
    onRevokePackage: ((String) -> Unit)? = null,
) {
    if (state.isGlobalSearchOpen) {
        DesktopBackHandler {
            onCloseGlobalSearch()
            true
        }
        GlobalSearchScreen(
            query = state.globalSearchQuery,
            onQueryChange = onGlobalSearchQueryChange,
            onSearch = onPerformGlobalSearch,
            onBack = onCloseGlobalSearch,
            isSearching = state.isGlobalSearching,
            sourceResults = state.globalSearchResults,
            hasSources = state.sources.isNotEmpty(),
            onlyPinned = state.globalSearchOnlyPinned,
            onToggleOnlyPinned = onToggleGlobalSearchOnlyPinned,
            onMangaSelected = onGlobalMangaSelected,
            onViewSource = { source ->
                onCloseGlobalSearch()
                onSourceSelected(source, SourceListingMode.Popular)
            },
        )
        return
    }

    var showRepoDialog by remember { mutableStateOf(false) }
    var pendingInstallItem: ExtensionStoreItem? by remember { mutableStateOf(null) }
    val strings = LocalStrings.current

    // Compute update count
    val installedPkgMap = state.installedExtensions.associateBy { it.pkg }
    val pendingUpdates = state.availableExtensions.filter { available ->
        val installed = installedPkgMap[available.pkg]
        installed != null && available.versionCode > installed.manifest.versionCode
    }

    // Trust state comes from the caller when provided, otherwise from the active trust store.
    val activeTrustStore = remember { ExtensionTrustStore.active() }
    val trustRevision = activeTrustStore?.revision?.collectAsState()?.value ?: 0L
    val pendingTrustRequest = activeTrustStore?.pendingTrustRequest?.collectAsState()?.value
    val computedTrustStatuses = remember(trustRevision, state.installedExtensions) {
        state.installedExtensions.associate { installed ->
            val fingerprints = installed.signatureFingerprints.ifEmpty {
                listOfNotNull(installed.signatureFingerprint.takeIf { it.isNotBlank() })
            }
            val storeStatus = activeTrustStore?.status(
                pkg = installed.pkg,
                fingerprints = fingerprints,
                trustedSigningKeys = listOfNotNull(installed.signingKey.takeIf { it.isNotBlank() }),
                signatureValid = installed.trustStatus != ExtensionTrustStatus.INVALID,
            )
            val storeHasOpinion = activeTrustStore != null && (
                activeTrustStore.getTrustedFingerprints(installed.pkg).isNotEmpty() ||
                    activeTrustStore.isRevoked(installed.pkg) ||
                    ExtensionTrustStore.normalizeFingerprint(installed.signingKey).isNotBlank()
                )
            installed.pkg to if (storeHasOpinion) (storeStatus ?: installed.trustStatus) else installed.trustStatus
        }
    }
    val providedTrustStatuses = extensionTrustStatuses
        ?: trustStatuses
        ?: extensionTrustStates
        ?: trustStates
    val effectiveTrustStatuses = if (providedTrustStatuses == null) {
        computedTrustStatuses
    } else {
        computedTrustStatuses + providedTrustStatuses
    }
    val handleTrust: (InstalledExtension) -> Unit = onTrust
        ?: onTrustExtension
        ?: onTrustPackage?.let { callback -> { installed -> callback(installed.pkg) } }
        ?: { installed ->
            val store = activeTrustStore ?: ExtensionTrustStore.active()
            val fingerprints = installed.signatureFingerprints.ifEmpty {
                listOfNotNull(
                    installed.signatureFingerprint.takeIf { it.isNotBlank() },
                    installed.signingKey.takeIf { it.isNotBlank() },
                )
            }
            if (fingerprints.isEmpty()) {
                store?.trustUnsigned(installed.pkg)
            } else {
                store?.trust(installed.pkg, fingerprints)
            }
        }
    val handleRevoke: (InstalledExtension) -> Unit = onRevoke
        ?: onRevokeExtension
        ?: onRevokePackage?.let { callback -> { installed -> callback(installed.pkg) } }
        ?: { installed -> (activeTrustStore ?: ExtensionTrustStore.active())?.revoke(installed.pkg) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("browse-screen")) {
        // Top Header
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings.browseTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenGlobalSearch,
                    modifier = Modifier.testTag("open-global-search-button"),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseGlobalSearch)
                }
                OutlinedButton(
                    onClick = {
                        val file = chooseMextFile(strings.text(UiText.ChooseExtension))
                        if (file != null) {
                            onInstallFromFile(file)
                        }
                    },
                    modifier = Modifier.testTag("install-file-button"),
                    enabled = !state.isInstalling,
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseInstallFromFile)
                }
                OutlinedButton(
                    onClick = { showRepoDialog = true },
                    modifier = Modifier.testTag("manage-repos-button"),
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseManageRepositories)
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh-browse-button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.browseRefresh)
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = state.selectedTab == BrowseTab.Sources,
                onClick = { onTabSelected(BrowseTab.Sources) },
                text = { Text("${strings.browseTabSources} (${state.sources.size})") },
                modifier = Modifier.testTag("browse-tab-sources"),
            )
            Tab(
                selected = state.selectedTab == BrowseTab.Extensions,
                onClick = { onTabSelected(BrowseTab.Extensions) },
                text = {
                    if (pendingUpdates.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(pendingUpdates.size.toString()) } }) {
                            Text("${strings.browseTabExtensions} (${state.availableExtensions.size})")
                        }
                    } else {
                        Text("${strings.browseTabExtensions} (${state.availableExtensions.size})")
                    }
                },
                modifier = Modifier.testTag("browse-tab-extensions"),
            )
            Tab(
                selected = state.selectedTab == BrowseTab.Migration,
                onClick = { onTabSelected(BrowseTab.Migration) },
                text = { Text(strings.browseTabMigration) },
                modifier = Modifier.testTag("browse-tab-migration"),
            )
        }

        // Filter / Search Bar (for Sources & Extensions tabs)
        if (state.selectedTab != BrowseTab.Migration) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("browse-search-bar"),
                placeholder = { Text(strings.browseSearchPlaceholder) },
                singleLine = true,
            )
        }

        ExtensionInstallStatus(state, onCancelInstallation)

        // Error Banner
        state.errorMessage?.let { error ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                // Install failures get their own explanation next to the install status; everything
                // else is classified as a download/source failure.
                if (state.installFailure == null) {
                    mihon.desktop.ui.common.FailureExplanation(mihon.desktop.download.classifyDownloadFailure(error))
                }
                mihon.desktop.ui.common.ErrorDetails(error, "browse-error")
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    modifier = Modifier.testTag("browse-error-retry"),
                ) {
                    Text(strings.libraryRetry)
                }
            }
        }

        // Content
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("browse-loading-indicator"))
            }
        } else {
            when (state.selectedTab) {
                BrowseTab.Sources -> SourcesListView(
                    sources = state.sources,
                    pinnedIds = state.pinnedSourceIds,
                    hiddenIds = state.hiddenSourceIds,
                    searchQuery = state.searchQuery,
                    selectedLanguage = state.sourceLanguageFilter,
                    installedExtensions = state.installedExtensions,
                    availableExtensions = state.availableExtensions,
                    showNsfw = state.showNsfw,
                    onSourceSelected = onSourceSelected,
                    onTogglePin = onTogglePinSource,
                    onHideSource = onHideSource,
                    onLanguageSelected = onSourceLanguageFilterChange,
                    onExtensionSelected = onExtensionSelected,
                )
                BrowseTab.Extensions -> ExtensionsListView(
                    state = state,
                    pendingUpdates = pendingUpdates,
                    trustStatuses = effectiveTrustStatuses,
                    onRequestInstall = { item -> pendingInstallItem = item },
                    onUninstallExtension = onUninstallExtension,
                    onToggleEnabled = onToggleExtensionEnabled,
                    onExtensionSelected = onExtensionSelected,
                    onUpdateAllPending = onUpdateAllPending,
                    onTrustExtension = handleTrust,
                    onRevokeExtension = handleRevoke,
                )
                BrowseTab.Migration -> MigrateSourceScreen(
                    sourcesWithCounts = state.sourcesWithMangaCounts,
                    selectedSource = state.selectedMigrationSource,
                    mangasForSelectedSource = state.mangasForSelectedMigrationSource,
                    availableTargetSources = state.sources.filterNot { it.id == BundledLocalSource.ID },
                    onSelectSource = { onSelectMigrationSource(it) },
                    onBackToSourceList = { onSelectMigrationSource(null) },
                    onSearchTargetSource = onSearchTargetMigrationSource,
                    onAutoMatchTargetSource = onAutoMatchTargetSource,
                    onPerformMigration = onPerformMigration,
                    batchMigrationState = state.batchMigrationState,
                    onStartBatchMigration = onStartBatchMigration,
                    onCancelBatchMigration = onCancelBatchMigration,
                    onDismissBatchReport = onDismissBatchReport,
                )
            }
        }
    }

    // Security Confirmation Dialog for Extension Installation
    pendingInstallItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingInstallItem = null },
            title = { Text(strings.browseInstallExtensionTitle(item.name)) },
            text = {
                Column {
                    Text(strings.browsePackageLabel(item.pkg))
                    Text(strings.browseVersionLabel(item.version))
                    Text(strings.browseLanguageLabel(item.lang.uppercase()))
                    extensionStoreLabel(item)?.let { store ->
                        Text(strings.extensionStoreLabel(store))
                    }
                    if (item.declaredDomains.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.browseNetworkDomainsHeader,
                            fontWeight = FontWeight.SemiBold,
                        )
                        item.declaredDomains.forEach { domain ->
                            Text(" • $domain", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val usableSigningKey = ExtensionTrustStore.normalizeFingerprint(item.signingKey)
                    if (usableSigningKey.isNotBlank()) {
                        Text(
                            text = strings.text(UiText.RepositorySigningKey, usableSigningKey),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = strings.text(UiText.NoRepositoryKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("unsigned-extension-warning"),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.browseNetworkPermissionNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // The confirmation dialog is the explicit user trust action required for
                        // packages whose repository does not publish a signing key.
                        val toInstall = item.copy(trustOnInstall = true)
                        pendingInstallItem = null
                        onInstallExtension(toInstall)
                    },
                    modifier = Modifier.testTag("confirm-install-button"),
                ) {
                    Text(strings.browseTrustAndInstall)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstallItem = null }) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }

    // Trust-required retry dialog. The installer records a pending request when it blocks an
    // install on an unknown signer; confirming here persists explicit trust and retries the
    // original install without requiring presenter changes.
    val trustError = state.errorMessage
    if (pendingTrustRequest != null &&
        trustError != null &&
        trustError.contains("Failed to install", ignoreCase = true) &&
        (trustError.contains("trust", ignoreCase = true) || trustError.contains("signature", ignoreCase = true))
    ) {
        val request = pendingTrustRequest
        AlertDialog(
            onDismissRequest = { activeTrustStore?.clearPendingTrustRequest() },
            title = { Text(strings.text(UiText.TrustExtensionSignature)) },
            text = {
                Column {
                    Text(strings.browsePackageLabel(request.pkg))
                    Text(
                        text = if (request.fingerprints.isEmpty()) {
                            strings.text(UiText.UnsignedExtensionHint)
                        } else {
                            strings.text(UiText.SignerFingerprint, request.fingerprints.joinToString())
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (request.reason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(request.reason, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeTrustStore?.clearPendingTrustRequest()
                        if (request.fingerprints.isEmpty()) {
                            activeTrustStore?.trustUnsigned(request.pkg)
                        } else {
                            activeTrustStore?.trust(request.pkg, request.fingerprints)
                        }
                        val storeItem = request.storeItem
                        if (storeItem != null) {
                            onInstallExtension(storeItem.copy(trustOnInstall = true))
                        } else {
                            request.filePath?.let { path -> onInstallFromFile(File(path)) }
                        }
                    },
                    modifier = Modifier.testTag("confirm-trust-install-button"),
                ) {
                    Text(strings.browseTrustAndInstall)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeTrustStore?.clearPendingTrustRequest() },
                    modifier = Modifier.testTag("cancel-trust-button"),
                ) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }

    // Repository Management Dialog
    if (showRepoDialog) {
        var newRepoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text(strings.browseManageRepositories) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newRepoUrl,
                            onValueChange = { newRepoUrl = it },
                            placeholder = { Text("https://example.com/repo") },
                            modifier = Modifier.weight(1f).testTag("new-repo-input"),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newRepoUrl.isNotBlank()) {
                                    onAddRepository(newRepoUrl.trim())
                                    newRepoUrl = ""
                                }
                            },
                            modifier = Modifier.testTag("add-repo-button"),
                        ) {
                            Text(strings.browseAddRepository)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.browseConfiguredRepositories, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.repositories) { repo ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = repo,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = { onRemoveRepository(repo) },
                                    modifier = Modifier.testTag("remove-repo-$repo"),
                                ) {
                                    Text(strings.browseRemoveRepository, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRepoDialog = false }) {
                    Text(strings.dialogClose)
                }
            },
        )
    }
}

@Composable
private fun SourcesListView(
    sources: List<SourceDescriptor>,
    pinnedIds: Set<Long>,
    hiddenIds: Set<Long> = emptySet(),
    searchQuery: String,
    selectedLanguage: String? = null,
    installedExtensions: List<InstalledExtension> = emptyList(),
    availableExtensions: List<ExtensionStoreItem> = emptyList(),
    showNsfw: Boolean = false,
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHideSource: (Long) -> Unit = {},
    onLanguageSelected: (String?) -> Unit = {},
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val spacing = LocalSpacing.current

    val availableLanguages = remember(sources, hiddenIds) {
        sources.filterNot { hiddenIds.contains(it.id) }
            .map { it.lang.lowercase() }
            .distinct()
            .sorted()
    }
    val iconUris = remember(installedExtensions, availableExtensions) {
        sourceIconUris(installedExtensions, availableExtensions)
    }

    val filteredSources =
        remember(
            sources,
            searchQuery,
            hiddenIds,
            selectedLanguage,
            showNsfw,
            installedExtensions,
            availableExtensions,
        ) {
            sources.filter { source ->
                if (hiddenIds.contains(source.id)) return@filter false
                if (!showNsfw && isSourceNsfw(source.id, installedExtensions, availableExtensions)) return@filter false
                if (selectedLanguage != null &&
                    !source.lang.equals(selectedLanguage, ignoreCase = true)
                ) {
                    return@filter false
                }
                if (searchQuery.isNotBlank() &&
                    !source.name.contains(searchQuery, ignoreCase = true) &&
                    !source.lang.contains(searchQuery, ignoreCase = true)
                ) {
                    return@filter false
                }
                true
            }
        }

    val pinnedSources = remember(filteredSources, pinnedIds) {
        filteredSources.filter { pinnedIds.contains(it.id) }
    }
    val otherSources = remember(filteredSources, pinnedIds) {
        filteredSources.filterNot { pinnedIds.contains(it.id) }
    }
    val grouped = remember(otherSources) {
        otherSources.groupBy { it.lang.uppercase() }
    }
    var collapsedLangs by remember { mutableStateOf(emptySet<String>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (availableLanguages.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("source-lang-filter"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = selectedLanguage == null,
                        onClick = { onLanguageSelected(null) },
                        label = { Text(strings.browseFilterLanguageAll) },
                        modifier = Modifier.testTag("source-lang-chip-all"),
                    )
                }
                items(availableLanguages, key = { it }) { lang ->
                    FilterChip(
                        selected = selectedLanguage.equals(lang, ignoreCase = true),
                        onClick = {
                            if (selectedLanguage.equals(lang, ignoreCase = true)) {
                                onLanguageSelected(null)
                            } else {
                                onLanguageSelected(lang)
                            }
                        },
                        label = { Text(strings.browseSourceLanguage(lang)) },
                        modifier = Modifier.testTag("source-lang-chip-$lang"),
                    )
                }
            }
        }

        if (filteredSources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = strings.browseNoMangaFound,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("sources-list"),
                verticalArrangement = Arrangement.spacedBy(spacing.listRowGap / 2),
            ) {
                if (pinnedSources.isNotEmpty()) {
                    item(key = "hdr_pinned") {
                        BrowseSectionHeader(
                            title = strings.browsePinnedSection,
                            count = pinnedSources.size,
                            accent = true,
                        )
                    }
                    items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                        SourceListItem(
                            source = source,
                            isPinned = true,
                            iconUri = iconUris[source.id],
                            isNsfw = isSourceNsfw(source.id, installedExtensions, availableExtensions),
                            installedExtensions = installedExtensions,
                            onSourceSelected = onSourceSelected,
                            onTogglePin = onTogglePin,
                            onHide = { onHideSource(source.id) },
                            onExtensionSelected = onExtensionSelected,
                        )
                    }
                }

                grouped.forEach { (lang, langSources) ->
                    val collapsed = lang in collapsedLangs
                    item(key = "hdr_$lang") {
                        BrowseSectionHeader(
                            title = strings.browseSourceLanguage(lang),
                            count = langSources.size,
                            collapsed = collapsed,
                            onToggle = {
                                collapsedLangs = if (collapsed) collapsedLangs - lang else collapsedLangs + lang
                            },
                        )
                    }
                    if (!collapsed) {
                        items(langSources, key = { it.id }) { source ->
                            SourceListItem(
                                source = source,
                                isPinned = false,
                                iconUri = iconUris[source.id],
                                isNsfw = isSourceNsfw(source.id, installedExtensions, availableExtensions),
                                installedExtensions = installedExtensions,
                                onSourceSelected = onSourceSelected,
                                onTogglePin = onTogglePin,
                                onHide = { onHideSource(source.id) },
                                onExtensionSelected = onExtensionSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseSectionHeader(
    title: String,
    count: Int,
    accent: Boolean = false,
    collapsed: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sectionGap / 2)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collapsed != null) {
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun BrowseItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    onSecondaryClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    val hovered by interactionSource.collectIsHoveredAsState()
    val container = if (hovered) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .hoverable(interactionSource)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(if (onSecondaryClick != null) Modifier.onSecondaryClick { onSecondaryClick() } else Modifier)
                .padding(horizontal = spacing.listRowPadding + 2.dp, vertical = spacing.listRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.listRowPadding + 4.dp),
            content = content,
        )
    }
}

@Composable
private fun SourceListItem(
    source: SourceDescriptor,
    isPinned: Boolean,
    iconUri: String? = null,
    isNsfw: Boolean = false,
    installedExtensions: List<InstalledExtension> = emptyList(),
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHide: () -> Unit = {},
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val associatedExtension = remember(source.id, installedExtensions) {
        installedExtensions.find { ext -> ext.manifest.sources.any { it.id == source.id } }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val currentOnSourceSelected by rememberUpdatedState(onSourceSelected)
    val onPopularClick = remember(source.id) {
        { currentOnSourceSelected(source, SourceListingMode.Popular) }
    }
    val onLatestClick = remember(source.id) {
        { currentOnSourceSelected(source, SourceListingMode.Latest) }
    }
    val currentOnTogglePin by rememberUpdatedState(onTogglePin)
    val onPinClick = remember(source.id) {
        { currentOnTogglePin(source.id) }
    }
    val currentOnExtensionSelected by rememberUpdatedState(onExtensionSelected)
    val onExtClick = remember(associatedExtension) {
        {
            val ext = associatedExtension
            if (ext != null) currentOnExtensionSelected(ext)
        }
    }
    val pinLabel = (if (isPinned) strings.browseUnpin else strings.browsePin).replace("☆", "").replace("★", "").trim()

    BrowseItemCard(
        modifier = Modifier.testTag("source-item-${source.id}"),
        onClick = onPopularClick,
        onSecondaryClick = { showContextMenu = true },
        interactionSource = interactionSource,
    ) {
        DesktopTooltipBox(text = strings.browseSourceId(source.id.toString())) {
            ExtensionIcon(name = source.name, iconUri = iconUri, size = 48.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (source.id == BundledLocalSource.ID) {
                    SmallBadge(strings.browseLocalBadge, BadgeTone.Primary)
                }
                if (isNsfw) SmallBadge("18+", BadgeTone.Danger)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge(source.lang.uppercase())
                val extName = associatedExtension?.manifest?.name
                val secondary = listOfNotNull(
                    extName?.takeIf { !it.equals(source.name, ignoreCase = true) },
                    associatedExtension?.manifest?.version?.let { "v$it" },
                ).joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (associatedExtension != null && hovered) {
                DesktopTooltipBox(text = strings.extensionInfo) {
                    IconButton(onClick = onExtClick, modifier = Modifier.testTag("source-ext-btn-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.extensionInfo,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (source.supportsLatest) {
                DesktopTooltipBox(text = strings.browseSourceLatest) {
                    IconButton(onClick = onLatestClick, modifier = Modifier.testTag("latest-btn-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = strings.browseSourceLatest,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            DesktopTooltipBox(text = pinLabel) {
                IconButton(onClick = onPinClick, modifier = Modifier.testTag("pin-btn-${source.id}")) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = pinLabel,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box {
                DesktopTooltipBox(text = strings.browseMoreActions) {
                    IconButton(onClick = {
                        showContextMenu = true
                    }, modifier = Modifier.testTag("source-overflow-${source.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = strings.browseMoreActions,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.browseSourceBrowse) },
                        onClick = {
                            showContextMenu = false
                            onPopularClick()
                        },
                    )
                    if (source.supportsLatest) {
                        DropdownMenuItem(
                            text = { Text(strings.browseSourceLatest) },
                            onClick = {
                                showContextMenu = false
                                onLatestClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(pinLabel) },
                        onClick = {
                            showContextMenu = false
                            onPinClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.browseHideSource) },
                        modifier = Modifier.testTag("source-context-hide-${source.id}"),
                        onClick = {
                            showContextMenu = false
                            onHide()
                        },
                    )
                    if (associatedExtension != null) {
                        DropdownMenuItem(
                            text = { Text(strings.text(UiText.ContextExtensionSettings)) },
                            onClick = {
                                showContextMenu = false
                                onExtClick()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                strings.browseSourceId(source.id.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionsListView(
    state: BrowseUiState,
    pendingUpdates: List<ExtensionStoreItem>,
    trustStatuses: Map<String, ExtensionTrustStatus>,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit,
    onUpdateAllPending: () -> Unit,
    onTrustExtension: (InstalledExtension) -> Unit,
    onRevokeExtension: (InstalledExtension) -> Unit,
) {
    val strings = LocalStrings.current
    val spacing = LocalSpacing.current
    val effectivePendingUpdates = remember(pendingUpdates, state.showNsfw) {
        if (state.showNsfw) pendingUpdates else pendingUpdates.filter { !it.isNsfw }
    }
    val effectiveInstalled = remember(state.installedExtensions, state.showNsfw) {
        if (state.showNsfw) state.installedExtensions else state.installedExtensions.filter { !it.manifest.isNsfw }
    }
    val installedPkgMap = effectiveInstalled.associateBy { it.pkg }
    val filteredAvailable = remember(state.availableExtensions, state.searchQuery, state.showNsfw) {
        state.availableExtensions.filter { item ->
            if (!state.showNsfw && item.isNsfw) return@filter false
            state.searchQuery.isBlank() ||
                item.name.contains(state.searchQuery, ignoreCase = true) ||
                item.pkg.contains(state.searchQuery, ignoreCase = true) ||
                item.lang.contains(state.searchQuery, ignoreCase = true)
        }
    }
    var collapsedLangs by remember { mutableStateOf(emptySet<String>()) }

    @Composable
    fun ExtRow(item: ExtensionStoreItem, installed: InstalledExtension?) {
        ExtensionItemRow(
            item = item,
            installed = installed,
            trustStatus = trustStatuses[item.pkg],
            isInstalling = state.isInstalling && state.installingPkg == item.pkg,
            installEnabled = !state.isInstalling,
            onRequestInstall = onRequestInstall,
            onUninstallExtension = onUninstallExtension,
            onToggleEnabled = onToggleEnabled,
            onExtensionSelected = onExtensionSelected,
            onTrustExtension = onTrustExtension,
            onRevokeExtension = onRevokeExtension,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("extensions-list"),
        verticalArrangement = Arrangement.spacedBy(spacing.listRowGap / 2),
    ) {
        if (effectivePendingUpdates.isNotEmpty()) {
            item(key = "hdr_updates") {
                BrowseSectionHeader(
                    title = strings.browseUpdate,
                    count = effectivePendingUpdates.size,
                    accent = true,
                    trailing = {
                        FilledTonalButton(
                            onClick = onUpdateAllPending,
                            enabled = !state.isInstalling,
                            modifier = Modifier.testTag("update-all-button"),
                        ) {
                            Text(strings.browseUpdateAll)
                        }
                    },
                )
            }
            items(effectivePendingUpdates, key = { "update_${it.pkg}" }) { item ->
                ExtRow(item, installedPkgMap[item.pkg])
            }
        }

        if (effectiveInstalled.isNotEmpty()) {
            item(key = "hdr_installed") {
                BrowseSectionHeader(title = strings.browseInstalledBadge, count = effectiveInstalled.size)
            }
            items(effectiveInstalled, key = { "installed_${it.pkg}" }) { installed ->
                val availableMatch = state.availableExtensions.find { it.pkg == installed.pkg }
                val item = availableMatch ?: ExtensionStoreItem(
                    pkg = installed.pkg,
                    name = installed.manifest.name,
                    version = installed.manifest.version,
                    versionCode = installed.manifest.versionCode,
                    lang = installed.manifest.lang,
                    isNsfw = installed.manifest.isNsfw,
                    sources = installed.manifest.sources,
                )
                ExtRow(item, installed)
            }
        }

        val notInstalled = filteredAvailable.filter { !installedPkgMap.containsKey(it.pkg) }
        val groupedAvailable = notInstalled.groupBy { it.lang.uppercase() }

        item(key = "hdr_available") {
            BrowseSectionHeader(title = strings.browseTabExtensions, count = notInstalled.size)
        }

        if (notInstalled.isEmpty()) {
            item {
                Text(
                    text = if (state.availableExtensions.isEmpty()) {
                        strings.text(UiText.NoRepositories)
                    } else {
                        strings.browseNoMangaFound
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
        } else {
            groupedAvailable.forEach { (lang, items) ->
                val collapsed = lang in collapsedLangs
                item(key = "hdr_avail_$lang") {
                    BrowseSectionHeader(
                        title = strings.browseSourceLanguage(lang),
                        count = items.size,
                        collapsed = collapsed,
                        onToggle = {
                            collapsedLangs = if (collapsed) collapsedLangs - lang else collapsedLangs + lang
                        },
                    )
                }
                if (!collapsed) {
                    items(items, key = { it.pkg }) { item -> ExtRow(item, null) }
                }
            }
        }
    }
}

@Composable
private fun ExtensionItemRow(
    item: ExtensionStoreItem,
    installed: InstalledExtension?,
    trustStatus: ExtensionTrustStatus?,
    isInstalling: Boolean,
    installEnabled: Boolean,
    onRequestInstall: (ExtensionStoreItem) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onExtensionSelected: (InstalledExtension) -> Unit,
    onTrustExtension: (InstalledExtension) -> Unit,
    onRevokeExtension: (InstalledExtension) -> Unit,
) {
    val strings = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }
    val displayTrustStatus = if (installed != null) {
        trustStatus ?: installed.trustStatus
    } else if (ExtensionTrustStore.normalizeFingerprint(item.signingKey).isNotBlank()) {
        ExtensionTrustStatus.TRUSTED
    } else {
        ExtensionTrustStatus.UNKNOWN
    }
    val hasUpdate = installed != null && item.versionCode > installed.manifest.versionCode
    val disabled = installed != null && !installed.isEnabled

    BrowseItemCard(
        modifier = Modifier.testTag("extension-item-${item.pkg}"),
        onClick = installed?.let { { onExtensionSelected(it) } },
        onSecondaryClick = if (installed != null) ({ showMenu = true }) else null,
    ) {
        DesktopTooltipBox(text = item.pkg) {
            Box {
                ExtensionIcon(
                    name = item.name,
                    iconUri = extensionIconUri(installed, item),
                    size = 48.dp,
                    dimmed = disabled,
                )
                if (hasUpdate) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(
                            Alignment.TopEnd,
                        ).padding(1.dp).testTag("ext-update-badge-${item.pkg}"),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (disabled) MaterialTheme.colorScheme.outline else Color.Unspecified,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isNsfw) SmallBadge("18+", BadgeTone.Danger)
                if (installed != null) {
                    SmallBadge(
                        text = strings.trustStatusLabel(displayTrustStatus),
                        tone = when (displayTrustStatus) {
                            ExtensionTrustStatus.TRUSTED -> BadgeTone.Positive
                            ExtensionTrustStatus.INVALID -> BadgeTone.Danger
                            else -> BadgeTone.Neutral
                        },
                        textTag = "extension-trust-status-${item.pkg}",
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge(item.lang.uppercase())
                val version = if (hasUpdate) {
                    "v${installed!!.manifest.version} → v${item.version}"
                } else {
                    "v${installed?.manifest?.version ?: item.version}"
                }
                val storeLabel = extensionStoreLabel(
                    item.storeName,
                    item.repoUrl.ifBlank { installed?.repoUrl.orEmpty() },
                )
                val secondary = buildList {
                    add(version)
                    if (item.sources.size > 1) {
                        add(strings.text(UiText.ExtensionSources, item.sources.joinToString(", ") { it.name }))
                    }
                }.joinToString(" · ")
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (storeLabel != null) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = strings.extensionStoreLabel(storeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("extension-store-${item.pkg}"),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (installed == null) {
                FilledTonalButton(
                    onClick = { onRequestInstall(item) },
                    modifier = Modifier.testTag("install-btn-${item.pkg}"),
                    enabled = installEnabled,
                ) {
                    Text(strings.browseInstall)
                }
            } else {
                if (hasUpdate) {
                    Button(
                        onClick = { onRequestInstall(item) },
                        modifier = Modifier.testTag("update-btn-${item.pkg}"),
                        enabled = installEnabled,
                    ) {
                        Text(strings.browseUpdate)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                DesktopTooltipBox(text = strings.extensionInfo) {
                    IconButton(
                        onClick = { onExtensionSelected(installed) },
                        modifier = Modifier.testTag("settings-btn-${item.pkg}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.extensionInfo,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box {
                    DesktopTooltipBox(text = strings.browseMoreActions) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("ext-overflow-${item.pkg}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = strings.browseMoreActions,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (displayTrustStatus == ExtensionTrustStatus.TRUSTED) {
                            DropdownMenuItem(
                                text = { Text(strings.extensionRevoke) },
                                leadingIcon = { Icon(Icons.Rounded.GppMaybe, contentDescription = null) },
                                modifier = Modifier.testTag("revoke-btn-${item.pkg}"),
                                onClick = {
                                    showMenu = false
                                    onRevokeExtension(installed)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(strings.extensionTrust) },
                                leadingIcon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
                                modifier = Modifier.testTag("trust-btn-${item.pkg}"),
                                onClick = {
                                    showMenu = false
                                    onTrustExtension(installed)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (installed.isEnabled) strings.browseDisable else strings.browseEnable) },
                            leadingIcon = {
                                Icon(
                                    if (installed.isEnabled) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.testTag("toggle-btn-${item.pkg}"),
                            onClick = {
                                showMenu = false
                                onToggleEnabled(item.pkg, !installed.isEnabled)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(strings.browseUninstall, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier.testTag("uninstall-btn-${item.pkg}"),
                            onClick = {
                                showMenu = false
                                onUninstallExtension(item.pkg)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    item.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            },
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
