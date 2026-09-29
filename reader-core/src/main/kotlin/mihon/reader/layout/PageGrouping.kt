package mihon.reader.layout

import mihon.reader.model.PageDescriptor
import mihon.reader.model.ReadingMode
import mihon.reader.model.SplitSide
import java.util.Collections

enum class DualPageSplit {
    NEVER,
    ALWAYS,
    WIDE,
}

object PageGrouping {
    fun shouldSplit(
        page: PageDescriptor,
        split: DualPageSplit,
        rotateToFit: Boolean = false,
    ): Boolean {
        if (rotateToFit && page.isWide) return false
        return when (split) {
            DualPageSplit.NEVER -> false
            DualPageSplit.ALWAYS -> true
            DualPageSplit.WIDE -> page.isWide
        }
    }

    fun splitPages(
        pages: List<PageDescriptor>,
        split: DualPageSplit = DualPageSplit.WIDE,
        rotateToFit: Boolean = false,
        isRightToLeft: Boolean = false,
    ): List<PageDescriptor> {
        if (pages.isEmpty()) return emptyList()
        return buildList {
            for (page in pages) {
                if (rotateToFit && page.isWide) {
                    add(page.copy(width = page.height, height = page.width, rotated = true))
                } else if (shouldSplit(page, split, rotateToFit)) {
                    val halfWidth = maxOf(1, page.width / 2)
                    val left = page.copy(
                        width = halfWidth,
                        splitSide = SplitSide.LEFT,
                    )
                    val right = page.copy(
                        width = maxOf(1, page.width - halfWidth),
                        splitSide = SplitSide.RIGHT,
                    )
                    if (isRightToLeft) {
                        add(right)
                        add(left)
                    } else {
                        add(left)
                        add(right)
                    }
                } else {
                    add(page)
                }
            }
        }
    }

    fun dual(
        pages: List<PageDescriptor>,
        reserveCover: Boolean,
        split: DualPageSplit = DualPageSplit.WIDE,
        rotateToFit: Boolean = false,
        isRightToLeft: Boolean = false,
    ): List<List<PageDescriptor>> {
        if (pages.isEmpty()) return emptyList()

        val expanded = splitPages(pages, split, rotateToFit, isRightToLeft)
        val groups = buildList {
            var index = 0
            if (reserveCover) {
                val first = expanded.first()
                if (first.splitSide != null && expanded.size > 1 && expanded[1].id == first.id) {
                    add(immutableSnapshot(listOf(first, expanded[1])))
                    index = 2
                } else {
                    add(immutableSnapshot(listOf(first)))
                    index = 1
                }
            }
            while (index < expanded.size) {
                val current = expanded[index]
                if (current.splitSide != null && index + 1 < expanded.size && expanded[index + 1].id == current.id) {
                    add(immutableSnapshot(listOf(current, expanded[index + 1])))
                    index += 2
                } else if (index + 1 < expanded.size && expanded[index + 1].splitSide != null &&
                    expanded[index + 1].id != current.id
                ) {
                    add(immutableSnapshot(listOf(current)))
                    index += 1
                } else {
                    add(immutableSnapshot(expanded.subList(index, minOf(index + 2, expanded.size))))
                    index += 2
                }
            }
        }
        return immutableSnapshot(groups)
    }

    fun forMode(
        pages: List<PageDescriptor>,
        mode: ReadingMode,
        reserveCover: Boolean,
        split: DualPageSplit = DualPageSplit.WIDE,
        rotateToFit: Boolean = false,
    ): List<List<PageDescriptor>> {
        val groups = if (mode.isDualPage) {
            dual(pages, reserveCover, split, rotateToFit, mode.isRightToLeft)
        } else {
            immutableSnapshot(pages.map(::listOf))
        }
        return if (mode.isRightToLeft) {
            immutableSnapshot(groups.map { immutableSnapshot(it.reversed()) })
        } else {
            groups
        }
    }
}

private fun <T> immutableSnapshot(items: Iterable<T>): List<T> = Collections.unmodifiableList(items.toList())
