package mihon.desktop.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text

const val ACTIVITY_CENTER_BUTTON_TAG = "activity-center-button"
const val ACTIVITY_CENTER_PANEL_TAG = "activity-center-panel"
const val ACTIVITY_CENTER_FAILED_BADGE_TAG = "activity-center-failed-badge"

/**
 * Compact title-bar entry for background work. Quiet when idle; a thin ring while something runs;
 * an error dot when something needs attention. Clicking opens a small panel with progress,
 * failure reasons and one-click retry.
 */
@Composable
fun ActivityCenterButton(center: ActivityCenter, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val items by center.items.collectAsState()
    val summary by center.summary.collectAsState()
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val label = buildString {
        append(strings.text(UiText.ActivityCenter))
        if (summary.running > 0) append(", ").append(strings.text(UiText.ActivityRunningCount, summary.running))
        if (summary.failed > 0) append(", ").append(strings.text(UiText.ActivityFailedCount, summary.failed))
    }
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = label) { open = !open }
                .semantics { contentDescription = label }
                .testTag(ACTIVITY_CENTER_BUTTON_TAG),
            contentAlignment = Alignment.Center,
        ) {
            if (summary.running > 0) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 1.5.dp,
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.15f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(if (summary.running > 0) 12.dp else 16.dp),
                tint = if (summary.running > 0 || summary.failed > 0) colors.primary else colors.onSurfaceVariant,
            )
            if (summary.failed > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(7.dp)
                        .background(colors.error, CircleShape)
                        .border(1.dp, colors.surface, CircleShape)
                        .testTag(ACTIVITY_CENTER_FAILED_BADGE_TAG),
                )
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 40),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ActivityPanel(
                    items = items,
                    strings = strings,
                    onRetry = { center.retry(it) },
                    onDismiss = { center.dismiss(it) },
                    onClearFinished = { center.clearFinished() },
                )
            }
        }
    }
}

@Composable
private fun ActivityPanel(
    items: List<ActivityItem>,
    strings: DesktopStrings,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClearFinished: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.width(340.dp).testTag(ACTIVITY_CENTER_PANEL_TAG),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.text(UiText.ActivityCenter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (items.any { it.state != ActivityState.RUNNING }) {
                    TextButton(onClick = onClearFinished) {
                        Text(strings.text(UiText.ActivityClearFinished), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            if (items.isEmpty()) {
                Text(
                    strings.text(UiText.ActivityCenterEmpty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp).testTag("activity-center-empty"),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.id }) { item ->
                        ActivityRow(item, strings, onRetry = { onRetry(item.id) }, onDismiss = { onDismiss(item.id) })
                    }
                }
            }
        }
    }
}

private fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.DOWNLOAD -> Icons.Rounded.Download
    ActivityKind.LIBRARY_UPDATE -> Icons.Rounded.LibraryBooks
    ActivityKind.EXTENSION_INSTALL -> Icons.Rounded.Extension
    ActivityKind.SYNC -> Icons.Rounded.Sync
}

private fun ActivityKind.label(strings: DesktopStrings): String = when (this) {
    ActivityKind.DOWNLOAD -> strings.text(UiText.ActivityDownloads)
    ActivityKind.LIBRARY_UPDATE -> strings.text(UiText.ActivityLibraryUpdate)
    ActivityKind.EXTENSION_INSTALL -> strings.text(UiText.ActivityExtensionInstall)
    ActivityKind.SYNC -> strings.text(UiText.ActivitySync)
}

@Composable
private fun ActivityRow(item: ActivityItem, strings: DesktopStrings, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("activity-${item.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            item.kind.icon(),
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
            tint = if (item.state == ActivityState.FAILED) colors.error else colors.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val counts = if (item.current != null && item.total != null && item.total > 0) {
                " · ${item.current}/${item.total}"
            } else {
                ""
            }
            Text(
                item.kind.label(strings) + counts,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            item.subject?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (item.state) {
                ActivityState.RUNNING -> {
                    val progress = item.progress
                    val bar = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = bar, drawStopIndicator = {})
                    } else {
                        LinearProgressIndicator(modifier = bar)
                    }
                }
                ActivityState.FAILED -> {
                    val reason = listOfNotNull(
                        item.failedCount.takeIf { it > 0 }?.let { strings.text(UiText.ActivityFailedCount, it) },
                        item.failureReason,
                    )
                    reason.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.retry != null || item.actions.isNotEmpty()) {
                        Row {
                            if (item.retry != null) {
                                TextButton(
                                    onClick = onRetry,
                                    modifier = Modifier.height(30.dp).testTag("activity-retry-${item.id}"),
                                ) {
                                    Text(
                                        strings.text(UiText.ActivityRetry),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            item.actions.forEach { action ->
                                TextButton(
                                    onClick = action.run,
                                    modifier = Modifier.height(30.dp)
                                        .testTag("activity-action-${item.id}-${action.id}"),
                                ) {
                                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                ActivityState.SUCCEEDED -> Text(
                    strings.text(UiText.ActivityDone),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (item.state != ActivityState.RUNNING) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = strings.text(UiText.ActivityDismiss),
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(24.dp))
        }
    }
}

/**
 * Title-bar entry. The caption strip is composed outside the app's [mihon.desktop.i18n.LocalStrings]
 * provider, so it must provide the app language itself or it falls back to English.
 */
@Composable
fun TitleBarActivityCenter(center: ActivityCenter, language: mihon.desktop.i18n.AppLanguage) {
    mihon.desktop.i18n.ProvideDesktopStrings(language) { ActivityCenterButton(center) }
}
