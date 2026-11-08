package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

/** Title plus the global search, install-from-file, repositories and refresh buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowseHeaderRow(
    state: BrowseUiState,
    strings: DesktopStrings,
    onOpenGlobalSearch: () -> Unit,
    onInstallFromFile: (java.io.File) -> Unit,
    onManageRepositories: () -> Unit,
    onRefresh: () -> Unit,
) {
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
            BrowseHeaderButton(
                label = strings.browseGlobalSearch,
                icon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                tag = "open-global-search-button",
                onClick = onOpenGlobalSearch,
            )
            BrowseHeaderButton(
                label = strings.browseInstallFromFile,
                icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                tag = "install-file-button",
                enabled = !state.isInstalling,
                onClick = {
                    val file = chooseMextFile(strings.text(UiText.ChooseExtension))
                    if (file != null) onInstallFromFile(file)
                },
            )
            BrowseHeaderButton(
                label = strings.browseManageRepositories,
                icon = { Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                tag = "manage-repos-button",
                onClick = onManageRepositories,
            )
            BrowseHeaderButton(
                label = strings.browseRefresh,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                tag = "refresh-browse-button",
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun BrowseHeaderButton(
    label: String,
    icon: @Composable () -> Unit,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.testTag(tag), enabled = enabled) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

/** Sources / Extensions / Migration tabs, with a badge for pending extension updates. */
@Composable
internal fun BrowseTabs(
    state: BrowseUiState,
    strings: DesktopStrings,
    pendingUpdateCount: Int,
    onTabSelected: (BrowseTab) -> Unit,
) {
    TabRow(selectedTabIndex = state.selectedTab.ordinal, modifier = Modifier.fillMaxWidth()) {
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
                val label = "${strings.browseTabExtensions} (${state.availableExtensions.size})"
                if (pendingUpdateCount > 0) {
                    BadgedBox(badge = { Badge { Text(pendingUpdateCount.toString()) } }) { Text(label) }
                } else {
                    Text(label)
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
}

@Composable
internal fun BrowseSearchBar(state: BrowseUiState, strings: DesktopStrings, onQueryChange: (String) -> Unit) {
    if (state.selectedTab == BrowseTab.Migration) return
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("browse-search-bar"),
        placeholder = { Text(strings.browseSearchPlaceholder) },
        singleLine = true,
    )
}

@Composable
internal fun BrowseErrorBanner(state: BrowseUiState, strings: DesktopStrings, onRefresh: () -> Unit) {
    val error = state.errorMessage ?: return
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
