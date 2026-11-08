package mihon.desktop.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.image.LocalImageLoader
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.ui.common.coverHeaders

/** The dialog stack of the manga detail pane. */
@Composable
internal fun MangaDetailDialogs(
    manga: MangaDetails,
    strings: DesktopStrings,
    state: MangaDetailUiState,
    sourceBaseUrl: String?,
    hasCustomCover: Boolean,
    isCoverDialogOpen: Boolean,
    isNotesDialogOpen: Boolean,
    onDismissCover: () -> Unit,
    onDismissNotes: () -> Unit,
    onCoverChanged: () -> Unit,
    onSaveMangaInfo: (
        String,
        String?,
        String?,
        String?,
        List<String>,
        Long,
        String,
    ) -> Unit,
    onDismissEditInfo: () -> Unit,
    onResetMangaInfo: () -> Unit,
    onDismissChapterSettings: () -> Unit,
    onChapterSortChange: (ChapterSortState) -> Unit,
    onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit,
    onShowMissingChaptersChange: (Boolean) -> Unit,
    onExcludedScanlatorsChange: (Set<String>) -> Unit,
    onSetChapterSettingsAsDefault: (Boolean) -> Unit,
    onResetChapterSettingsToDefault: () -> Unit,
    onDismissReadingSettings: () -> Unit,
    onSaveReadingSettings: (MangaReaderSettingsOverride) -> Unit,
    onClearReadingSettings: () -> Unit,
) {
    if (isCoverDialogOpen) {
        CoverDialog(manga, strings, sourceBaseUrl, hasCustomCover, onDismissCover, onCoverChanged)
    }
    if (isNotesDialogOpen) {
        NotesDialog(manga, onDismissNotes, onSaveMangaInfo)
    }
    if (state.isEditInfoDialogOpen) {
        EditMangaInfoDialog(
            manga = manga,
            onDismissRequest = onDismissEditInfo,
            onSave = onSaveMangaInfo,
            onResetToSource = onResetMangaInfo,
        )
    }
    if (state.isChapterSettingsDialogOpen) {
        ChapterSettingsDialog(
            settings = state.chapterSettings,
            availableScanlators = state.availableScanlators,
            onDismissRequest = onDismissChapterSettings,
            onDisplayModeChange = onChapterDisplayModeChange,
            onSortModeChange = { mode, ascending ->
                onChapterSortChange(ChapterSortState(mode = mode, ascending = ascending))
            },
            onShowMissingChaptersChange = onShowMissingChaptersChange,
            onExcludedScanlatorsChange = onExcludedScanlatorsChange,
            onSetAsDefault = onSetChapterSettingsAsDefault,
            onResetToDefault = onResetChapterSettingsToDefault,
        )
    }
    if (state.isReadingSettingsDialogOpen) {
        ReadingSettingsOverrideDialog(
            currentOverride = state.readingSettingsOverride,
            onDismissRequest = onDismissReadingSettings,
            onSave = onSaveReadingSettings,
            onClear = onClearReadingSettings,
        )
    }
}

@Composable
private fun CoverDialog(
    manga: MangaDetails,
    strings: DesktopStrings,
    sourceBaseUrl: String?,
    hasCustomCover: Boolean,
    onDismiss: () -> Unit,
    onCoverChanged: () -> Unit,
) {
    val customCoverManager = LocalCustomCoverManager.current
    val imageLoader = LocalImageLoader.current
    MangaCoverDialog(
        mangaTitle = manga.title,
        thumbnailUrl = manga.thumbnailUrl,
        mangaId = manga.id,
        isCustomCover = hasCustomCover,
        headers = coverHeaders(sourceBaseUrl, manga.thumbnailUrl),
        onDismissRequest = onDismiss,
        onChangeCover = {
            val picked = chooseCoverImage(strings.text(UiText.ChooseCover))
            if (picked != null) {
                customCoverManager?.setCustomCover(manga.id, picked)
                imageLoader?.clearMemoryCache()
                onCoverChanged()
            }
        },
        onResetCover = {
            customCoverManager?.removeCustomCover(manga.id)
            imageLoader?.clearMemoryCache()
            onCoverChanged()
        },
        coverFileProvider = {
            imageLoader?.getCoverFile(manga.id, manga.thumbnailUrl)
        },
    )
}

@Composable
private fun NotesDialog(
    manga: MangaDetails,
    onDismiss: () -> Unit,
    onSaveMangaInfo: (String, String?, String?, String?, List<String>, Long, String) -> Unit,
) {
    MangaNotesDialog(
        mangaTitle = manga.title,
        initialNotes = manga.notes,
        onDismissRequest = onDismiss,
        onSaveNotes = { newNotes ->
            onSaveMangaInfo(
                manga.title,
                manga.author,
                manga.artist,
                manga.description,
                decodeGenres(manga.genreJson),
                manga.status,
                newNotes,
            )
        },
    )
}
