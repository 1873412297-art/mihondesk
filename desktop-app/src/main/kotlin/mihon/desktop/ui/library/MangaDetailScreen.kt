package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.ui.common.MangaBackdropBanner
import mihon.desktop.ui.common.formatNetworkErrorMessage

/**
 * Shared manga detail page. [actions] carries every mutation; the remaining parameters are the
 * entry-specific parts (navigation, source refresh, library membership) that differ per host.
 */
@Composable
fun MangaDetailScreen(
    state: MangaDetailUiState,
    onBack: () -> Unit,
    actions: MangaDetailActions = MangaDetailActions(),
    onRetry: () -> Unit = {},
    onToggleLibrary: (() -> Unit)? = null,
    onRefreshSource: (() -> Unit)? = null,
    isLibraryActionRunning: Boolean = false,
    isRefreshingSource: Boolean = false,
    modifier: Modifier = Modifier,
    sourceBaseUrl: String? = null,
    showBack: Boolean = true,
    /** Shown as a ✕ at the right end of the action row (side pane). */
    onClose: (() -> Unit)? = null,
    onOpenChapterInWebView: ((Long) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier.testTag("manga-detail-pane"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.loading -> DetailLoading(showBack, onBack)
            state.errorMessage != null -> DetailMessage(
                message = formatNetworkErrorMessage(state.errorMessage, strings),
                tag = "manga-detail-error",
                onBack = onBack,
                showBack = showBack,
                onRetry = onRetry,
            )
            state.manga == null -> DetailMessage(
                message = strings.text(UiText.MangaRemoved),
                tag = "manga-detail-missing",
                onBack = onBack,
                showBack = showBack,
                onRetry = null,
            )
            else -> MangaDetailLoaded(
                manga = state.manga,
                state = state,
                entry = MangaDetailEntry(
                    sourceBaseUrl = sourceBaseUrl,
                    showBack = showBack,
                    onBack = onBack,
                    onClose = onClose,
                    onToggleLibrary = onToggleLibrary,
                    onRefreshSource = onRefreshSource,
                    isLibraryActionRunning = isLibraryActionRunning,
                    isRefreshingSource = isRefreshingSource,
                    onOpenChapterInWebView = onOpenChapterInWebView,
                ),
                actions = actions,
            )
        }
    }
}
