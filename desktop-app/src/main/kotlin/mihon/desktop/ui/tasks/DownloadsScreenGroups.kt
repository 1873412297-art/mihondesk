package mihon.desktop.ui.tasks
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.classifyDownloadFailure
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.chapterCountLabel
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.DownloadIndicator
import mihon.desktop.ui.common.ErrorDetails
import mihon.desktop.ui.common.FailureExplanation
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.animatedDownloadProgress
import mihon.desktop.ui.common.downloadIsIndeterminate
import mihon.desktop.ui.common.downloadStatusLabel
import mihon.desktop.ui.common.normalizedDownloadProgress
import mihon.desktop.ui.common.onSecondaryClick
import mihon.desktop.ui.common.searchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus

@Composable
internal fun DownloadGroupHeader(
    group: DownloadGroup,
    expanded: Boolean,
    isFirst: Boolean,
    onAction: (DownloadGroupAction) -> Unit,
    onToggle: () -> Unit,
) {
    val strings = LocalStrings.current
    val toggleLabel = strings.text(
        if (expanded) UiText.DownloadGroupCollapse else UiText.DownloadGroupExpand,
        group.mangaTitle,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("download-group-${group.mangaId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = toggleLabel,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = group.mangaTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = strings.chapterCountLabel(group.items.size.toLong()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.failedCount > 0) {
            StatusBadge(DownloadStatus.ERROR, isRunning = true)
        }
        Spacer(Modifier.weight(1f))
        val hasActive = group.items.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
        val hasPaused = group.items.any { it.status == DownloadStatus.PAUSED }
        if (hasActive) {
            GroupActionButton(Icons.Rounded.Pause, strings.text(UiText.DownloadGroupPause), "pause", group.mangaId) {
                onAction(DownloadGroupAction.PAUSE)
            }
        }
        if (hasPaused) {
            GroupActionButton(
                Icons.Rounded.PlayArrow,
                strings.text(UiText.DownloadGroupResume),
                "resume",
                group.mangaId,
            ) { onAction(DownloadGroupAction.RESUME) }
        }
        if (group.failedCount > 0) {
            GroupActionButton(Icons.Rounded.Refresh, strings.text(UiText.DownloadGroupRetry), "retry", group.mangaId) {
                onAction(DownloadGroupAction.RETRY)
            }
        }
        if (!isFirst) {
            GroupActionButton(
                Icons.Rounded.KeyboardDoubleArrowUp,
                strings.text(UiText.DownloadGroupMoveTop),
                "top",
                group.mangaId,
            ) { onAction(DownloadGroupAction.MOVE_TO_TOP) }
        }
        GroupActionButton(Icons.Rounded.Close, strings.text(UiText.DownloadGroupCancel), "cancel", group.mangaId) {
            onAction(DownloadGroupAction.CANCEL)
        }
    }
}

@Composable
internal fun GroupActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    mangaId: Long,
    onClick: () -> Unit,
) {
    DesktopTooltipBox(text = label) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp).testTag("download-group-$tag-$mangaId")) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun DownloadDragHandle(chapterId: Long, enabled: Boolean, onDrop: (offsetPx: Float, extentPx: Float) -> Unit) {
    val label = LocalStrings.current.text(UiText.DownloadDragHandle)
    var offset by remember { mutableStateOf(0f) }
    var extent by remember { mutableStateOf(0f) }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(48.dp)
            .onGloballyPositioned { coords ->
                // The card row is the parent; its height approximates one queue slot.
                extent = (coords.parentLayoutCoordinates?.size?.height ?: coords.size.height).toFloat() + spacingPx
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(chapterId) {
                            detectDragGestures(
                                onDragStart = { offset = 0f },
                                onDragEnd = { onDrop(offset, extent) },
                                onDragCancel = { offset = 0f },
                            ) { change, drag ->
                                change.consume()
                                offset += drag.y
                            }
                        }
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = label }
            .testTag("download-drag-$chapterId"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.8f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun StatusBadge(status: DownloadStatus, isRunning: Boolean) {
    val label = downloadStatusLabel(status, isRunning)
    val color = when (effectiveDownloadStatus(status, isRunning)) {
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun DesktopDownload.displayStatus(isRunning: Boolean) = effectiveDownloadStatus(status, isRunning)

internal fun effectiveDownloadStatus(status: DownloadStatus, isRunning: Boolean): DownloadStatus =
    if (!isRunning &&
        (status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING)
    ) {
        DownloadStatus.PAUSED
    } else {
        status
    }

internal fun formatSpeed(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
        else -> String.format("%.0f B/s", bytesPerSec)
    }
}
