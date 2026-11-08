package mihon.desktop.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.tasks.DownloadGroupAction

internal class DownloadActions(
    val onPauseAll: () -> Unit = {},
    val onResumeAll: () -> Unit = {},
    val onClearCompleted: () -> Unit = {},
    val onCancel: (Long) -> Unit = {},
    val onPause: (Long) -> Unit = {},
    val onResume: (Long) -> Unit = {},
    val onMoveUp: (Long) -> Unit = {},
    val onMoveDown: (Long) -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    val onRetryAllFailed: () -> Unit = {},
    val onGroupAction: (Long, DownloadGroupAction) -> Unit = { _, _ -> },
    val onReorder: (Int, Int) -> Unit = { _, _ -> },
    val onReadChapter: (Long, Long) -> Unit = { _, _ -> },
)

internal fun downloadActions(ctx: AppShellContext): DownloadActions {
    val downloader = ctx.runtime.downloader
    return DownloadActions(
        onPauseAll = { downloader?.pause() },
        onResumeAll = { downloader?.resume() },
        onClearCompleted = {
            val cleared = downloader?.clearCompleted().orEmpty()
            if (cleared.isNotEmpty()) {
                ctx.presenterScope.launch {
                    val result = ctx.snackbarHostState.showSnackbar(
                        ctx.strings.text(UiText.CompletedDownloadsCleared),
                        ctx.strings.text(UiText.Undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) downloader?.restoreDownloads(cleared)
                }
            }
        },
        onCancel = { chapterId ->
            val cancelled = downloader?.cancel(chapterId)
            if (cancelled != null) {
                ctx.presenterScope.launch {
                    val result = ctx.snackbarHostState.showSnackbar(
                        ctx.strings.text(UiText.DownloadCancelled),
                        ctx.strings.text(UiText.Undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) downloader.restoreDownloads(listOf(cancelled))
                }
            }
        },
        onPause = { downloader?.pause(it) },
        onResume = { downloader?.resume(it) },
        onMoveUp = { downloader?.moveUp(it) },
        onMoveDown = { downloader?.moveDown(it) },
        onRetry = { downloader?.retry(it) },
        onRetryAllFailed = { downloader?.retryAllFailed() },
        onGroupAction = { mangaId, action ->
            when (action) {
                DownloadGroupAction.PAUSE -> downloader?.pauseManga(mangaId)
                DownloadGroupAction.RESUME -> downloader?.resumeManga(mangaId)
                DownloadGroupAction.RETRY -> downloader?.retryManga(mangaId)
                DownloadGroupAction.CANCEL -> downloader?.cancelManga(mangaId)
                DownloadGroupAction.MOVE_TO_TOP -> downloader?.moveMangaToTop(mangaId)
            }
        },
        onReorder = { from, to -> downloader?.reorder(from, to) },
        onReadChapter = { mangaId, chapterId ->
            ctx.libraryPresenter.openMangaDetail(mangaId)
            ctx.navigator.navigate(mihon.desktop.navigation.DesktopDestination.Reader(chapterId))
        },
    )
}
