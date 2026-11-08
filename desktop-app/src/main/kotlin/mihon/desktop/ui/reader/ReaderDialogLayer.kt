package mihon.desktop.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.ui.shortcut.DesktopKeyBindingStore
import mihon.desktop.ui.shortcut.DesktopKeyBindings

/** Chapter transition, page actions and shortcut dialogs layered above the reader. */
@Composable
internal fun ReaderDialogLayer(
    transition: ReaderChapterTransition?,
    transitionDismissable: Boolean,
    onContinueTransition: (ReaderChapterTransition) -> Unit,
    onDismissTransition: () -> Unit,
    settings: DesktopReaderSettings,
    pageActionsTarget: ReaderPageActionTarget?,
    onRunPageAction: (
        ReaderPageActionTarget,
        suspend (ReaderPageActionHandler, ReaderPageActionTarget) -> Boolean,
    ) -> Unit,
    onDismissPageActions: () -> Unit,
    showShortcuts: Boolean,
    onDismissShortcuts: () -> Unit,
    keyBindingStore: DesktopKeyBindingStore?,
) {
    transition?.let { current ->
        ReaderChapterTransitionSurface(
            transition = current,
            settings = settings,
            allowDismiss = transitionDismissable,
            onContinue = { onContinueTransition(current) },
            onDismiss = onDismissTransition,
        )
    }
    pageActionsTarget?.let { target ->
        ReaderPageActionsDialog(
            onDismissRequest = onDismissPageActions,
            canSetAsCover = target.mangaId != null,
            canOpenInBrowser = target.canOpenInBrowser,
            onSave = {
                onRunPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@onRunPageAction false
                    handler.saveImage(pageTarget, image)
                }
            },
            onCopy = {
                onRunPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@onRunPageAction false
                    handler.copyImage(image)
                }
            },
            onShare = {
                onRunPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@onRunPageAction false
                    handler.shareImage(image)
                }
            },
            onSetAsCover = {
                onRunPageAction(target) { handler, pageTarget ->
                    val image = handler.loadImage(pageTarget) ?: return@onRunPageAction false
                    handler.setAsCover(pageTarget, image)
                }
            },
            onOpenInBrowser = {
                onRunPageAction(target) { handler, pageTarget ->
                    pageTarget.pageUrl?.let(handler::openInBrowser) ?: false
                }
            },
        )
    }
    if (showShortcuts) {
        ReaderShortcutsDialog(
            onDismiss = onDismissShortcuts,
            bindings = remember(keyBindingStore) {
                keyBindingStore?.load()?.reader ?: DesktopKeyBindings.defaultReader
            },
        )
    }
}
