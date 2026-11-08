package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.platform.DesktopBrowserHelper
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSnackbarHostState

/** The detail header action row: back/close pinned, the other actions overflowing into a ⋯ menu. */
@Composable
internal fun MangaDetailActionRow(
    manga: MangaDetails,
    strings: DesktopStrings,
    sourceBaseUrl: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: (() -> Unit)?,
    isLibraryActionRunning: Boolean,
    isRefreshingSource: Boolean,
    onToggleLibrary: (() -> Unit)?,
    onRefreshSource: (() -> Unit)?,
    onEditInfo: () -> Unit,
    onEditCategories: () -> Unit,
    onOpenTracking: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val shareableLink = remember(manga.url, sourceBaseUrl) {
        when {
            manga.url.startsWith("http") -> manga.url
            sourceBaseUrl != null -> sourceBaseUrl + manga.url
            else -> null
        }
    }
    val copyLink: () -> Unit = {
        shareableLink?.let { link ->
            clipboardManager.setText(AnnotatedString(link))
            coroutineScope.launch {
                snackbarHostState?.showSnackbar(strings.mangaDetailLinkCopied)
            }
        }
    }
    val openBrowser: () -> Unit = {
        DesktopBrowserHelper.openInBrowser(manga.url)
    }
    DetailActionRowLayout(
        actions = detailRowActions(
            manga = manga,
            strings = strings,
            shareableLink = shareableLink,
            copyLink = copyLink,
            openBrowser = openBrowser,
            isLibraryActionRunning = isLibraryActionRunning,
            isRefreshingSource = isRefreshingSource,
            onToggleLibrary = onToggleLibrary,
            onRefreshSource = onRefreshSource,
            onEditInfo = onEditInfo,
            onEditCategories = onEditCategories,
            onOpenTracking = onOpenTracking,
        ),
        strings = strings,
        showBack = showBack,
        onBack = onBack,
        onClose = onClose,
    )
}

private fun detailRowActions(
    manga: MangaDetails,
    strings: DesktopStrings,
    shareableLink: String?,
    copyLink: () -> Unit,
    openBrowser: () -> Unit,
    isLibraryActionRunning: Boolean,
    isRefreshingSource: Boolean,
    onToggleLibrary: (() -> Unit)?,
    onRefreshSource: (() -> Unit)?,
    onEditInfo: () -> Unit,
    onEditCategories: () -> Unit,
    onOpenTracking: () -> Unit,
): List<OverflowAction> = buildList {
    libraryRowAction(strings, manga.favorite, isLibraryActionRunning, onToggleLibrary)?.let(::add)
    refreshRowAction(strings, isRefreshingSource, onRefreshSource)?.let(::add)
    add(editInfoRowAction(strings, onEditInfo))
    add(categoriesRowAction(strings, onEditCategories))
    if (manga.url.startsWith("http")) add(openBrowserRowAction(strings, openBrowser))
    if (shareableLink != null) add(copyLinkRowAction(strings, copyLink))
    add(trackingRowAction(strings, onOpenTracking))
}

private fun libraryRowAction(
    strings: DesktopStrings,
    favorite: Boolean,
    running: Boolean,
    onClick: (() -> Unit)?,
): OverflowAction? = onClick?.let { toggle ->
    val icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
    val label = if (favorite) strings.mangaDetailInLibrary else strings.mangaDetailAddToLibrary
    OverflowAction("library", 6, label, icon, !running, toggle) {
        DetailActionButton(label, icon, toggle, "manga-detail-library-button", enabled = !running)
    }
}

private fun refreshRowAction(
    strings: DesktopStrings,
    refreshing: Boolean,
    onClick: (() -> Unit)?,
): OverflowAction? = onClick?.let { refresh ->
    OverflowAction("refresh", 5, strings.browseRefresh, Icons.Rounded.Refresh, !refreshing, refresh) {
        DetailActionButton(
            strings.browseRefresh,
            Icons.Rounded.Refresh,
            refresh,
            "manga-detail-refresh-button",
            enabled = !refreshing,
        )
    }
}

private fun editInfoRowAction(strings: DesktopStrings, onClick: () -> Unit) = OverflowAction(
    "edit-info",
    0,
    strings.mangaDetailEditInfo,
    Icons.Rounded.Edit,
    onClick = onClick,
) {
    DetailActionButton(strings.mangaDetailEditInfo, Icons.Rounded.Edit, onClick, "manga-detail-edit-info-button")
}

private fun categoriesRowAction(strings: DesktopStrings, onClick: () -> Unit) = OverflowAction(
    "categories",
    4,
    strings.mangaDetailCategories,
    Icons.AutoMirrored.Rounded.Label,
    onClick = onClick,
) {
    DetailActionButton(
        strings.mangaDetailCategories,
        Icons.AutoMirrored.Rounded.Label,
        onClick,
        "manga-detail-edit-categories-button",
    )
}

private fun openBrowserRowAction(strings: DesktopStrings, onClick: () -> Unit) = OverflowAction(
    "open-browser",
    3,
    strings.openInBrowser,
    Icons.AutoMirrored.Rounded.OpenInNew,
    onClick = onClick,
) {
    DetailActionButton(
        strings.openInBrowser,
        Icons.AutoMirrored.Rounded.OpenInNew,
        onClick,
        "manga-detail-open-browser-button",
    )
}

private fun copyLinkRowAction(strings: DesktopStrings, onClick: () -> Unit) = OverflowAction(
    "copy-link",
    2,
    strings.mangaDetailCopyLink,
    Icons.Rounded.ContentCopy,
    onClick = onClick,
) {
    DetailActionButton(
        strings.mangaDetailCopyLink,
        Icons.Rounded.ContentCopy,
        onClick,
        "manga-detail-copy-link-button",
    )
}

private fun trackingRowAction(strings: DesktopStrings, onClick: () -> Unit) = OverflowAction(
    "tracking",
    1,
    strings.mangaDetailTracking,
    Icons.Rounded.Sync,
    onClick = onClick,
) {
    DetailActionButton(
        strings.mangaDetailTracking,
        Icons.Rounded.Sync,
        onClick,
        "manga-detail-open-tracking-button",
        tonal = true,
    )
}

@Composable
private fun DetailActionRowLayout(
    actions: List<OverflowAction>,
    strings: DesktopStrings,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: (() -> Unit)?,
) {
    OverflowActionRow(
        actions = actions,
        moreLabel = strings.text(UiText.More),
        modifier = Modifier.fillMaxWidth().testTag("manga-detail-action-row"),
        leading = if (showBack) {
            {
                TextButton(onClick = onBack, modifier = Modifier.testTag("manga-detail-back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.mangaDetailBack)
                }
            }
        } else {
            null
        },
        trailing = onClose?.let { close ->
            {
                DesktopTooltipBox(tooltip = strings.dialogClose) {
                    IconButton(onClick = close, modifier = Modifier.testTag("library-detail-close")) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.dialogClose)
                    }
                }
            }
        },
    )
}
