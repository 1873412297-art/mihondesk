package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One button in an [OverflowActionRow]. Lower [dropOrder] moves into the overflow menu first. */
internal class OverflowAction(
    val key: String,
    val dropOrder: Int,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val button: @Composable () -> Unit,
)

/**
 * A single row: [leading], the actions that fit, a "more" menu holding the rest, and [trailing] pinned to
 * the right edge. It never wraps, so the row stays one button tall.
 */
@Composable
internal fun OverflowActionRow(
    actions: List<OverflowAction>,
    moreLabel: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    spacing: Dp = 8.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = spacing.roundToPx()

        fun List<Placeable>.width() = sumOf { it.width }

        // Probes only measure. They carry no semantics, so hidden buttons are neither announced nor found twice.
        fun probeWidth(slot: String, content: @Composable () -> Unit): Int =
            subcompose(slot) { Box(Modifier.clearAndSetSemantics {}) { content() } }
                .sumOf { it.measure(loose).width }

        val lead = leading?.let { content -> subcompose("lead", content).map { it.measure(loose) } }.orEmpty()
        val trail = trailing?.let { content -> subcompose("trail", content).map { it.measure(loose) } }.orEmpty()
        val widths = actions.map { action -> probeWidth("probe-${action.key}", action.button) }
        val moreWidth = probeWidth("probe-more") { MoreButton(moreLabel, tagged = false) {} }
        val fixed = (if (lead.isEmpty()) 0 else lead.width() + gap) + (if (trail.isEmpty()) 0 else trail.width() + gap)

        val hidden = mutableSetOf<String>()
        fun needed(): Int {
            val shown = actions.indices.filter { actions[it].key !in hidden }
            val buttonsWidth = shown.sumOf { widths[it] } + gap * (shown.size - 1).coerceAtLeast(0)
            return fixed + buttonsWidth + if (hidden.isEmpty()) 0 else moreWidth + gap
        }
        for (action in actions.sortedBy { it.dropOrder }) {
            if (needed() <= constraints.maxWidth) break
            hidden += action.key
        }

        val visible = actions.filter { it.key !in hidden }.map { action ->
            subcompose("action-${action.key}", action.button).map { it.measure(loose) }
        }
        val overflow = actions.filter { it.key in hidden }
        val more = if (overflow.isEmpty()) {
            emptyList()
        } else {
            subcompose("more") {
                Box {
                    MoreButton(moreLabel) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        overflow.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = { Icon(action.icon, contentDescription = null) },
                                enabled = action.enabled,
                                onClick = {
                                    menuOpen = false
                                    action.onClick()
                                },
                                modifier = Modifier.testTag("manga-detail-menu-${action.key}"),
                            )
                        }
                    }
                }
            }.map { it.measure(loose) }
        }
        val height = (lead + trail + more + visible.flatten()).maxOfOrNull { it.height } ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else needed()
        layout(width, height) {
            var x = 0
            fun place(group: List<Placeable>) {
                group.forEach { it.placeRelative(x, (height - it.height) / 2) }
                x += group.width() + gap
            }
            if (lead.isNotEmpty()) place(lead)
            visible.forEach(::place)
            if (more.isNotEmpty()) place(more)
            var trailX = width - trail.width()
            trail.forEach {
                it.placeRelative(trailX, (height - it.height) / 2)
                trailX += it.width
            }
        }
    }
}

@Composable
private fun MoreButton(label: String, tagged: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = if (tagged) Modifier.testTag("manga-detail-more-button") else Modifier,
    ) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = label)
    }
}
