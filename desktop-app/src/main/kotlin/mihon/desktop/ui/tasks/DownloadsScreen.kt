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

const val DOWNLOADS_SCREEN_TEST_TAG = "downloads_screen"
const val DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG = "downloads_pause_all"
const val DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG = "downloads_resume_all"
const val DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG = "downloads_clear_completed"
const val DOWNLOAD_ITEM_TEST_TAG_PREFIX = "download_item_"
const val DOWNLOAD_READ_BUTTON_TEST_TAG_PREFIX = "download_read_"

/** High-frequency download values, read lazily by the card so progress ticks stay local. */
data class DownloadProgressInfo(val progress: Float, val downloadedImages: Int, val totalPages: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    queue: List<DesktopDownload>,
    isRunning: Boolean,
    speedBytesPerSec: Double,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onCancel: (chapterId: Long) -> Unit,
    onRetry: (chapterId: Long) -> Unit,
    onReadChapter: (mangaId: Long, chapterId: Long) -> Unit,
    onPause: (chapterId: Long) -> Unit = {},
    onResume: (chapterId: Long) -> Unit = {},
    onMoveUp: (chapterId: Long) -> Unit = {},
    onMoveDown: (chapterId: Long) -> Unit = {},
    modifier: Modifier = Modifier,
    recoveryMessage: String? = null,
    storageError: String? = null,
    onRetryAllFailed: () -> Unit = {},
    onGroupAction: (mangaId: Long, action: DownloadGroupAction) -> Unit = { _, _ -> },
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    progressOf: (Long) -> DownloadProgressInfo = { id ->
        queue.firstOrNull { it.chapterId == id }
            ?.let { DownloadProgressInfo(it.progress, it.downloadedImages, it.totalPages) }
            ?: DownloadProgressInfo(0f, 0, 0)
    },
) {
    val strings = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf<DownloadStatus?>(null) }
    var collapsedMangaIds by remember { mutableStateOf(emptySet<Long>()) }
    val failedCount = queue.count { it.status == DownloadStatus.ERROR }
    val counts = queue.groupingBy { it.displayStatus(isRunning) }.eachCount()
    val visible = queue.filter { item ->
        (selectedStatus == null || item.displayStatus(isRunning) == selectedStatus) &&
            (
                item.mangaTitle.contains(query.trim(), ignoreCase = true) ||
                    item.chapterName.contains(query.trim(), ignoreCase = true)
                )
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(DOWNLOADS_SCREEN_TEST_TAG)
                .padding(16.dp),
        ) {
            // Top Header
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        text = strings.downloadsTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val speedText = formatSpeed(speedBytesPerSec)
                    val activeCount = queue.count {
                        it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.QUEUED
                    }
                    Text(
                        text = if (isRunning && speedBytesPerSec > 0) {
                            strings.downloadsActiveSpeed(activeCount, speedText)
                        } else {
                            strings.text(UiText.DownloadQueueCount, queue.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRunning) {
                        DesktopTooltipBox(tooltip = strings.downloadsPauseAll) {
                            OutlinedButton(
                                onClick = onPauseAll,
                                modifier = Modifier.testTag(DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.downloadsPauseAll)
                            }
                        }
                    } else {
                        DesktopTooltipBox(tooltip = strings.downloadsResumeAll) {
                            Button(
                                onClick = onResumeAll,
                                modifier = Modifier.testTag(DOWNLOADS_RESUME_ALL_BUTTON_TEST_TAG),
                                enabled = queue.any {
                                    it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.downloadsResumeAll)
                            }
                        }
                    }

                    if (failedCount > 0) {
                        DesktopTooltipBox(tooltip = strings.text(UiText.RetryAllFailed, failedCount)) {
                            OutlinedButton(
                                onClick = onRetryAllFailed,
                                modifier = Modifier.testTag("downloads-retry-failed"),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.text(UiText.RetryAllFailed, failedCount))
                            }
                        }
                    }

                    DesktopTooltipBox(tooltip = strings.downloadsClearCompleted) {
                        TextButton(
                            onClick = onClearCompleted,
                            modifier = Modifier.testTag(DOWNLOADS_CLEAR_COMPLETED_BUTTON_TEST_TAG),
                            enabled = queue.any { it.status == DownloadStatus.COMPLETED },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.downloadsClearCompleted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (queue.isNotEmpty()) {
                val searchFocusRequester = LocalSearchFocusRequester.current
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(strings.text(UiText.SearchDownloads)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            DesktopTooltipBox(tooltip = strings.text(UiText.ClearSearch)) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = strings.text(UiText.ClearSearch))
                                }
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .searchFocusRequester(searchFocusRequester)
                        .trackTextInputFocus()
                        .testTag("downloads-search"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("${strings.text(UiText.All)} (${queue.size})") },
                        modifier = Modifier.testTag("download-filter-ALL"),
                    )
                    DownloadStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text("${downloadStatusLabel(status, true)} (${counts[status] ?: 0})") },
                            modifier = Modifier.testTag("download-filter-$status"),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            listOfNotNull(storageError, recoveryMessage).distinct().forEach { message ->
                Surface(
                    color = if (message == storageError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("download-recovery-notice"),
                ) {
                    Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = strings.downloadsEmptyTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(strings.text(UiText.DownloadsNoMatches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        query = ""
                        selectedStatus = null
                    }) { Text(strings.text(UiText.ClearFilters)) }
                }
            } else {
                val indexMap = remember(queue) { queue.withIndex().associate { it.value.chapterId to it.index } }
                val groups = remember(visible) { groupDownloadsByManga(visible) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { group ->
                        val expanded = group.mangaId !in collapsedMangaIds
                        item(key = "manga-${group.mangaId}") {
                            DownloadGroupHeader(
                                group = group,
                                expanded = expanded,
                                isFirst = queue.firstOrNull()?.mangaId == group.mangaId,
                                onAction = { action -> onGroupAction(group.mangaId, action) },
                                onToggle = {
                                    collapsedMangaIds = if (expanded) {
                                        collapsedMangaIds + group.mangaId
                                    } else {
                                        collapsedMangaIds - group.mangaId
                                    }
                                },
                            )
                        }
                        if (!expanded) return@forEach
                        items(group.items, key = { it.chapterId }) { item ->
                            val queueIndex = indexMap[item.chapterId] ?: -1
                            val canMoveUp = queueIndex > 0
                            val canMoveDown = queueIndex in 0 until queue.lastIndex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DownloadDragHandle(
                                    chapterId = item.chapterId,
                                    enabled = queueIndex >= 0 && queue.size > 1,
                                    onDrop = { offsetPx, extentPx ->
                                        val target = dragReorderTarget(queueIndex, offsetPx, extentPx, queue.lastIndex)
                                        if (target != queueIndex) onReorder(queueIndex, target)
                                    },
                                )
                                DownloadCard(
                                    download = item,
                                    progressOf = progressOf,
                                    isRunning = isRunning,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onPause = { onPause(item.chapterId) },
                                    onResume = { onResume(item.chapterId) },
                                    onMoveUp = { onMoveUp(item.chapterId) },
                                    onMoveDown = { onMoveDown(item.chapterId) },
                                    onCancel = { onCancel(item.chapterId) },
                                    onRetry = { onRetry(item.chapterId) },
                                    onRead = { onReadChapter(item.mangaId, item.chapterId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
