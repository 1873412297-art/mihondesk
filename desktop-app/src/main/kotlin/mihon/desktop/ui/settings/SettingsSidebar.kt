package mihon.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.searchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus

@Composable
internal fun SettingsSidebar(
    strings: DesktopStrings,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchingResults: List<SearchableSettingEntry>,
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
    onSearchResult: (SearchableSettingEntry) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = strings.settingsTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SettingsSearchField(
            strings = strings,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            matchingResults = matchingResults,
            onSearchResult = onSearchResult,
        )
        SettingsSectionItems(
            strings = strings,
            searchQuery = searchQuery,
            selectedSection = selectedSection,
            onSectionSelected = onSectionSelected,
            onSearchQueryChange = onSearchQueryChange,
        )
    }
}

@Composable
private fun SettingsSearchField(
    strings: DesktopStrings,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchingResults: List<SearchableSettingEntry>,
    onSearchResult: (SearchableSettingEntry) -> Unit,
) {
    val searchFocusRequester = LocalSearchFocusRequester.current
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                text = strings.settingsSearchPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier.testTag("settings-clear-search").size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.text(UiText.ClearSearch),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .searchFocusRequester(searchFocusRequester)
            .trackTextInputFocus()
            .onPreviewKeyEvent { event ->
                handleSearchKey(event, searchQuery, matchingResults, onSearchQueryChange, onSearchResult)
            }
            .testTag("settings-search"),
    )
}

private fun handleSearchKey(
    event: androidx.compose.ui.input.key.KeyEvent,
    searchQuery: String,
    matchingResults: List<SearchableSettingEntry>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResult: (SearchableSettingEntry) -> Unit,
): Boolean {
    if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
        if (searchQuery.isEmpty()) return false
        onSearchQueryChange("")
        return true
    }
    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
        val first = matchingResults.firstOrNull() ?: return false
        onSearchResult(first)
        onSearchQueryChange("")
        return true
    }
    return false
}

@Composable
private fun SettingsSectionItems(
    strings: DesktopStrings,
    searchQuery: String,
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    SettingsSection.entries.forEachIndexed { index, section ->
        if (index == 0 || SettingsSection.entries[index - 1].group != section.group) {
            SettingsGroupHeader(strings, index, section)
        }
        SettingsSectionRow(
            strings = strings,
            section = section,
            isSelected = searchQuery.isBlank() && section == selectedSection,
            onClick = {
                onSectionSelected(section)
                onSearchQueryChange("")
            },
        )
    }
}

@Composable
private fun SettingsGroupHeader(strings: DesktopStrings, index: Int, section: SettingsSection) {
    val header = section.group.localized(strings)
    if (header != null) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 14.dp, top = if (index == 0) 4.dp else 16.dp, bottom = 4.dp)
                .testTag("settings-group-${section.group.name}"),
        )
    } else {
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun SettingsSectionRow(
    strings: DesktopStrings,
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("settings-section-${section.name}"),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = section.localized(strings),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
