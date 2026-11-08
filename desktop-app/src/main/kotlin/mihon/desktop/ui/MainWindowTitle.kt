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
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.readerSettingsOverride
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.navigation.DesktopRoute
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

/** "mihondesk — <manga> — <chapter>" style title of the main window for [destination]. */
internal fun mainWindowTitle(
    destination: DesktopRoute,
    mangaDetailState: mihon.desktop.ui.library.MangaDetailUiState,
    strings: mihon.desktop.i18n.DesktopStrings,
): String {
    val prefix = "mihondesk"
    return when (destination) {
        is DesktopDestination.Reader -> {
            val mTitle = mangaDetailState.manga?.title
            val cTitle = mangaDetailState.allChapters
                .firstOrNull { it.id == destination.chapterId }
                ?.let { chapter ->
                    chapterDisplayLabel(
                        chapter,
                        mangaDetailState.chapterSettings.displayMode,
                        strings,
                    )
                } ?: strings.text(UiText.ChapterNumberLabel, destination.chapterId)
            if (mTitle != null) {
                "$prefix — $mTitle — $cTitle"
            } else {
                "$prefix — $cTitle"
            }
        }
        is DesktopDestination.MangaDetails -> {
            val mTitle = mangaDetailState.manga?.title
            if (mTitle != null) {
                "$prefix — $mTitle"
            } else {
                prefix
            }
        }
        DesktopDestination.Upcoming -> "$prefix — ${strings.text(UiText.Upcoming)}"
        is DesktopDestination -> "$prefix — ${strings.destinationLabel(destination)}"
    }
}
