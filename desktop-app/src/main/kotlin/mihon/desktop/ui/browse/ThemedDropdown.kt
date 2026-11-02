package mihon.desktop.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Roughly 8-9 menu rows (each ~36dp) before the menu scrolls internally. */
val THEMED_DROPDOWN_MAX_HEIGHT = 312.dp

/** Select filters with more options than this may render as grouped chips. */
const val SELECT_CHIP_THRESHOLD = 12

enum class SelectFilterLayout { Dropdown, GroupedChips }

data class SelectOption(val index: Int, val label: String)

data class SelectOptionGroup(val header: String?, val options: List<SelectOption>)

private val PREFIX_REGEX = Regex("""^\s*([^·・]{1,8}?)\s*[·・]\s*(.+?)\s*$""")

private fun splitPrefix(label: String): Pair<String, String>? =
    PREFIX_REGEX.matchEntire(label)?.let { it.groupValues[1].trim() to it.groupValues[2] }

/** Groups options by their `前綴 ·` prefix, preserving first-seen group order and original indices. */
fun groupSelectOptions(labels: List<String>): List<SelectOptionGroup> {
    val groups = LinkedHashMap<String?, MutableList<SelectOption>>()
    labels.forEachIndexed { index, label ->
        val split = splitPrefix(label)
        val key = split?.first
        groups.getOrPut(key) { mutableListOf() } += SelectOption(index, split?.second ?: label)
    }
    return groups.map { (header, options) -> SelectOptionGroup(header, options) }
}

fun selectFilterLayout(labels: List<String>): SelectFilterLayout {
    if (labels.size <= SELECT_CHIP_THRESHOLD) return SelectFilterLayout.Dropdown
    val prefixed = labels.count { splitPrefix(it) != null }
    return if (prefixed * 2 >= labels.size) SelectFilterLayout.GroupedChips else SelectFilterLayout.Dropdown
}

/** Shared themed dropdown used by all source filter selects. */
@Composable
fun ThemedDropdownSelect(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary

    Box(modifier = modifier.onSizeChanged { anchorWidthPx = it.width }) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().let { if (testTag != null) it.testTag(testTag) else it },
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = options.getOrNull(selectedIndex).orEmpty(),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            modifier = Modifier
                .width(with(density) { anchorWidthPx.toDp() })
                .heightIn(max = THEMED_DROPDOWN_MAX_HEIGHT)
                .let { if (testTag != null) it.testTag("$testTag-menu") else it },
            shape = RoundedCornerShape(12.dp),
            // Match SourceFilterDialog's Surface(color = surface, tonalElevation = 6.dp) exactly.
            containerColor = colors.surfaceColorAtElevation(6.dp),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selected) accent else colors.onSurface,
                        trailingIconColor = accent,
                    ),
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** Long prefixed select lists rendered inline as grouped chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupedSelectChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(options) { groupSelectOptions(options) }
    // Drop the 48dp touch-target padding so wrapped chip rows stay compact on desktop.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { group ->
                if (group.header != null) {
                    Text(
                        text = group.header,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.options.forEach { option ->
                        FilterChip(
                            selected = option.index == selectedIndex,
                            onClick = { onSelect(option.index) },
                            label = { Text(option.label) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("filter-select-chip-${option.index}"),
                        )
                    }
                }
                Spacer(Modifier.size(2.dp))
            }
        }
    }
}
