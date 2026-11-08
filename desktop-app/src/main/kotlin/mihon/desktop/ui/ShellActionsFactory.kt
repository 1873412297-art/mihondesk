package mihon.desktop.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.updates.AppUpdatePresenter

@Composable
internal fun shellActions(
    ctx: AppShellContext,
    live: ShellLiveState,
    appUpdates: AppUpdatePresenter,
    upcoming: UpcomingPresenter,
    overrides: UpdatesRowOverrides,
): DesktopShellActions = DesktopShellActions(
    library = libraryActions(ctx, live),
    appUpdateContent = {
        mihon.desktop.ui.settings.AppUpdatePanel(appUpdates, ctx.runtime.appUpdateService)
    },
    upcomingContent = upcomingContent(ctx, upcoming),
    browseContent = browseContent(ctx, live),
    mangaDetailActions = mangaDetailActions(
        runtime = ctx.runtime,
        libraryPresenter = ctx.libraryPresenter,
        mangaDetailState = live.mangaDetailState,
        session = ctx.session,
        navigator = ctx.navigator,
        presenterScope = ctx.presenterScope,
        snackbarHostState = ctx.snackbarHostState,
        strings = LocalStrings.current,
    ),
    onDestinationSelected = ctx.navigator::navigate,
    onImportBackup = ctx.startImportBackup,
    onImportLocal = ctx.startImportLocal,
    onPairPhoneSync = ctx.startPairPhoneSync,
    onBackFromMangaDetail = {
        ctx.libraryPresenter.selectManga(null)
        if (ctx.navigator.current is DesktopDestination.MangaDetails) ctx.navigator.back()
    },
    onReadChapter = { ctx.navigator.navigate(DesktopDestination.Reader(it)) },
    onContinueReading = { mangaId ->
        ctx.libraryPresenter.continueReading(mangaId) { ctx.navigator.navigate(DesktopDestination.Reader(it)) }
    },
    onOpenMangaFromHome = { mangaId ->
        ctx.libraryPresenter.openMangaDetail(mangaId)
        ctx.navigator.navigate(DesktopDestination.MangaDetails(mangaId))
    },
    onToggleMangaLibrary = toggleMangaLibrary(ctx, live),
    onRefreshMangaSource = refreshMangaSource(ctx, live),
    onOpenRandomManga = openRandomManga(ctx, live),
    onOpenChapterInWebView = openChapterWebView(ctx, live),
    downloadActions = downloadActions(ctx),
).withLibraryUpdateActions(ctx, overrides).withSettingsActions(ctx, live)

private fun DesktopShellActions.withLibraryUpdateActions(
    ctx: AppShellContext,
    overrides: UpdatesRowOverrides,
): DesktopShellActions = copy(
    onCheckForUpdates = { ctx.presenterScope.launch { ctx.runtime.libraryUpdateScheduler?.triggerUpdateNow() } },
    onUpdateLibrary = { ctx.presenterScope.launch { ctx.runtime.libraryUpdateScheduler?.triggerUpdateNow() } },
    onCancelLibraryUpdate = { ctx.runtime.libraryUpdateScheduler?.cancelUpdate() },
    onMarkUpdatesChapterRead = { mangaId, chapterId ->
        overrides.markRead(chapterId)
        ctx.libraryPresenter.markChapterRead(mangaId, chapterId)
    },
    onDownloadUpdatesChapter = { mangaId, chapterId ->
        overrides.markDownloaded(chapterId)
        ctx.libraryPresenter.enqueueChapterDownload(mangaId, chapterId)
    },
    onOpenUpcoming = { ctx.navigator.navigate(DesktopDestination.Upcoming) },
    onCloseUpcoming = { ctx.navigator.back() },
    onRepairBrokenCovers = ctx.libraryPresenter::repairBrokenCovers,
    onDuplicateOpenManga = ctx.libraryPresenter::openDuplicateManga,
    onDuplicateMigrate = ctx.libraryPresenter::migrateDuplicateTo,
    onDuplicateAddAnyway = ctx.libraryPresenter::addDuplicateAnyway,
    onDuplicateDismiss = ctx.libraryPresenter::dismissDuplicateDialog,
)

private fun DesktopShellActions.withSettingsActions(
    ctx: AppShellContext,
    live: ShellLiveState,
): DesktopShellActions = copy(
    onPreferencesChanged = { updatePreferences(ctx, it) },
    onContentDensityChange = ctx.layoutPrefs::updateContentDensity,
    onRailCollapsedChange = ctx.layoutPrefs::updateRailCollapsed,
    onToggleIncognito = {
        ctx.session.preferences =
            ctx.runtime.preferences.updatePreferences { it.copy(incognitoMode = !it.incognitoMode) }
    },
    onOpenOnboarding = {
        mihon.desktop.ui.splash.FirstRunSplash.reset(ctx.runtime.preferences)
        ctx.session.showFirstRunSplash = true
        ctx.session.showOnboarding = true
    },
    onExportBackup = exportBackup(ctx),
    onRefreshStats = { ctx.presenterScope.launch { ctx.runtime.statsService?.refresh() } },
    onLockNow = if (ctx.session.preferences.appLockEnabled) ctx.appLockController::lockNow else null,
    onHistoryQueryChange = { ctx.runtime.historyService?.setQuery(it) },
    onDeleteHistoryItem = deleteHistoryItem(ctx, live),
    onClearAllHistory = { ctx.runtime.historyService?.clearAll() },
)

private fun updatePreferences(ctx: AppShellContext, preferences: DesktopPreferences) {
    ctx.session.preferences = preferences
    ctx.appLockController.refresh()
}

internal fun removeLibrarySelectionAction(ctx: AppShellContext, live: ShellLiveState): () -> Unit = {
    val selected = live.libraryState.selectionState.selectedMangaIds.toSet()
    ctx.libraryPresenter.batchRemoveFromLibrary { removed ->
        val ids = if (removed.isNotEmpty()) removed else selected
        ctx.presenterScope.launch {
            val result = ctx.snackbarHostState.showSnackbar(
                message = ctx.strings.text(UiText.LibraryRemoved),
                actionLabel = ctx.strings.text(UiText.Undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) ctx.libraryPresenter.restoreLibraryManga(ids)
        }
    }
}

private fun openRandomManga(ctx: AppShellContext, live: ShellLiveState): () -> Unit = {
    val manga = live.libraryState.items.randomOrNull()
    if (manga != null) {
        ctx.libraryPresenter.selectManga(manga.id)
    } else {
        ctx.presenterScope.launch { ctx.snackbarHostState.showSnackbar(ctx.strings.text(UiText.EmptyLibraryRandom)) }
    }
}

private fun exportBackup(ctx: AppShellContext): () -> Unit = {
    ctx.presenterScope.launch {
        val path = mihon.desktop.ui.library.chooseExportBackup(ctx.strings.text(UiText.ExportBackup)) ?: return@launch
        ctx.session.pendingExportPath = path
    }
}
