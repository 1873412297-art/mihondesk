package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.ui.common.MangaBackdropBanner
import mihon.desktop.ui.common.formatNetworkErrorMessage

@Composable
fun MangaDetailScreen(
    state: MangaDetailUiState,
    onBack: () -> Unit,
    actions: MangaDetailActions = MangaDetailActions(),
    onReadChapter: (Long) -> Unit = actions.onReadChapter,
    onRetry: () -> Unit = {},
    onToggleLibrary: (() -> Unit)? = null,
    onRefreshSource: (() -> Unit)? = null,
    onInstallMissingSource: (ExtensionStoreItem) -> Unit = actions.onInstallMissingSource,
    onIgnoreMissingSource: () -> Unit = actions.onIgnoreMissingSource,
    isLibraryActionRunning: Boolean = false,
    isRefreshingSource: Boolean = false,
    modifier: Modifier = Modifier,
    sourceBaseUrl: String? = null,
    showBack: Boolean = true,
    /** Shown as a ✕ at the right end of the action row (side pane). */
    onClose: (() -> Unit)? = null,
    onEditCategories: () -> Unit = actions.onEditCategories,
    onOpenTracking: () -> Unit = actions.onOpenTracking,
    onEditInfo: () -> Unit = actions.onEditInfo,
    onDismissEditInfo: () -> Unit = actions.onDismissEditInfo,
    onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = actions.onSaveMangaInfo,
    onResetMangaInfo: () -> Unit = actions.onResetMangaInfo,
    onChapterFilterChange: (ChapterFilterState) -> Unit = actions.onChapterFilterChange,
    onChapterSortChange: (ChapterSortState) -> Unit = actions.onChapterSortChange,
    onToggleBookmark: (Long) -> Unit = actions.onToggleBookmark,
    onToggleRead: (Long) -> Unit = actions.onToggleRead,
    onMarkPreviousRead: (Long) -> Unit = actions.onMarkPreviousRead,
    onDownloadChapter: (Long) -> Unit = actions.onDownloadChapter,
    onDeleteDownload: (Long) -> Unit = actions.onDeleteDownload,
    onDownloadBatch: (Int?) -> Unit = actions.onDownloadBatch,
    onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = actions.onBatchBookmarkChapters,
    onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = actions.onBatchMarkChaptersRead,
    onBatchDownloadChapters: (Set<Long>) -> Unit = actions.onBatchDownloadChapters,
    onBatchDeleteDownloads: (Set<Long>) -> Unit = actions.onBatchDeleteDownloads,
    onOpenChapterSettings: () -> Unit = actions.onOpenChapterSettings,
    onDismissChapterSettings: () -> Unit = actions.onDismissChapterSettings,
    onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = actions.onChapterDisplayModeChange,
    onExcludedScanlatorsChange: (Set<String>) -> Unit = actions.onExcludedScanlatorsChange,
    onShowMissingChaptersChange: (Boolean) -> Unit = actions.onShowMissingChaptersChange,
    onSetChapterSettingsAsDefault: (Boolean) -> Unit = actions.onSetChapterSettingsAsDefault,
    onResetChapterSettingsToDefault: () -> Unit = actions.onResetChapterSettingsToDefault,
    onOpenReadingSettings: () -> Unit = actions.onOpenReadingSettings,
    onDismissReadingSettings: () -> Unit = actions.onDismissReadingSettings,
    onSaveReadingSettings: (MangaReaderSettingsOverride) -> Unit = actions.onSaveReadingSettings,
    onClearReadingSettings: () -> Unit = actions.onClearReadingSettings,
    onCoverLoadFailed: ((Int) -> Unit)? = actions.onCoverLoadFailed,
    onOpenChapterInWebView: ((Long) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier.testTag("manga-detail-pane"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.loading -> DetailLoading(showBack, onBack)
            state.errorMessage != null -> DetailMessage(
                message = formatNetworkErrorMessage(state.errorMessage, strings),
                tag = "manga-detail-error",
                onBack = onBack,
                showBack = showBack,
                onRetry = onRetry,
            )
            state.manga == null -> DetailMessage(
                message = strings.text(UiText.MangaRemoved),
                tag = "manga-detail-missing",
                onBack = onBack,
                showBack = showBack,
                onRetry = null,
            )
            else -> {
                val manga = state.manga
                val strings = mihon.desktop.i18n.LocalStrings.current
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                val customCoverManager = mihon.desktop.image.LocalCustomCoverManager.current
                val imageLoader = mihon.desktop.image.LocalImageLoader.current
                var coverRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                val hasCustomCover = androidx.compose.runtime.remember(manga.id, coverRefreshKey) {
                    customCoverManager?.hasCustomCover(manga.id) == true
                }
                val chapterItems = state.chapterListItems.ifEmpty {
                    buildChapterListItems(
                        chapters = state.chapters,
                        settings = state.chapterSettings,
                    )
                }

                var chapterSearchQuery by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("")
                }
                var isSearchingChapters by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isCoverDialogOpen by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isNotesDialogOpen by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var isSelectionMode by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                var selectedChapterIds by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(emptySet<Long>())
                }
                // Last chapter toggled by a plain click; Shift+click selects everything between it and the target.
                var selectionAnchorId by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<Long?>(null)
                }

                val displayedChapterItems = androidx.compose.runtime.remember(chapterItems, chapterSearchQuery) {
                    filterChapterItems(chapterItems, chapterSearchQuery)
                }
                val nextChapterToRead = androidx.compose.runtime.remember(state.chapters) {
                    nextChapterToRead(state.chapters)
                }

                val fabText = androidx.compose.runtime.remember(nextChapterToRead, state.chapters) {
                    when {
                        nextChapterToRead == null -> ""
                        state.chapters.none { it.read } -> strings.mangaDetailStart(nextChapterToRead.name)
                        state.chapters.all { it.read } -> strings.mangaDetailStart(nextChapterToRead.name)
                        else -> strings.mangaDetailResume(nextChapterToRead.name)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MangaBackdropBanner(
                        thumbnailUrl = manga.thumbnailUrl,
                        mangaId = manga.id,
                        modifier = Modifier.align(Alignment.TopCenter),
                        bannerHeight = 320.dp,
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "detail-header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                MangaDetailActionRow(
                                    manga = manga,
                                    strings = strings,
                                    sourceBaseUrl = sourceBaseUrl,
                                    showBack = showBack,
                                    onBack = onBack,
                                    onClose = onClose,
                                    isLibraryActionRunning = isLibraryActionRunning,
                                    isRefreshingSource = isRefreshingSource,
                                    onToggleLibrary = onToggleLibrary,
                                    onRefreshSource = onRefreshSource,
                                    onEditInfo = onEditInfo,
                                    onEditCategories = onEditCategories,
                                    onOpenTracking = onOpenTracking,
                                )

                                MissingSourceBanner(
                                    state.missingSource,
                                    strings,
                                    onInstallMissingSource,
                                    onIgnoreMissingSource,
                                )

                                MangaDetailInfoRow(
                                    manga = manga,
                                    strings = strings,
                                    sourceBaseUrl = sourceBaseUrl,
                                    coverRefreshKey = coverRefreshKey,
                                    hasCustomCover = hasCustomCover,
                                    onCoverClick = { isCoverDialogOpen = true },
                                    onCoverChanged = { coverRefreshKey++ },
                                    onEditNotes = { isNotesDialogOpen = true },
                                    onCoverLoadFailed = onCoverLoadFailed,
                                )

                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MangaChapterToolbar(
                                        state = state,
                                        strings = strings,
                                        isSearchingChapters = isSearchingChapters,
                                        chapterSearchQuery = chapterSearchQuery,
                                        isSelectionMode = isSelectionMode,
                                        selectedCount = selectedChapterIds.size,
                                        onDownloadBatch = onDownloadBatch,
                                        onChapterSortChange = onChapterSortChange,
                                        onOpenChapterSettings = onOpenChapterSettings,
                                        onOpenReadingSettings = onOpenReadingSettings,
                                        onToggleSearch = {
                                            isSearchingChapters = !isSearchingChapters
                                            if (!isSearchingChapters) chapterSearchQuery = ""
                                        },
                                        onToggleSelection = {
                                            isSelectionMode = !isSelectionMode
                                            if (!isSelectionMode) selectedChapterIds = emptySet()
                                        },
                                    )

                                    ChapterFiltersBar(
                                        state = state,
                                        strings = strings,
                                        isSearchingChapters = isSearchingChapters,
                                        chapterSearchQuery = chapterSearchQuery,
                                        onQueryChange = { chapterSearchQuery = it },
                                        onChapterFilterChange = onChapterFilterChange,
                                    )
                                }
                            }
                        }
                        chapterListItems(
                            displayedChapterItems = displayedChapterItems,
                            chapterSearchQuery = chapterSearchQuery,
                            state = state,
                            strings = strings,
                            isSelectionMode = isSelectionMode,
                            selectedChapterIds = selectedChapterIds,
                            selectionAnchorId = selectionAnchorId,
                            onSelectedChapterIdsChange = { selectedChapterIds = it },
                            onSelectionModeChange = { isSelectionMode = it },
                            onSelectionAnchorChange = { selectionAnchorId = it },
                            onReadChapter = onReadChapter,
                            onToggleBookmark = onToggleBookmark,
                            onToggleRead = onToggleRead,
                            onMarkPreviousRead = onMarkPreviousRead,
                            onDownloadChapter = onDownloadChapter,
                            onDeleteDownload = onDeleteDownload,
                            onOpenChapterInWebView = onOpenChapterInWebView,
                        )
                    }

                    if (nextChapterToRead != null && !isSelectionMode && selectedChapterIds.isEmpty()) {
                        NextChapterFab(
                            text = fabText,
                            onRead = { onReadChapter(nextChapterToRead.id) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                        )
                    }

                    ChapterActionMenu(
                        visible = isSelectionMode || selectedChapterIds.isNotEmpty(),
                        selectedChapterIds = selectedChapterIds,
                        displayedChapterItems = displayedChapterItems,
                        downloadedChapterIds = state.downloadedChapterIds,
                        onSelectedChapterIdsChange = { selectedChapterIds = it },
                        onBatchBookmarkChapters = onBatchBookmarkChapters,
                        onBatchMarkChaptersRead = onBatchMarkChaptersRead,
                        onMarkPreviousRead = onMarkPreviousRead,
                        onBatchDownloadChapters = onBatchDownloadChapters,
                        onBatchDeleteDownloads = onBatchDeleteDownloads,
                        onCloseSelection = {
                            isSelectionMode = false
                            selectedChapterIds = emptySet()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                MangaDetailDialogs(
                    manga = manga,
                    strings = strings,
                    state = state,
                    sourceBaseUrl = sourceBaseUrl,
                    hasCustomCover = hasCustomCover,
                    isCoverDialogOpen = isCoverDialogOpen,
                    isNotesDialogOpen = isNotesDialogOpen,
                    onDismissCover = { isCoverDialogOpen = false },
                    onDismissNotes = { isNotesDialogOpen = false },
                    onCoverChanged = { coverRefreshKey++ },
                    onSaveMangaInfo = onSaveMangaInfo,
                    onDismissEditInfo = onDismissEditInfo,
                    onResetMangaInfo = onResetMangaInfo,
                    onDismissChapterSettings = onDismissChapterSettings,
                    onChapterSortChange = onChapterSortChange,
                    onChapterDisplayModeChange = onChapterDisplayModeChange,
                    onShowMissingChaptersChange = onShowMissingChaptersChange,
                    onExcludedScanlatorsChange = onExcludedScanlatorsChange,
                    onSetChapterSettingsAsDefault = onSetChapterSettingsAsDefault,
                    onResetChapterSettingsToDefault = onResetChapterSettingsToDefault,
                    onDismissReadingSettings = onDismissReadingSettings,
                    onSaveReadingSettings = onSaveReadingSettings,
                    onClearReadingSettings = onClearReadingSettings,
                )
            }
        }
    }
}
