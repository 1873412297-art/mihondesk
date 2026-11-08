package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourceState
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.extension.model.SourceDescriptor

/**
 * Desktop pixel-perfect replication of upstream Mihon's Extension Details screen.
 * Follows eu.kanade.presentation.browse.ExtensionDetailsScreen layout and behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailsScreen(
    extension: InstalledExtension,
    sources: List<SourceState>,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: (ExtensionStoreItem) -> Unit,
    onUninstall: () -> Unit,
    onClearCookies: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleSourceEnabled: (Long, Boolean) -> Unit,
    onToggleSourceIncognito: (Long, Boolean) -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onOpenSource: (SourceDescriptor) -> Unit = {},
    trustStatus: ExtensionTrustStatus? = null,
    extensionTrustStatus: ExtensionTrustStatus? = trustStatus,
    trustState: ExtensionTrustStatus? = null,
    extensionTrustState: ExtensionTrustStatus? = trustState,
    onTrust: (() -> Unit)? = null,
    onTrustExtension: ((InstalledExtension) -> Unit)? = null,
    onRevoke: (() -> Unit)? = null,
    onRevokeExtension: ((InstalledExtension) -> Unit)? = null,
    onTrustPackage: ((String) -> Unit)? = null,
    onRevokePackage: ((String) -> Unit)? = null,
    trustStore: ExtensionTrustStore? = null,
) {
    val environment = rememberExtensionDetailsEnvironment(
        extension = extension,
        extensionTrustStatus = extensionTrustStatus,
        extensionTrustState = extensionTrustState,
        trustStore = trustStore,
        onTrust = onTrust,
        onTrustExtension = onTrustExtension,
        onTrustPackage = onTrustPackage,
        onRevoke = onRevoke,
        onRevokeExtension = onRevokeExtension,
        onRevokePackage = onRevokePackage,
    )
    ExtensionDetailsScaffold(
        extension = extension,
        sources = sources,
        isExtensionIncognito = isExtensionIncognito,
        updateItem = updateItem,
        environment = environment,
        onBack = onBack,
        onToggleEnabled = onToggleEnabled,
        onUpdate = onUpdate,
        onUninstall = onUninstall,
        onClearCookies = onClearCookies,
        onToggleIncognito = onToggleIncognito,
        onToggleSourceEnabled = onToggleSourceEnabled,
        onToggleSourceIncognito = onToggleSourceIncognito,
        onOpenSourcePreferences = onOpenSourcePreferences,
        onOpenSource = onOpenSource,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionDetailsScaffold(
    extension: InstalledExtension,
    sources: List<SourceState>,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    environment: ExtensionDetailsEnvironment,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: (ExtensionStoreItem) -> Unit,
    onUninstall: () -> Unit,
    onClearCookies: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleSourceEnabled: (Long, Boolean) -> Unit,
    onToggleSourceIncognito: (Long, Boolean) -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onOpenSource: (SourceDescriptor) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("extension-details-screen"),
        snackbarHost = { SnackbarHost(environment.snackbarHostState) },
        topBar = {
            ExtensionDetailsTopBar(
                repoUrl = environment.repoUrl,
                showMenuTrust = environment.menuShowsTrust,
                showMenuRevoke = environment.menuShowsRevoke,
                onBack = onBack,
                onToggleEnabled = onToggleEnabled,
                onClearCookies = onClearCookies,
                onTrust = environment.trust,
                onRevoke = environment.revoke,
                onShowSnackbar = environment.showSnackbar,
            )
        },
    ) { contentPadding ->
        ExtensionDetailsContent(
            extension = extension,
            sources = sources,
            isExtensionIncognito = isExtensionIncognito,
            updateItem = updateItem,
            environment = environment,
            contentPadding = contentPadding,
            onToggleEnabled = onToggleEnabled,
            onUpdate = onUpdate,
            onUninstall = onUninstall,
            onClearCookies = onClearCookies,
            onToggleIncognito = onToggleIncognito,
            onToggleSourceEnabled = onToggleSourceEnabled,
            onToggleSourceIncognito = onToggleSourceIncognito,
            onOpenSourcePreferences = onOpenSourcePreferences,
            onOpenSource = onOpenSource,
        )
    }
}

@Composable
private fun ExtensionDetailsContent(
    extension: InstalledExtension,
    sources: List<SourceState>,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    environment: ExtensionDetailsEnvironment,
    contentPadding: PaddingValues,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: (ExtensionStoreItem) -> Unit,
    onUninstall: () -> Unit,
    onClearCookies: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleSourceEnabled: (Long, Boolean) -> Unit,
    onToggleSourceIncognito: (Long, Boolean) -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onOpenSource: (SourceDescriptor) -> Unit,
) {
    val strings = LocalStrings.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("extension-details-content"),
    ) {
        extensionWarningBanners(
            strings = strings,
            extension = extension,
            trustStatus = environment.trustStatus,
        )
        item {
            ExtensionDetailsHeaderItem(
                extension = extension,
                sources = sources,
                isExtensionIncognito = isExtensionIncognito,
                updateItem = updateItem,
                environment = environment,
                onToggleEnabled = onToggleEnabled,
                onUpdate = onUpdate,
                onUninstall = onUninstall,
                onClearCookies = onClearCookies,
                onToggleIncognito = onToggleIncognito,
                onOpenSourcePreferences = onOpenSourcePreferences,
            )
        }
        extensionSourceItems(
            strings = strings,
            sources = sources,
            onClickSourcePreferences = onOpenSourcePreferences,
            onClickSource = onOpenSource,
            onToggleSourceEnabled = onToggleSourceEnabled,
            onToggleSourceIncognito = onToggleSourceIncognito,
        )
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExtensionDetailsHeaderItem(
    extension: InstalledExtension,
    sources: List<SourceState>,
    isExtensionIncognito: Boolean,
    updateItem: ExtensionStoreItem?,
    environment: ExtensionDetailsEnvironment,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: (ExtensionStoreItem) -> Unit,
    onUninstall: () -> Unit,
    onClearCookies: () -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
) {
    val strings = LocalStrings.current
    DetailsHeader(
        extension = extension,
        icon = environment.icon,
        effectiveTrustStatus = environment.trustStatus,
        isExtensionIncognito = isExtensionIncognito,
        updateItem = updateItem,
        sources = sources,
        dateFormatter = environment.dateFormatter,
        onClickUninstall = onUninstall,
        onClickUpdate = { updateItem?.let(onUpdate) },
        onToggleEnabled = onToggleEnabled,
        onClearCookies = onClearCookies,
        onOpenSourcePreferences = onOpenSourcePreferences,
        onExtIncognitoChange = onToggleIncognito,
        onCopyDebugInfo = {
            copyExtensionDebugInfo(extension, environment.trustStatus)
            environment.showSnackbar(strings.extensionDebugInfoCopied)
        },
        onTrustClick = environment.trust,
        onRevokeClick = environment.revoke,
        showTrustButton = environment.showTrustButton,
        showRevokeButton = environment.showRevokeButton,
    )
}

private fun LazyListScope.extensionWarningBanners(
    strings: DesktopStrings,
    extension: InstalledExtension,
    trustStatus: ExtensionTrustStatus,
) {
    if (extension.manifest.libVersion < 1.0) {
        item {
            WarningBanner(text = strings.extensionObsolete)
        }
    }
    if (trustStatus == ExtensionTrustStatus.UNTRUSTED) {
        item {
            WarningBanner(text = strings.extensionUntrusted)
        }
    }
}
