package mihon.desktop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.history.DesktopHistoryGroup
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.LocalSearchFocusRequester
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.onSecondaryClick
import mihon.desktop.ui.common.searchFocusRequester
import mihon.desktop.ui.common.trackTextInputFocus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(
    groups: List<DesktopHistoryGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    onReadChapter: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Total pages of a chapter when the reader has seen it; null hides the "of Y" part and the bar. */
    pageCountFor: (chapterId: Long) -> Int? = { null },
    onOpenStats: (() -> Unit)? = null,
) {
    val strings = mihon.desktop.i18n.LocalStrings.current
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().testTag("history-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = strings.historyTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings.historyHeaderSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onOpenStats != null) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onOpenStats,
                        modifier = Modifier.testTag("history-open-stats"),
                    ) {
                        Text(strings.destinationLabel(mihon.desktop.navigation.DesktopDestination.Stats))
                    }
                }
                if (groups.isNotEmpty() || query.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier.testTag("history-clear-all-button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.historyClearAll)
                    }
                }
            }
        }

        // Search Bar
        val searchFocusRequester = LocalSearchFocusRequester.current
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .searchFocusRequester(searchFocusRequester)
                .trackTextInputFocus()
                .testTag("history-search"),
            label = { Text(strings.historySearchPlaceholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    DesktopTooltipBox(tooltip = strings.text(UiText.ClearSearch)) {
                        androidx.compose.material3.IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag("history-clear-search"),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = strings.text(UiText.ClearSearch),
                            )
                        }
                    }
                }
            },
        )

        // Content
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().testTag("history-empty-state"),
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
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (query.isBlank()) strings.historyEmptyTitle else strings.historyNoMatch,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("history-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.title}") {
                        Text(
                            text = strings.historyGroupTitle(group.title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                top = 10.dp,
                                bottom = 2.dp,
                            ).testTag("history-group-${group.title}"),
                        )
                    }

                    items(group.items, key = { it.chapterId }) { item ->
                        HistoryItemRow(
                            item = item,
                            onRead = { onReadChapter(item.chapterId) },
                            onDelete = { onDeleteItem(item.chapterId) },
                            pageCount = pageCountFor(item.chapterId),
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(strings.historyClearDialogTitle) },
            text = {
                Text(strings.historyClearDialogMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAll()
                    },
                    modifier = Modifier.testTag("history-confirm-clear-button"),
                ) {
                    Text(strings.historyClearAll, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(strings.dialogCancel)
                }
            },
        )
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryWithDetails,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    pageCount: Int? = null,
) {
    val strings = LocalStrings.current
    var showContextMenu by remember { mutableStateOf(false) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val timeFormatter = remember(strings) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(strings.locale)
    }
    val timeStr = remember(item.lastRead, timeFormatter) {
        Instant.ofEpochMilli(item.lastRead).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }
    val page = (item.lastPageRead + 1).toInt()
    val total = pageCount?.takeIf { it > 0 }
    val progressText = when {
        item.read -> null
        total != null -> strings.text(UiText.HistoryPageOf, page.coerceAtMost(total), total)
        else -> strings.text(UiText.HistoryPageAt, page)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .onSecondaryClick { showContextMenu = true },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = strings.historyResumeButton,
                    onClick = onRead,
                )
                .testTag("history-item-${item.chapterId}"),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hovered) 0.8f else 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MangaCover(
                    thumbnailUrl = item.mangaThumbnailUrl,
                    mangaId = item.mangaId,
                    modifier = Modifier
                        .size(width = 64.dp, height = 90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("history-cover-${item.chapterId}"),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.mangaTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(item.chapterName, progressText).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (total != null && !item.read) {
                        LinearProgressIndicator(
                            progress = { (page.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(160.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("history-progress-${item.chapterId}"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            drawStopIndicator = {},
                        )
                    }
                    val readAt = strings.text(UiText.HistoryReadAt, timeStr)
                    val meta = if (item.readDuration > 0) {
                        val minutes = (item.readDuration / 60000).coerceAtLeast(1)
                        "$readAt · ${strings.text(UiText.ReadingDuration, minutes)}"
                    } else {
                        readAt
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }

                // Quiet delete: only visible on hover, still focusable and announced for accessibility.
                DesktopTooltipBox(tooltip = strings.historyDeleteButton) {
                    androidx.compose.material3.IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .alpha(if (hovered) 1f else 0f)
                            .testTag("history-delete-${item.chapterId}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = strings.historyDeleteButton,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showContextMenu) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.historyResumeButton) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onRead()
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.historyDeleteButton) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("history-menu-delete-${item.chapterId}"),
                )
            }
        }
    }
}
