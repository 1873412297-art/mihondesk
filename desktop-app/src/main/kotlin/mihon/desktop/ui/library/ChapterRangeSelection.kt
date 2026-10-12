package mihon.desktop.ui.library

/**
 * Shift-click selection over the chapters in display order.
 *
 * Adds every chapter between [anchorId] and [targetId] (inclusive) to [selected]. Without a usable anchor
 * (nothing clicked yet, or the anchor was filtered out) only [targetId] is added, so the click still selects.
 */
fun chapterRangeSelection(
    orderedIds: List<Long>,
    anchorId: Long?,
    targetId: Long,
    selected: Set<Long>,
): Set<Long> {
    val target = orderedIds.indexOf(targetId)
    if (target < 0) return selected
    val anchor = anchorId?.let(orderedIds::indexOf)?.takeIf { it >= 0 } ?: return selected + targetId
    val range = if (anchor <= target) anchor..target else target..anchor
    return selected + orderedIds.slice(range)
}
