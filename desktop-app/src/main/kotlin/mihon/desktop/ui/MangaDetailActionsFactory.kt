package mihon.desktop.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.ui.common.formatNetworkErrorMessage
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState

/** Detail callbacks shared by library and Browse, including consistent undo recovery. */
internal fun mangaDetailActions(
    runtime: DesktopRuntime,
    libraryPresenter: LibraryPresenter,
    mangaDetailState: MangaDetailUiState,
    session: AppSessionState,
    navigator: DesktopNavigator,
    presenterScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    strings: DesktopStrings,
): MangaDetailActions = MangaDetailActions(
    onReadChapter = { navigator.navigate(DesktopDestination.Reader(it)) },
    onInstallMissingSource = {
        installMissingSource(runtime, libraryPresenter, presenterScope, snackbarHostState, strings, it)
    },
    onIgnoreMissingSource = {
        mangaDetailState.manga?.let { libraryPresenter.ignoreMissingSource(it.id) }
    },
    onEditCategories = { requestOrganization(session, mangaDetailState, MangaOrganizationAction.Categories) },
    onOpenTracking = { requestOrganization(session, mangaDetailState, MangaOrganizationAction.Tracking) },
    onEditInfo = { libraryPresenter.setEditInfoDialogOpen(true) },
    onDismissEditInfo = { libraryPresenter.setEditInfoDialogOpen(false) },
    onSaveMangaInfo = libraryPresenter::updateSelectedMangaInfo,
    onResetMangaInfo = libraryPresenter::resetSelectedMangaInfo,
    onChapterFilterChange = libraryPresenter::setChapterFilter,
    onChapterSortChange = libraryPresenter::setChapterSort,
    onToggleBookmark = libraryPresenter::toggleChapterBookmark,
    onToggleRead = libraryPresenter::toggleChapterRead,
    onMarkPreviousRead = libraryPresenter::markPreviousChaptersRead,
    onDownloadChapter = libraryPresenter::downloadChapter,
    onDeleteDownload = { deleteDownload(libraryPresenter, presenterScope, snackbarHostState, strings, it) },
    onDownloadBatch = { amount ->
        if (amount == -1) {
            libraryPresenter.downloadNextChapters(null, unreadOnly = false)
        } else {
            libraryPresenter.downloadNextChapters(amount, unreadOnly = true)
        }
    },
    onBatchBookmarkChapters = libraryPresenter::batchBookmarkChapters,
    onBatchMarkChaptersRead = libraryPresenter::batchMarkChaptersRead,
    onBatchDownloadChapters = libraryPresenter::batchDownloadChapters,
    onBatchDeleteDownloads = { deleteDownloads(libraryPresenter, presenterScope, snackbarHostState, strings, it) },
    onCoverLoadFailed = libraryPresenter::onCoverLoadFailed,
).withChapterSettings(libraryPresenter)

private fun MangaDetailActions.withChapterSettings(presenter: LibraryPresenter): MangaDetailActions = copy(
    onOpenChapterSettings = { presenter.setChapterSettingsDialogOpen(true) },
    onDismissChapterSettings = { presenter.setChapterSettingsDialogOpen(false) },
    onChapterDisplayModeChange = presenter::setChapterDisplayMode,
    onExcludedScanlatorsChange = presenter::setExcludedScanlators,
    onShowMissingChaptersChange = presenter::setShowMissingChapters,
    onSetChapterSettingsAsDefault = presenter::setChapterSettingsAsDefault,
    onResetChapterSettingsToDefault = presenter::resetChapterSettingsToDefault,
    onOpenReadingSettings = { presenter.setReadingSettingsDialogOpen(true) },
    onDismissReadingSettings = { presenter.setReadingSettingsDialogOpen(false) },
    onSaveReadingSettings = presenter::saveReadingSettingsOverride,
    onClearReadingSettings = presenter::clearReadingSettingsOverride,
)

private fun installMissingSource(
    runtime: DesktopRuntime,
    presenter: LibraryPresenter,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    strings: DesktopStrings,
    extension: ExtensionStoreItem,
) {
    scope.launch {
        try {
            runtime.extensionInstaller.downloadAndInstall(
                downloadUrl = extension.downloadUrl,
                expectedSha256 = extension.sha256,
                repoUrl = extension.repoUrl,
                storeItem = extension,
            )
            presenter.refreshDetails()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            snackbar.showSnackbar(formatNetworkErrorMessage(error.message ?: "Failed to install extension", strings))
        }
    }
}

private fun requestOrganization(session: AppSessionState, detail: MangaDetailUiState, action: MangaOrganizationAction) {
    if (detail.manga?.favorite != true) {
        session.pendingMangaOrganizationAction = action
    } else if (action == MangaOrganizationAction.Categories) {
        session.isEditMangaCategoriesDialogOpen = true
    } else {
        session.isTrackingDialogOpen = true
    }
}

private fun deleteDownload(
    presenter: LibraryPresenter,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    strings: DesktopStrings,
    id: Long,
) {
    presenter.deleteChapterDownload(id)
    undoDownloadDelete(presenter, scope, snackbar, strings, setOf(id))
}

private fun deleteDownloads(
    presenter: LibraryPresenter,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    strings: DesktopStrings,
    ids: Set<Long>,
) {
    presenter.batchDeleteChapterDownloads(ids)
    undoDownloadDelete(presenter, scope, snackbar, strings, ids)
}

private fun undoDownloadDelete(
    presenter: LibraryPresenter,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    strings: DesktopStrings,
    ids: Set<Long>,
) {
    scope.launch {
        val result = snackbar.showSnackbar(
            strings.text(UiText.DownloadDeleted),
            strings.text(UiText.Undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) ids.forEach(presenter::downloadChapter)
    }
}
