@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mihon.desktop.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.FrameWindowScope
import mihon.desktop.DesktopRuntime
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.image.LocalImageLoader
import mihon.desktop.navigation.DesktopBackHandler
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.LocalDesktopNavigator
import mihon.desktop.platform.JbrTitleBar
import mihon.desktop.platform.MihonTitleBar
import mihon.desktop.platform.WindowsTitleBar
import mihon.desktop.platform.toColorRef
import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.security.DesktopAppLockGate
import mihon.desktop.security.UnlockResult
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.LocalSnackbarHostState
import mihon.desktop.ui.common.LocalTextInputTracker
import mihon.desktop.ui.common.TextInputTracker
import mihon.desktop.ui.splash.StartupHandoff
import mihon.desktop.updates.AppUpdatePresenter

@Composable
internal fun FrameWindowScope.AppWindowContent(
    app: AppSession,
    ctx: AppShellContext,
    live: ShellLiveState,
    runtime: DesktopRuntime,
    windowController: MainWindowController,
    appUpdatePresenter: AppUpdatePresenter,
    title: String,
    icon: Painter?,
    host: MainWindowHost?,
) {
    val state = app.state
    SideEffect {
        windowController.composeWindow = window
        window.minimumSize = java.awt.Dimension(960, 640)
    }
    val dark = resolveDarkTheme(state.preferences.themeMode)
    val customTitleBar = host?.customTitleBar ?: remember(window) {
        if (mihon.desktop.platform.DesktopTitleBarPolicy.useCustomTitleBar(
                jbrSupported = JbrTitleBar.isSupported,
                userPrefersSystemTitleBar = state.preferences.useSystemTitleBar,
                highContrast = JbrTitleBar.isHighContrast,
            )
        ) {
            JbrTitleBar.install(window, dark)
        } else {
            null
        }
    }
    LaunchedEffect(window, dark) {
        WindowsTitleBar.apply(window, dark)
        customTitleBar?.let { JbrTitleBar.setDark(window, it, dark) }
    }
    MihonDesktopTheme(
        state.preferences.themeMode,
        state.preferences.appTheme,
        state.preferences.themeDarkAmoled,
        topBar = {
            // The title bar sits outside AppProviders, so it needs its own strings provider for the
            // accessibility label of the window-menu icon.
            mihon.desktop.i18n.ProvideDesktopStrings(state.preferences.language) {
                AppTitleBar(windowController, runtime, state, customTitleBar, title, icon)
            }
            val borderColor = MaterialTheme.colorScheme.outlineVariant
            LaunchedEffect(window, borderColor, customTitleBar) {
                WindowsTitleBar.applyBorderColor(window, if (customTitleBar != null) borderColor.toColorRef() else null)
            }
            customTitleBar?.let { titleBar ->
                LaunchedEffect(window, dark) { JbrTitleBar.setDark(window, titleBar, dark) }
            }
        },
    ) {
        AppProviders(app, runtime) { AppLockedContent(app, ctx, live, runtime, windowController, appUpdatePresenter) }
    }
}

@Composable
private fun FrameWindowScope.AppTitleBar(
    controller: MainWindowController,
    runtime: DesktopRuntime,
    state: AppSessionState,
    titleBar: com.jetbrains.WindowDecorations.CustomTitleBar?,
    title: String,
    icon: Painter?,
) {
    val showCaption = mihon.desktop.platform.DesktopTitleBarPolicy.showCaptionStrip(
        customTitleBar = titleBar != null,
        borderlessOrFullscreen = controller.readerWindowMode != ReaderWindowMode.NORMAL,
    )
    if (showCaption && titleBar != null) {
        MihonTitleBar(
            frame = window,
            titleBar = titleBar,
            title = title,
            icon = icon,
            iconDescription = LocalStrings.current.titleBarSystemMenu,
            actions = {
                mihon.desktop.activity.TitleBarActivityCenter(runtime.activityCenter, state.preferences.language)
            },
        )
    }
}

@Composable
private fun AppProviders(app: AppSession, runtime: DesktopRuntime, content: @Composable () -> Unit) {
    val tracker = remember { TextInputTracker { app.textInputFocused.set(it) } }
    ProvideDesktopStrings(app.state.preferences.language) {
        CompositionLocalProvider(
            LocalImageLoader provides runtime.imageLoader,
            LocalCustomCoverManager provides runtime.customCoverManager,
            LocalDesktopNavigator provides app.navigator,
            LocalSnackbarHostState provides app.snackbar,
            LocalSearchFocusRequester provides app.searchFocus,
            LocalTextInputTracker provides tracker,
            content = content,
        )
    }
}

@Composable
private fun AppLockedContent(
    app: AppSession,
    ctx: AppShellContext,
    live: ShellLiveState,
    runtime: DesktopRuntime,
    windowController: MainWindowController,
    updates: AppUpdatePresenter,
) {
    val locked by app.lock.isLocked.collectAsState()
    Box(
        Modifier.fillMaxSize().pointerInput(app.lock) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    app.lock.recordActivity()
                }
            }
        }.navigateBackOnMouseBack(enabled = { !app.lock.isLocked.value }) { navigateShellBack(ctx, app.searchFocus) }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    app.navigator.current == DesktopDestination.Library && event.dragData() is DragData.FilesList
                },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val files = droppedFiles(event)
                            files.forEach { app.openExternalFile(runtime, it) }
                            return files.isNotEmpty()
                        }
                    }
                },
            ),
    ) {
        DesktopAppLockGate(
            isLocked = locked,
            onUnlock = { app.lock.unlock(it) == UnlockResult.Success },
            onForgotPinConfirmed = app.lock::disableLock,
        ) {
            AppDestinationRoute(app, ctx, live, runtime, windowController, updates)
        }
        FirstRunSplashHost(ctx)
    }
}

@Composable
private fun AppDestinationRoute(
    app: AppSession,
    ctx: AppShellContext,
    live: ShellLiveState,
    runtime: DesktopRuntime,
    windowController: MainWindowController,
    updates: AppUpdatePresenter,
) {
    val destination = app.navigator.current
    LaunchedEffect(destination) {
        if (destination is DesktopDestination.Reader) windowController.restoreReaderWindowMode()
        if (destination != DesktopDestination.Library) {
            app.state.isManageCategoriesDialogOpen = false
            app.state.isEditMangaCategoriesDialogOpen = false
        }
    }
    OnlineMangaCacheRetention(destination, live.mangaDetailState, runtime)
    val backupState by app.backup.state.collectAsState()
    if (destination is DesktopDestination.Reader) {
        ReaderRoute(
            destination,
            runtime,
            live.mangaDetailState,
            app.library,
            app.navigator,
            app.scope,
            windowController,
        )
    } else {
        MainShellRoute(ctx, destination, live, updates, app.upcoming, app.updates)
    }
    MangaOrganizationDialogs(ctx, live.mangaDetailState, live.libraryState, live.categories)
    BackupDialogs(ctx, backupState)
    OnboardingHost(ctx)
    MissingSourcesDialog(ctx, backupState)
    ShellOverlays(ctx, remember(runtime) { paletteSourceSearch(runtime) }) { routePaletteTarget(ctx, it) }
}
