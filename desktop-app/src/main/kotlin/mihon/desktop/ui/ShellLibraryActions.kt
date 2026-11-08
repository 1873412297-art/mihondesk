package mihon.desktop.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.extension.findSourceDescriptor
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.formatNetworkErrorMessage
import mihon.extension.source.model.SManga

internal fun toggleMangaLibrary(ctx: AppShellContext, live: ShellLiveState): () -> Unit = action@{
    val manga = live.mangaDetailState.manga ?: return@action
    ctx.session.isMangaLibraryActionRunning = true
    val wasFavorite = manga.favorite
    ctx.libraryPresenter.setDetailFavorite(!wasFavorite) { updated ->
        ctx.session.isMangaLibraryActionRunning = false
        ctx.presenterScope.launch {
            if (!updated) {
                ctx.snackbarHostState.showSnackbar("Unable to update library membership")
            } else if (wasFavorite) {
                val result = ctx.snackbarHostState.showSnackbar(
                    ctx.strings.text(UiText.LibraryRemoved),
                    ctx.strings.text(UiText.Undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) ctx.libraryPresenter.setDetailFavorite(true)
            }
        }
    }
}

internal fun refreshMangaSource(ctx: AppShellContext, live: ShellLiveState): () -> Unit = {
    val manga = live.mangaDetailState.manga
    if (manga != null && !ctx.session.isMangaSourceRefreshing) {
        ctx.presenterScope.launch {
            ctx.session.isMangaSourceRefreshing = true
            try {
                val source = ctx.runtime.sourceManager.findSourceDescriptor(manga.sourceId)
                if (source == null || source.isLocalSource()) {
                    ctx.libraryPresenter.retryDetail()
                } else {
                    ctx.runtime.onlineMangaSyncService.prepareOnlineMangaForReading(
                        sourceId = manga.sourceId,
                        manga = SManga(manga.url, manga.title, manga.thumbnailUrl, initialized = false),
                        forceRefresh = true,
                    )
                    ctx.libraryPresenter.retryDetail()
                }
            } catch (error: Exception) {
                ctx.snackbarHostState.showSnackbar(
                    formatNetworkErrorMessage(error.message ?: "Unable to refresh manga", ctx.strings),
                )
            } finally {
                ctx.session.isMangaSourceRefreshing = false
            }
        }
    }
}

internal fun openChapterWebView(ctx: AppShellContext, live: ShellLiveState): (Long) -> Unit = { chapterId ->
    val manga = live.mangaDetailState.manga
    val chapter = live.mangaDetailState.allChapters.firstOrNull { it.id == chapterId }
    val base = manga?.let { live.sourceBaseUrls[it.sourceId] }
    val url = when {
        chapter == null -> null
        chapter.url.startsWith("http") -> chapter.url
        base != null -> base + chapter.url
        else -> null
    }
    val source = manga?.let { ctx.runtime.sourceManager.findSourceDescriptor(it.sourceId) }
    if (source == null || url == null) {
        ctx.presenterScope.launch {
            ctx.snackbarHostState.showSnackbar(ctx.strings.text(UiText.SourceWebPageUnavailable))
        }
    } else {
        ctx.session.chapterWebViewRequest = source to url
    }
}

internal fun deleteHistoryItem(ctx: AppShellContext, live: ShellLiveState): (Long) -> Unit = { chapterId ->
    val item = live.historyState.groups.flatMap { it.items }.find { it.chapterId == chapterId }
    ctx.runtime.historyService?.deleteItem(chapterId)
    ctx.presenterScope.launch {
        val result = ctx.snackbarHostState.showSnackbar(
            ctx.strings.text(UiText.HistoryItemDeleted),
            ctx.strings.text(UiText.Undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && item != null) ctx.runtime.historyService?.restoreItem(item)
    }
}
