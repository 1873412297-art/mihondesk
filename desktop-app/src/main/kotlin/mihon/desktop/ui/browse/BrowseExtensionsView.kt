package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.LocalSpacing

@Composable
internal fun ExtensionsListView(
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
