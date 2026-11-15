package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.ui.common.MangaBackdropBanner

/** Entry-specific parts of a detail page: navigation, source refresh and library membership. */
internal data class MangaDetailEntry(
    val sourceBaseUrl: String?,
    val showBack: Boolean,
    val onBack: () -> Unit,
    val onClose: (() -> Unit)?,
    val onToggleLibrary: (() -> Unit)?,
    val onRefreshSource: (() -> Unit)?,
    val isLibraryActionRunning: Boolean,
    val isRefreshingSource: Boolean,
    val onOpenChapterInWebView: ((Long) -> Unit)?,
)

/** Cover / notes dialogs and the cover cache key: pure UI state of the loaded page. */
private class MangaDetailDialogFlags {
    var coverRefreshKey by mutableStateOf(0)
    var isCoverDialogOpen by mutableStateOf(false)
    var isNotesDialogOpen by mutableStateOf(false)
}

@Composable
internal fun MangaDetailLoaded(
    manga: MangaDetails,
    state: MangaDetailUiState,
    entry: MangaDetailEntry,
    actions: MangaDetailActions,
) {
    val strings = LocalStrings.current
    val browse = state.chapterBrowse.forManga(manga.id)
    val onBrowse = actions.onChapterBrowseAction
    val flags = remember(manga.id) { MangaDetailDialogFlags() }
    val customCoverManager = LocalCustomCoverManager.current
    val hasCustomCover = remember(manga.id, flags.coverRefreshKey) {
        customCoverManager?.hasCustomCover(manga.id) == true
    }
    val chapterItems = state.chapterListItems.ifEmpty {
        buildChapterListItems(chapters = state.chapters, settings = state.chapterSettings)
    }
    val displayedChapterItems = remember(chapterItems, browse.searchQuery) {
        filterChapterItems(chapterItems, browse.searchQuery)
    }
    val nextChapter = remember(state.chapters) { nextChapterToRead(state.chapters) }

    Box(modifier = Modifier.fillMaxSize()) {
        MangaBackdropBanner(
            thumbnailUrl = manga.thumbnailUrl,
            mangaId = manga.id,
            modifier = Modifier.align(Alignment.TopCenter),
            bannerHeight = 320.dp,
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "detail-header") {
                MangaDetailHeader(manga, state, entry, actions, browse, flags, hasCustomCover, strings)
            }
            mangaChapterList(displayedChapterItems, state, entry, actions, browse, strings)
        }
        if (nextChapter != null && !browse.isSelecting) {
            NextChapterFab(
                text = nextChapterFabText(nextChapter, state.chapters, strings),
                onRead = { actions.onReadChapter(nextChapter.id) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
        ChapterActionMenu(
            visible = browse.isSelecting,
            selectedChapterIds = browse.selectedChapterIds,
            displayedChapterItems = displayedChapterItems,
            downloadedChapterIds = state.downloadedChapterIds,
            onSelectedChapterIdsChange = { onBrowse(ChapterBrowseAction.SetSelectedChapters(it)) },
            onBatchBookmarkChapters = actions.onBatchBookmarkChapters,
            onBatchMarkChaptersRead = actions.onBatchMarkChaptersRead,
            onMarkPreviousRead = actions.onMarkPreviousRead,
            onBatchDownloadChapters = actions.onBatchDownloadChapters,
            onBatchDeleteDownloads = actions.onBatchDeleteDownloads,
            onCloseSelection = { onBrowse(ChapterBrowseAction.CloseSelection) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    MangaDetailPageDialogs(manga, state, entry, actions, flags, hasCustomCover, strings)
}

@Composable
private fun MangaDetailHeader(
    manga: MangaDetails,
    state: MangaDetailUiState,
    entry: MangaDetailEntry,
    actions: MangaDetailActions,
    browse: ChapterBrowseState,
    flags: MangaDetailDialogFlags,
    hasCustomCover: Boolean,
    strings: DesktopStrings,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MangaDetailActionRow(
            manga = manga,
            strings = strings,
            sourceBaseUrl = entry.sourceBaseUrl,
            showBack = entry.showBack,
            onBack = entry.onBack,
            onClose = entry.onClose,
            isLibraryActionRunning = entry.isLibraryActionRunning,
            isRefreshingSource = entry.isRefreshingSource,
            onToggleLibrary = entry.onToggleLibrary,
            onRefreshSource = entry.onRefreshSource,
            onEditInfo = actions.onEditInfo,
            onEditCategories = actions.onEditCategories,
            onOpenTracking = actions.onOpenTracking,
        )
        MissingSourceBanner(state.missingSource, strings, actions.onInstallMissingSource, actions.onIgnoreMissingSource)
        MangaDetailInfoRow(
            manga = manga,
            strings = strings,
            sourceBaseUrl = entry.sourceBaseUrl,
            coverRefreshKey = flags.coverRefreshKey,
            hasCustomCover = hasCustomCover,
            onCoverClick = { flags.isCoverDialogOpen = true },
            onCoverChanged = { flags.coverRefreshKey++ },
            onEditNotes = { flags.isNotesDialogOpen = true },
            onCoverLoadFailed = actions.onCoverLoadFailed,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        MangaChapterControls(state, actions, browse, strings)
    }
}

@Composable
private fun MangaChapterControls(
    state: MangaDetailUiState,
    actions: MangaDetailActions,
    browse: ChapterBrowseState,
    strings: DesktopStrings,
) {
    val onBrowse = actions.onChapterBrowseAction
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MangaChapterToolbar(
            state = state,
            strings = strings,
            isSearchingChapters = browse.isSearching,
            chapterSearchQuery = browse.searchQuery,
            isSelectionMode = browse.isSelectionMode,
            selectedCount = browse.selectedChapterIds.size,
            onDownloadBatch = actions.onDownloadBatch,
            onChapterSortChange = actions.onChapterSortChange,
            onOpenChapterSettings = actions.onOpenChapterSettings,
            onOpenReadingSettings = actions.onOpenReadingSettings,
            onToggleSearch = { onBrowse(ChapterBrowseAction.ToggleSearch) },
            onToggleSelection = { onBrowse(ChapterBrowseAction.ToggleSelectionMode) },
        )
        ChapterFiltersBar(
            state = state,
            strings = strings,
            isSearchingChapters = browse.isSearching,
            chapterSearchQuery = browse.searchQuery,
            onQueryChange = { onBrowse(ChapterBrowseAction.QueryChanged(it)) },
            onChapterFilterChange = actions.onChapterFilterChange,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mangaChapterList(
    displayedChapterItems: List<ChapterListItem>,
    state: MangaDetailUiState,
    entry: MangaDetailEntry,
    actions: MangaDetailActions,
    browse: ChapterBrowseState,
    strings: DesktopStrings,
) {
    val onBrowse = actions.onChapterBrowseAction
    chapterListItems(
        displayedChapterItems = displayedChapterItems,
        chapterSearchQuery = browse.searchQuery,
        state = state,
        strings = strings,
        isSelectionMode = browse.isSelectionMode,
        selectedChapterIds = browse.selectedChapterIds,
        selectionAnchorId = browse.selectionAnchorId,
        onSelectedChapterIdsChange = { onBrowse(ChapterBrowseAction.SetSelectedChapters(it)) },
        onSelectionModeChange = { onBrowse(ChapterBrowseAction.SetSelectionMode(it)) },
        onSelectionAnchorChange = { onBrowse(ChapterBrowseAction.SetSelectionAnchor(it)) },
        onReadChapter = actions.onReadChapter,
        onToggleBookmark = actions.onToggleBookmark,
        onToggleRead = actions.onToggleRead,
        onMarkPreviousRead = actions.onMarkPreviousRead,
        onDownloadChapter = actions.onDownloadChapter,
        onDeleteDownload = actions.onDeleteDownload,
        onOpenChapterInWebView = entry.onOpenChapterInWebView,
    )
}

@Composable
private fun MangaDetailPageDialogs(
    manga: MangaDetails,
    state: MangaDetailUiState,
    entry: MangaDetailEntry,
    actions: MangaDetailActions,
    flags: MangaDetailDialogFlags,
    hasCustomCover: Boolean,
    strings: DesktopStrings,
) {
    MangaDetailDialogs(
        manga = manga,
        strings = strings,
        state = state,
        sourceBaseUrl = entry.sourceBaseUrl,
        hasCustomCover = hasCustomCover,
        isCoverDialogOpen = flags.isCoverDialogOpen,
        isNotesDialogOpen = flags.isNotesDialogOpen,
        onDismissCover = { flags.isCoverDialogOpen = false },
        onDismissNotes = { flags.isNotesDialogOpen = false },
        onCoverChanged = { flags.coverRefreshKey++ },
        onSaveMangaInfo = actions.onSaveMangaInfo,
        onDismissEditInfo = actions.onDismissEditInfo,
        onResetMangaInfo = actions.onResetMangaInfo,
        onDismissChapterSettings = actions.onDismissChapterSettings,
        onChapterSortChange = actions.onChapterSortChange,
        onChapterDisplayModeChange = actions.onChapterDisplayModeChange,
        onShowMissingChaptersChange = actions.onShowMissingChaptersChange,
        onExcludedScanlatorsChange = actions.onExcludedScanlatorsChange,
        onSetChapterSettingsAsDefault = actions.onSetChapterSettingsAsDefault,
        onResetChapterSettingsToDefault = actions.onResetChapterSettingsToDefault,
        onDismissReadingSettings = actions.onDismissReadingSettings,
        onSaveReadingSettings = actions.onSaveReadingSettings,
        onClearReadingSettings = actions.onClearReadingSettings,
    )
}

private fun nextChapterFabText(next: LibraryChapter, chapters: List<LibraryChapter>, strings: DesktopStrings): String =
    if (chapters.none { it.read } || chapters.all { it.read }) {
        strings.mangaDetailStart(next.name)
    } else {
        strings.mangaDetailResume(next.name)
    }
