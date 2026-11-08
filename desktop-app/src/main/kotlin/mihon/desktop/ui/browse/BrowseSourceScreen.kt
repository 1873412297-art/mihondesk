package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

@Composable
fun BrowseSourceScreen(
    state: BrowseSourceUiState,
    onBack: () -> Unit,
    onModeChange: (SourceListingMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPageChange: (Int) -> Unit,
    onMangaSelected: (SManga) -> Unit,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenFilters: () -> Unit = {},
    onCloseFilters: () -> Unit = {},
    onResetFilters: () -> Unit = {},
    onApplyFilters: (FilterList) -> Unit = {},
    onImportLocal: () -> Unit = {},
    onOpenWebPage: (() -> Unit)? = null,
    onToggleLibrary: ((SManga) -> Unit)? = null,
) {
    val strings = LocalStrings.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("browse-source-screen")) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("source-back-btn")) {
                    Text(strings.mangaDetailBack)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = state.source.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.browseSourceLanguage(state.source.lang.uppercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Mode chips
            Row {
                FilterChip(
                    selected = state.mode == SourceListingMode.Popular,
                    onClick = { onModeChange(SourceListingMode.Popular) },
                    label = { Text(strings.browseSourcePopular) },
                    modifier = Modifier.testTag("mode-popular-chip"),
                )
                if (state.source.supportsLatest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = state.mode == SourceListingMode.Latest,
                        onClick = { onModeChange(SourceListingMode.Latest) },
                        label = { Text(strings.browseSourceLatest) },
                        modifier = Modifier.testTag("mode-latest-chip"),
                    )
                }
            }
        }

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(strings.browseSearchTitlesPlaceholder) },
                modifier = Modifier.weight(1f).testTag("source-search-input").onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp && !state.isLoading) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSearch,
                modifier = Modifier.testTag("source-search-btn"),
                enabled = !state.isLoading,
            ) {
                Text(strings.browseSearchButton)
            }
            if (state.filterList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag("source-filter-btn"),
                ) {
                    val count = state.activeFilterCount
                    Text(strings.browseFiltersButton(count))
                }
            }
            if (onOpenWebPage != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onOpenWebPage, modifier = Modifier.testTag("source-open-webpage")) {
                    Text(strings.browseOpenWebsite)
                }
            }
        }

        // Error message
        state.errorMessage?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    state.networkFailure?.let {
                        Text(sourceNetworkFailureText(it), color = MaterialTheme.colorScheme.error)
                    }
                        ?: mihon.desktop.ui.common.FailureExplanation(
                            mihon.desktop.download.classifyDownloadFailure(error),
                        )
                    mihon.desktop.ui.common.ErrorDetails(error, "source-error")
                    if (onOpenWebPage != null && suggestsWebVerification(error, state.networkFailure)) {
                        Text(strings.text(mihon.desktop.i18n.UiText.HintWebVerificationAction))
                        OutlinedButton(
                            onClick = onOpenWebPage,
                            modifier = Modifier.testTag("source-verify-webview"),
                        ) { Text(strings.text(mihon.desktop.i18n.UiText.SourceVerifyInWebView)) }
                    }
                }
                Button(onClick = onRetry) { Text(strings.libraryRetry) }
            }
        }

        // Auto load more when scrolling near bottom
        val gridState = rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) return@derivedStateOf false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 4 ||
                    (lastVisible >= totalItems - 1 && layoutInfo.visibleItemsInfo.size == totalItems)
            }
        }

        LaunchedEffect(shouldLoadMore, state.hasNextPage, state.isLoading, state.isLoadingMore, state.errorMessage) {
            if (shouldLoadMore && state.hasNextPage && !state.isLoading && !state.isLoadingMore &&
                state.errorMessage == null
            ) {
                onLoadMore()
            }
        }

        // Main Grid or Loading
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("source-loading-indicator"))
            }
        } else if (state.unsupportedMode != null) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp).testTag("source-unsupported-message"),
                ) {
                    Text(
                        strings.browseSourceUnsupportedOperation(state.unsupportedMode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (state.mangas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        strings.browseNoMangaFound,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (state.source.isLocalSource()) {
                        Button(
                            onClick = onImportLocal,
                            modifier = Modifier.testTag("local-source-import-btn"),
                        ) {
                            Text(strings.libraryImportLocal)
                        }
                    }
                }
            }
        } else {
            val gridCoverHeaders = remember(state.source.baseUrl) {
                mihon.desktop.ui.common.coverHeaders(state.source.baseUrl, null)
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.weight(1f).testTag("manga-grid"),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.mangas, key = { it.url }) { manga ->
                    BrowseMangaCard(
                        manga = manga,
                        inLibrary = state.inLibraryUrls.contains(manga.url),
                        libraryPending = state.libraryPendingUrls.contains(manga.url),
                        chapterCount = state.chapterCounts[manga.url],
                        coverHeaders = gridCoverHeaders,
                        onOpen = { onMangaSelected(manga) },
                        onToggleLibrary = onToggleLibrary?.let { toggle -> { toggle(manga) } },
                    )
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        // Pagination Controls
        if (state.mangas.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { if (state.page > 1) onPageChange(state.page - 1) },
                    enabled = state.page > 1 && !state.isLoading && !state.isLoadingMore,
                    modifier = Modifier.testTag("prev-page-btn"),
                ) {
                    Text(strings.browsePrevPage)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${strings.browsePageNumber(state.page)} (${state.mangas.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = {
                        if (state.hasNextPage) {
                            onPageChange(state.page + 1)
                        }
                    },
                    enabled = state.hasNextPage && !state.isLoading && !state.isLoadingMore,
                    modifier = Modifier.testTag("next-page-btn"),
                ) {
                    Text(strings.browseNextPage)
                }
            }
        }

        if (state.isFilterDialogOpen) {
            SourceFilterDialog(
                filterList = state.filterList,
                defaultFilterList = state.defaultFilterList ?: state.filterList,
                onDismissRequest = onCloseFilters,
                onReset = onResetFilters,
                onApply = { applied ->
                    onApplyFilters(applied)
                },
            )
        }
    }
}
