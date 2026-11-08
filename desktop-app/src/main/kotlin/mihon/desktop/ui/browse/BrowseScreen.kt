package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.navigation.DesktopBackHandler
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import java.io.File

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
    trustStore: ExtensionTrustStore? = null,
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

    val activeTrustStore = trustStore
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
            val store = activeTrustStore
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
        ?: { installed -> activeTrustStore?.revoke(installed.pkg) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("browse-screen")) {
        BrowseHeaderRow(
            state = state,
            strings = strings,
            onOpenGlobalSearch = onOpenGlobalSearch,
            onInstallFromFile = onInstallFromFile,
            onManageRepositories = { showRepoDialog = true },
            onRefresh = onRefresh,
        )

        BrowseTabs(
            state = state,
            strings = strings,
            pendingUpdateCount = pendingUpdates.size,
            onTabSelected = onTabSelected,
        )

        BrowseSearchBar(state, strings, onSearchQueryChange)

        ExtensionInstallStatus(state, onCancelInstallation)

        BrowseErrorBanner(state, strings, onRefresh)

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
        InstallConfirmationDialog(
            item = item,
            strings = strings,
            onDismiss = { pendingInstallItem = null },
            onConfirm = { toInstall ->
                pendingInstallItem = null
                onInstallExtension(toInstall)
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
        TrustRetryDialog(
            request = request,
            strings = strings,
            onDismiss = { activeTrustStore?.clearPendingTrustRequest() },
            onConfirm = {
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
        )
    }

    // Repository Management Dialog
    if (showRepoDialog) {
        RepositoryManagementDialog(
            repositories = state.repositories,
            strings = strings,
            onAdd = onAddRepository,
            onRemove = onRemoveRepository,
            onDismiss = { showRepoDialog = false },
        )
    }
}
