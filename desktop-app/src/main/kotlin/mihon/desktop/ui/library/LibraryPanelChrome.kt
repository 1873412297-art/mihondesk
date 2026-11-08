package mihon.desktop.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val DETAIL_PANE_GAP = 16.dp

internal val DETAIL_PANE_MIN_WIDTH = 360.dp

internal val DETAIL_PANE_CLOSE_SLACK = 80.dp
internal const val DETAIL_PANE_DEFAULT_FRACTION = 0.45f
internal const val DETAIL_PANE_MAX_FRACTION = 0.7f

private val LIST_MODE_MIN_GRID_WIDTH = 360.dp

/** Grid/list scroll positions shared by every library layout, so opening or closing details keeps them. */
internal class LibraryScrollStates {
    val grid = androidx.compose.foundation.lazy.grid.LazyGridState()
    val list = androidx.compose.foundation.lazy.LazyListState()
}

internal val LocalLibraryScrollStates =
    androidx.compose.runtime.staticCompositionLocalOf<LibraryScrollStates?> { null }

internal fun gridFitsTwoColumns(
    mode: LibraryDisplayMode,
    gridWidth: androidx.compose.ui.unit.Dp,
    gridSize: Float,
    spacing: androidx.compose.ui.unit.Dp,
): Boolean = if (mode == LibraryDisplayMode.List) {
    gridWidth >= LIST_MODE_MIN_GRID_WIDTH
} else {
    gridWidth > 0.dp && calculateLibraryGridMetrics(mode, gridWidth.value, gridSize, spacing.value).columns >= 2
}

/** Primary taps nobody consumed (empty grid space) close the side pane; card clicks consume theirs. */

internal fun Modifier.closeOnEmptyTap(onClose: () -> Unit): Modifier = pointerInput(onClose) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if (!up.isConsumed) onClose()
    }
}

@Composable
internal fun DetailPaneDivider(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState(onDrag)
    Box(
        modifier = Modifier
            .width(DETAIL_PANE_GAP)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = dragState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStopped = { onDragEnd() },
            )
            .testTag("library-detail-divider"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
    }
}

@Composable
internal fun DetailPaneCard(
    selectedMangaId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(selectedMangaId) { runCatching { focus.requestFocus() } }
    Surface(
        modifier = modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .testTag("library-detail-card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(Modifier.fillMaxSize().testTag("library-detail-content")) { content() }
    }
}
