package mihon.desktop.reader

import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.prefetch.PrefetchPolicy

/**
 * Desktop-configurable variant of the core [PrefetchPolicy] window.
 *
 * The core policy hardcodes four pages ahead and one behind; the desktop reader exposes the
 * ahead window as a user setting (1-10 pages) and applies it here. When the requested window
 * equals the core default the pipeline delegates to the coordinator's native prefetch untouched;
 * otherwise the pipeline runs this policy itself. The behind window always stays at the core
 * single page so quick direction reversal stays cheap. Pages are emitted nearest-first and RTL
 * affects placement rather than logical page order, exactly like the core policy.
 */
object DesktopPreloadPolicy {
    const val MIN_PRELOAD_PAGES = 1
    const val MAX_PRELOAD_PAGES = 10

    fun clamp(value: Int): Int = value.coerceIn(MIN_PRELOAD_PAGES, MAX_PRELOAD_PAGES)

    fun plan(
        pageCount: Int,
        selectedIndex: Int,
        mode: ReadingMode,
        direction: NavigationDirection,
        aheadPages: Int,
    ): List<Int> {
        require(pageCount > 0) { "pageCount must be positive" }
        require(selectedIndex in 0 until pageCount) { "selectedIndex $selectedIndex out of $pageCount pages" }

        val visibleWidth = PrefetchPolicy.unitPages(mode)
        val step = if (direction == NavigationDirection.FORWARD) 1 else -1
        val firstAhead = selectedIndex + step * visibleWidth
        val ahead = (0 until clamp(aheadPages))
            .map { firstAhead + step * it }
            .filter { it in 0 until pageCount }
        val behind = (1..PrefetchPolicy.BEHIND_PAGES)
            .map { selectedIndex - step * it }
            .filter { it in 0 until pageCount }
        return (ahead + behind).distinct()
    }
}
