package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.trackTextInputFocus

/** Chapter search field (when toggled) and the unread/downloaded/bookmarked filter chips. */
@Composable
internal fun ChapterFiltersBar(
    state: MangaDetailUiState,
    strings: DesktopStrings,
    isSearchingChapters: Boolean,
    chapterSearchQuery: String,
    onQueryChange: (String) -> Unit,
    onChapterFilterChange: (ChapterFilterState) -> Unit,
) {
    if (isSearchingChapters) {
        ChapterSearchField(strings, chapterSearchQuery, onQueryChange)
    }
    val filters = state.chapterFilterState
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChapterFilterChip(
            selected = filters.unread != TriStateFilter.Disabled,
            label = unreadLabel(filters.unread, strings),
            tag = "chapter-filter-unread",
        ) { onChapterFilterChange(filters.copy(unread = filters.unread.next())) }
        ChapterFilterChip(
            selected = filters.downloaded != TriStateFilter.Disabled,
            label = downloadedLabel(filters.downloaded, strings),
            tag = "chapter-filter-downloaded",
        ) { onChapterFilterChange(filters.copy(downloaded = filters.downloaded.next())) }
        ChapterFilterChip(
            selected = filters.bookmarked != TriStateFilter.Disabled,
            label = bookmarkedLabel(filters.bookmarked, strings),
            tag = "chapter-filter-bookmarked",
        ) { onChapterFilterChange(filters.copy(bookmarked = filters.bookmarked.next())) }
    }
}

@Composable
private fun ChapterSearchField(
    strings: DesktopStrings,
    chapterSearchQuery: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = chapterSearchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .trackTextInputFocus()
            .testTag("chapter-search-text-field"),
        placeholder = {
            Text(
                text = strings.mangaDetailSearchChaptersPlaceholder,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (chapterSearchQuery.isNotEmpty()) {
                DesktopTooltipBox(text = strings.text(UiText.ClearSearch)) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.text(UiText.ClearSearch),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ChapterFilterChip(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
    )
}

private fun unreadLabel(filter: TriStateFilter, strings: DesktopStrings): String = when (filter) {
    TriStateFilter.Include -> strings.filterUnreadOnly
    TriStateFilter.Exclude -> strings.filterReadOnly
    TriStateFilter.Disabled -> strings.filterUnread
}

private fun downloadedLabel(filter: TriStateFilter, strings: DesktopStrings): String = when (filter) {
    TriStateFilter.Include -> strings.filterDownloadedOnly
    TriStateFilter.Exclude -> strings.filterNotDownloadedOnly
    TriStateFilter.Disabled -> strings.filterDownloaded
}

private fun bookmarkedLabel(filter: TriStateFilter, strings: DesktopStrings): String = when (filter) {
    TriStateFilter.Include -> strings.filterBookmarkedOnly
    TriStateFilter.Exclude -> strings.filterNotBookmarkedOnly
    TriStateFilter.Disabled -> strings.filterBookmarked
}
