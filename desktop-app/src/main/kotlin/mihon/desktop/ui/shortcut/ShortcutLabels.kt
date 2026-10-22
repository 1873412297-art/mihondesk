package mihon.desktop.ui.shortcut

import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.reader.input.ReaderShortcutAction

/** The destination each Ctrl+N shell shortcut opens; the shell handler and the help dialogs share it. */
fun ShellShortcutAction.destination(): DesktopDestination? = when (this) {
    ShellShortcutAction.NavigateDestination1 -> DesktopDestination.Library
    ShellShortcutAction.NavigateDestination2 -> DesktopDestination.Updates
    ShellShortcutAction.NavigateDestination3 -> DesktopDestination.History
    ShellShortcutAction.NavigateDestination4 -> DesktopDestination.Browse
    ShellShortcutAction.NavigateDestination5 -> DesktopDestination.Downloads
    ShellShortcutAction.NavigateDestination6 -> DesktopDestination.Stats
    ShellShortcutAction.NavigateDestination7 -> DesktopDestination.Settings
    ShellShortcutAction.NavigateDestination8 -> DesktopDestination.About
    else -> null
}

fun DesktopStrings.shortcutLabel(action: ShellShortcutAction): String {
    action.destination()?.let { return text(UiText.ShortcutGoTo, destinationLabel(it)) }
    return when (action) {
        ShellShortcutAction.FocusSearch -> text(UiText.ShortcutFocusSearch)
        ShellShortcutAction.RefreshCurrentPage -> text(UiText.ShortcutRefreshPage)
        ShellShortcutAction.NavigateBack -> text(UiText.ShortcutBack)
        ShellShortcutAction.ShowShortcutsHelp -> text(UiText.ShortcutShowHelp)
        ShellShortcutAction.ToggleNavigationRail -> text(UiText.ShortcutToggleNavigationRail)
        ShellShortcutAction.OpenCommandPalette -> text(UiText.ShortcutCommandPalette)
        else -> action.name
    }
}

fun DesktopStrings.shortcutLabel(action: ReaderShortcutAction): String = text(
    when (action) {
        ReaderShortcutAction.PAGE_LEFT -> UiText.ReaderShortcutPageLeft
        ReaderShortcutAction.PAGE_RIGHT -> UiText.ReaderShortcutPageRight
        ReaderShortcutAction.PREVIOUS_PAGE -> UiText.ReaderShortcutPreviousPage
        ReaderShortcutAction.NEXT_PAGE -> UiText.ReaderShortcutNextPage
        ReaderShortcutAction.FIRST_PAGE -> UiText.ReaderShortcutFirstPage
        ReaderShortcutAction.LAST_PAGE -> UiText.ReaderShortcutLastPage
        ReaderShortcutAction.ZOOM_IN -> UiText.ReaderShortcutZoomIn
        ReaderShortcutAction.ZOOM_OUT -> UiText.ReaderShortcutZoomOut
        ReaderShortcutAction.RESET_ZOOM -> UiText.ResetZoomFull
        ReaderShortcutAction.FULLSCREEN -> UiText.ToggleFullscreen
        ReaderShortcutAction.BORDERLESS -> UiText.ToggleBorderless
        ReaderShortcutAction.EXIT -> UiText.ExitReader
        ReaderShortcutAction.PAGE_ACTIONS -> UiText.ReaderShortcutPageActions
        ReaderShortcutAction.PREVIOUS_CHAPTER -> UiText.ReaderShortcutPreviousChapter
        ReaderShortcutAction.NEXT_CHAPTER -> UiText.ReaderShortcutNextChapter
        ReaderShortcutAction.TOGGLE_BOOKMARK -> UiText.ToggleChapterBookmarkShortcut
        ReaderShortcutAction.CYCLE_READING_MODE -> UiText.CycleReadingModeShortcut
        ReaderShortcutAction.SHOW_HELP -> UiText.ShowShortcuts
        ReaderShortcutAction.TOGGLE_AUTO_SCROLL -> UiText.AutoScrollToggle
        ReaderShortcutAction.AUTO_SCROLL_FASTER -> UiText.AutoScrollFaster
        ReaderShortcutAction.AUTO_SCROLL_SLOWER -> UiText.AutoScrollSlower
    },
)

/** All chords for an action, e.g. `← / A`, or the "not set" label. */
fun DesktopStrings.chordsLabel(chords: List<KeyChord>): String =
    if (chords.isEmpty()) text(UiText.ShortcutUnbound) else chords.joinToString(" / ") { it.displayLabel() }
