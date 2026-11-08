package mihon.desktop.ui.browse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

enum class SourceListingMode {
    Popular,
    Latest,
    Search,
}

data class BrowseSourceUiState(
    val source: SourceDescriptor,
    val mode: SourceListingMode = SourceListingMode.Popular,
    val query: String = "",
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val mangas: List<SManga> = emptyList(),
    val inLibraryUrls: Set<String> = emptySet(),
    /** Cards whose add/remove-library request is still running. */
    val libraryPendingUrls: Set<String> = emptySet(),
    val chapterCounts: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val networkFailure: mihon.extension.ipc.NetworkFailure? = null,
    val filterList: FilterList = FilterList(),
    val defaultFilterList: FilterList? = null,
    val isFilterDialogOpen: Boolean = false,
    val unsupportedMode: SourceListingMode? = null,
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            for (f in filterList) {
                when (f) {
                    is Filter.CheckBox -> if (f.state) count++
                    is Filter.Select<*> -> if (f.state > 0) count++
                    is Filter.Text -> if (f.state.isNotBlank()) count++
                    is Filter.TriState -> if (f.state != Filter.TriState.STATE_IGNORE) count++
                    is Filter.Group<*> -> {
                        for (child in f.state) {
                            if (child is Filter.CheckBox && child.state) count++
                            if (child is Filter.TriState && child.state != Filter.TriState.STATE_IGNORE) count++
                        }
                    }
                    is Filter.Sort -> {
                        if (f.state != null && (f.state!!.index != 0 || f.state!!.ascending)) count++
                    }
                    else -> {}
                }
            }
            return count
        }
}
