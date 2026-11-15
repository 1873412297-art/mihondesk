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
    onChapterBrowseAction: (ChapterBrowseAction) -> Unit = actions.onChapterBrowseAction,
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
            else -> MangaDetailLoaded(
                manga = state.manga,
                state = state,
                entry = MangaDetailEntry(
                    sourceBaseUrl = sourceBaseUrl,
                    showBack = showBack,
                    onBack = onBack,
                    onClose = onClose,
                    onToggleLibrary = onToggleLibrary,
                    onRefreshSource = onRefreshSource,
                    isLibraryActionRunning = isLibraryActionRunning,
                    isRefreshingSource = isRefreshingSource,
                    onOpenChapterInWebView = onOpenChapterInWebView,
                ),
                actions = actions.copy(
                    onReadChapter = onReadChapter,
                    onInstallMissingSource = onInstallMissingSource,
                    onIgnoreMissingSource = onIgnoreMissingSource,
                    onEditCategories = onEditCategories,
                    onOpenTracking = onOpenTracking,
                    onEditInfo = onEditInfo,
                    onDismissEditInfo = onDismissEditInfo,
                    onSaveMangaInfo = onSaveMangaInfo,
                    onResetMangaInfo = onResetMangaInfo,
                    onChapterFilterChange = onChapterFilterChange,
                    onChapterSortChange = onChapterSortChange,
                    onToggleBookmark = onToggleBookmark,
                    onToggleRead = onToggleRead,
                    onMarkPreviousRead = onMarkPreviousRead,
                    onDownloadChapter = onDownloadChapter,
                    onDeleteDownload = onDeleteDownload,
                    onDownloadBatch = onDownloadBatch,
                    onBatchBookmarkChapters = onBatchBookmarkChapters,
                    onBatchMarkChaptersRead = onBatchMarkChaptersRead,
                    onBatchDownloadChapters = onBatchDownloadChapters,
                    onBatchDeleteDownloads = onBatchDeleteDownloads,
                    onOpenChapterSettings = onOpenChapterSettings,
                    onDismissChapterSettings = onDismissChapterSettings,
                    onChapterDisplayModeChange = onChapterDisplayModeChange,
                    onExcludedScanlatorsChange = onExcludedScanlatorsChange,
                    onShowMissingChaptersChange = onShowMissingChaptersChange,
                    onSetChapterSettingsAsDefault = onSetChapterSettingsAsDefault,
                    onResetChapterSettingsToDefault = onResetChapterSettingsToDefault,
                    onOpenReadingSettings = onOpenReadingSettings,
                    onDismissReadingSettings = onDismissReadingSettings,
                    onSaveReadingSettings = onSaveReadingSettings,
                    onClearReadingSettings = onClearReadingSettings,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onChapterBrowseAction = onChapterBrowseAction,
                ),
            )
        }
    }
}
