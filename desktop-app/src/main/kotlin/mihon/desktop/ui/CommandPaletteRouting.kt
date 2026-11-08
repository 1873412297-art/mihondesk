@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mihon.desktop.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.DesktopRuntime
import mihon.desktop.backup.filterByOptions
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.extension.MissingSourceInfo
import mihon.desktop.extension.MissingSourceResolver
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.extension.getSourceStates
import mihon.desktop.extension.searchManga
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.readerSettingsOverride
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.navigation.LocalDesktopNavigator
import mihon.desktop.platform.ForwardedFileKind
import mihon.desktop.platform.SingleInstanceRequest
import mihon.desktop.platform.toColorRef
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.MangaCacheRetention
import mihon.desktop.reader.ReaderChapterBookmarkStore
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.reader.mangaCacheRetention
import mihon.desktop.reader.window.ReaderWindowController
import mihon.desktop.reader.window.ReaderWindowEscape
import mihon.desktop.security.DesktopAppLockController
import mihon.desktop.security.DesktopAppLockGate
import mihon.desktop.security.UnlockResult
import mihon.desktop.track.toDesktopTrackRecord
import mihon.desktop.track.toTrackingRecord
import mihon.desktop.ui.backup.BackupOptionsDialog
import mihon.desktop.ui.backup.BackupOptionsMode
import mihon.desktop.ui.category.EditMangaCategoriesDialog
import mihon.desktop.ui.category.ManageCategoriesDialog
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalTextInputTracker
import mihon.desktop.ui.common.formatNetworkErrorMessage
import mihon.desktop.ui.library.BackupRestoreDialog
import mihon.desktop.ui.library.BackupRestorePresenter
import mihon.desktop.ui.library.BackupRestoreState
import mihon.desktop.ui.library.ChapterReaderAvailability
import mihon.desktop.ui.library.ImportActionState
import mihon.desktop.ui.library.LibraryImportActions
import mihon.desktop.ui.library.LibraryImportController
import mihon.desktop.ui.library.LibraryPresenter
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.chapterDisplayLabel
import mihon.desktop.ui.onboarding.OnboardingDialog
import mihon.desktop.ui.onboarding.StartAction
import mihon.desktop.ui.reader.DecodedReaderPage
import mihon.desktop.ui.reader.LibraryChapterBookmarkStore
import mihon.desktop.ui.reader.ReaderChapterTransitionChapter
import mihon.desktop.ui.reader.ReaderScreen
import mihon.desktop.ui.reader.readerNavigationChapters
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.shortcut.DesktopShortcutMatcher
import mihon.desktop.ui.shortcut.DesktopShortcutsDialog
import mihon.desktop.ui.shortcut.ShellShortcutAction
import mihon.desktop.ui.shortcut.ShortcutCaptureState
import mihon.desktop.ui.tasks.DownloadGroupAction
import mihon.desktop.ui.track.TrackingDialog
import mihon.desktop.ui.upcoming.UpcomingPresenter
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import mihon.extension.source.model.SManga
import mihon.reader.session.ReaderLoadState
import java.awt.Frame
import java.awt.Toolkit
import java.io.File
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement

/** Sources the command palette may search: enabled, visible and (unless allowed) not NSFW. */
internal fun paletteSourceSearch(runtime: DesktopRuntime) =
    mihon.desktop.ui.palette.PaletteSourceSearch(
        sources = {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val prefs = runtime.preferences.load()
                val installed = runCatching {
                    runtime.extensionInstaller.getInstalledExtensions()
                }.onFailure {
                    mihon.desktop.logging.DesktopLogger.warn(
                        "Palette",
                        "Cannot list installed extensions; hiding sources whose NSFW status is unknown",
                        it,
                    )
                }.getOrNull()
                val sources = runtime.sourceManager.getSourceStates()
                    .filter { it.isEnabled && !it.isLocal }
                    .map { it.source }
                    .filter { source -> source.id !in prefs.hiddenSourceIds }
                // Never guess the NSFW flag: if the installed manifest cannot be read, hide the
                // sources instead of showing ones the user asked to filter out.
                if (installed == null) {
                    emptyList()
                } else {
                    sources.filter { source ->
                        prefs.showNsfwSources || !mihon.desktop.ui.browse.isSourceNsfw(source.id, installed)
                    }
                }
            }
        },
        search = { sourceId, query ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runtime.sourceManager.searchManga(sourceId, 1, query).mangas
            }
        },
    )

internal fun routePaletteTarget(ctx: AppShellContext, target: mihon.desktop.ui.palette.PaletteTarget) {
    val runtime = ctx.runtime
    val session = ctx.session
    val navigator = ctx.navigator
    val libraryPresenter = ctx.libraryPresenter
    val presenterScope = ctx.presenterScope
    when (target) {
        is mihon.desktop.ui.palette.PaletteTarget.Manga -> {
            libraryPresenter.openMangaDetail(target.mangaId)
            navigator.navigate(DesktopDestination.MangaDetails(target.mangaId))
        }
        is mihon.desktop.ui.palette.PaletteTarget.Settings -> {
            session.settingsSectionRequest = target.section
            navigator.navigate(DesktopDestination.Settings)
        }
        is mihon.desktop.ui.palette.PaletteTarget.SourceManga -> {
            ctx.browseRequests.openManga(target.source, target.manga)
            navigator.navigate(DesktopDestination.Browse)
        }
        is mihon.desktop.ui.palette.PaletteTarget.Command -> when (target.command) {
            mihon.desktop.ui.palette.PaletteCommand.OpenLibrary -> navigator.navigate(DesktopDestination.Library)
            mihon.desktop.ui.palette.PaletteCommand.OpenUpdates -> navigator.navigate(DesktopDestination.Updates)
            mihon.desktop.ui.palette.PaletteCommand.OpenHistory -> navigator.navigate(DesktopDestination.History)
            mihon.desktop.ui.palette.PaletteCommand.OpenBrowse -> navigator.navigate(DesktopDestination.Browse)
            mihon.desktop.ui.palette.PaletteCommand.OpenDownloads -> navigator.navigate(
                DesktopDestination.Downloads,
            )
            mihon.desktop.ui.palette.PaletteCommand.OpenStats -> navigator.navigate(DesktopDestination.Stats)
            mihon.desktop.ui.palette.PaletteCommand.UpdateLibrary -> {
                presenterScope.launch { runtime.libraryUpdateScheduler?.triggerUpdateNow() }
            }
            mihon.desktop.ui.palette.PaletteCommand.ShowShortcuts -> session.showShortcutsHelp = true
            mihon.desktop.ui.palette.PaletteCommand.ToggleNavigationRail ->
                ctx.layoutPrefs.requestRailToggle()
        }
    }
}
