package mihon.desktop.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import mihon.desktop.reader.ReaderPageTransition
import mihon.reader.layout.PageGrouping
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState

data class ReaderSpread(
    val pageIndices: List<Int>,
    val pages: List<PageDescriptor> = emptyList(),
)

val LocalReaderPageTransition = staticCompositionLocalOf { ReaderPageTransition.NONE }

const val PAGE_TRANSITION_DURATION_MILLIS = 200

fun ReaderState.visibleSpread(pageSizes: Map<PageId, PageSize> = emptyMap()): ReaderSpread {
    if (pages.isEmpty()) return ReaderSpread(emptyList(), emptyList())
    val effectivePages = pages.map { it.withIntrinsicSize(pageSizes[it.id]) }
    val groups = PageGrouping.forMode(
        pages = effectivePages,
        mode = mode,
        reserveCover = coverOffset,
        split = dualPageSplit,
        rotateToFit = dualPageRotateToFit,
    )
    val selected = pages[selectedIndex].id
    val group = groups.firstOrNull { pagesInGroup -> pagesInGroup.any { it.id == selected } }
        ?: groups.first()
    val indices = group.map { page -> pages.indexOfFirst { it.id == page.id } }
    return ReaderSpread(pageIndices = indices, pages = group)
}

fun calculateSlideDirection(fromIndex: Int, toIndex: Int, isRtl: Boolean): Float {
    val forward = toIndex >= fromIndex
    val ltrDirection = if (forward) 1f else -1f
    return if (isRtl) -ltrDirection else ltrDirection
}

internal data class ActivePageTransition(
    val outgoingSpread: ReaderSpread,
    val incomingSpread: ReaderSpread,
    val direction: Float,
    val transition: ReaderPageTransition,
)

@Composable
internal fun PagedReader(
    state: ReaderState,
    viewportWidth: Dp,
    onAction: (ReaderAction) -> Unit,
    pageContent: ReaderPageContent,
    modifier: Modifier = Modifier,
    pageSizes: Map<PageId, PageSize> = emptyMap(),
    pageTransition: ReaderPageTransition = LocalReaderPageTransition.current,
) {
    val targetSpread = state.visibleSpread(pageSizes)
    val visiblePageIds = targetSpread.pages.map { it.id }.distinct().ifEmpty {
        targetSpread.pageIndices.mapNotNull { index -> state.pages.getOrNull(index)?.id }
    }
    LaunchedEffect(state.chapterId, visiblePageIds) {
        onAction(ReaderAction.SetVisiblePages(visiblePageIds))
    }

    val isRtl = state.mode == ReadingMode.SINGLE_RTL || state.mode == ReadingMode.DUAL_RTL

    var settledSpread by remember { mutableStateOf(targetSpread) }
    var lastChapterId by remember { mutableStateOf(state.chapterId) }
    var activeTransition by remember { mutableStateOf<ActivePageTransition?>(null) }
    val progressAnimatable = remember { Animatable(1f) }

    LaunchedEffect(targetSpread, state.chapterId, pageTransition, state.mode) {
        if (state.chapterId != lastChapterId || targetSpread.pageIndices.any { it !in state.pages.indices }) {
            lastChapterId = state.chapterId
            settledSpread = targetSpread
            activeTransition = null
            progressAnimatable.snapTo(1f)
            return@LaunchedEffect
        }
        lastChapterId = state.chapterId

        if (pageTransition == ReaderPageTransition.NONE) {
            settledSpread = targetSpread
            activeTransition = null
            progressAnimatable.snapTo(1f)
            return@LaunchedEffect
        }

        if (activeTransition == null && settledSpread == targetSpread) {
            return@LaunchedEffect
        }

        val currentActive = activeTransition
        if (currentActive != null) {
            if (targetSpread == currentActive.incomingSpread) {
                progressAnimatable.animateTo(1f, animationSpec = tween(PAGE_TRANSITION_DURATION_MILLIS))
                settledSpread = targetSpread
                activeTransition = null
                return@LaunchedEffect
            } else if (targetSpread == currentActive.outgoingSpread) {
                progressAnimatable.animateTo(0f, animationSpec = tween(PAGE_TRANSITION_DURATION_MILLIS))
                settledSpread = targetSpread
                activeTransition = null
                return@LaunchedEffect
            } else {
                val fromSpread = if (progressAnimatable.value >= 0.5f) {
                    currentActive.incomingSpread
                } else {
                    currentActive.outgoingSpread
                }
                val fromIndex = fromSpread.pageIndices.firstOrNull() ?: 0
                val toIndex = targetSpread.pageIndices.firstOrNull() ?: 0
                val dir = calculateSlideDirection(fromIndex, toIndex, isRtl)
                activeTransition = ActivePageTransition(
                    outgoingSpread = fromSpread,
                    incomingSpread = targetSpread,
                    direction = dir,
                    transition = pageTransition,
                )
                progressAnimatable.snapTo(0f)
                progressAnimatable.animateTo(1f, animationSpec = tween(PAGE_TRANSITION_DURATION_MILLIS))
                settledSpread = targetSpread
                activeTransition = null
                return@LaunchedEffect
            }
        }

        val fromSpread = settledSpread
        if (fromSpread != targetSpread && fromSpread.pageIndices.all { it in state.pages.indices }) {
            val fromIndex = fromSpread.pageIndices.firstOrNull() ?: 0
            val toIndex = targetSpread.pageIndices.firstOrNull() ?: 0
            val dir = calculateSlideDirection(fromIndex, toIndex, isRtl)
            activeTransition = ActivePageTransition(
                outgoingSpread = fromSpread,
                incomingSpread = targetSpread,
                direction = dir,
                transition = pageTransition,
            )
            progressAnimatable.snapTo(0f)
            progressAnimatable.animateTo(1f, animationSpec = tween(PAGE_TRANSITION_DURATION_MILLIS))
            settledSpread = targetSpread
            activeTransition = null
        } else {
            settledSpread = targetSpread
            activeTransition = null
            progressAnimatable.snapTo(1f)
        }
    }

    val currentTransition = activeTransition
    if (currentTransition == null || pageTransition == ReaderPageTransition.NONE) {
        ReaderSpreadView(
            spread = settledSpread,
            state = state,
            viewportWidth = viewportWidth,
            pageSizes = pageSizes,
            pageContent = pageContent,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                .testTag("reader-paged-transition-container"),
        ) {
            when (currentTransition.transition) {
                ReaderPageTransition.FADE -> {
                    ReaderSpreadView(
                        spread = currentTransition.outgoingSpread,
                        state = state,
                        viewportWidth = viewportWidth,
                        pageSizes = pageSizes,
                        pageContent = pageContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader-transition-outgoing")
                            .graphicsLayer {
                                alpha = (1f - progressAnimatable.value).coerceIn(0f, 1f)
                            },
                    )
                    ReaderSpreadView(
                        spread = currentTransition.incomingSpread,
                        state = state,
                        viewportWidth = viewportWidth,
                        pageSizes = pageSizes,
                        pageContent = pageContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader-transition-incoming")
                            .graphicsLayer {
                                alpha = progressAnimatable.value.coerceIn(0f, 1f)
                            },
                    )
                }
                ReaderPageTransition.SLIDE -> {
                    val dir = currentTransition.direction
                    ReaderSpreadView(
                        spread = currentTransition.outgoingSpread,
                        state = state,
                        viewportWidth = viewportWidth,
                        pageSizes = pageSizes,
                        pageContent = pageContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader-transition-outgoing")
                            .graphicsLayer {
                                translationX = -dir * size.width * progressAnimatable.value
                            },
                    )
                    ReaderSpreadView(
                        spread = currentTransition.incomingSpread,
                        state = state,
                        viewportWidth = viewportWidth,
                        pageSizes = pageSizes,
                        pageContent = pageContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader-transition-incoming")
                            .graphicsLayer {
                                translationX = dir * size.width * (1f - progressAnimatable.value)
                            },
                    )
                }
                ReaderPageTransition.NONE -> {
                    ReaderSpreadView(
                        spread = settledSpread,
                        state = state,
                        viewportWidth = viewportWidth,
                        pageSizes = pageSizes,
                        pageContent = pageContent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSpreadView(
    spread: ReaderSpread,
    state: ReaderState,
    viewportWidth: Dp,
    pageSizes: Map<PageId, PageSize>,
    pageContent: ReaderPageContent,
    modifier: Modifier = Modifier,
) {
    val displayPages = if (spread.pages.isNotEmpty()) {
        spread.pages
    } else {
        spread.pageIndices.mapNotNull { state.pages.getOrNull(it) }
    }
    when {
        displayPages.size == 2 -> Row(modifier = modifier.fillMaxSize()) {
            displayPages.forEachIndexed { i, page ->
                val pageIndex = spread.pageIndices.getOrNull(i)
                    ?: state.pages.indexOfFirst { it.id == page.id }.coerceAtLeast(0)
                val intrinsicSize = pageSizes[page.id]
                ReaderPageFrame(
                    page = page.withIntrinsicSize(intrinsicSize),
                    pageIndex = pageIndex,
                    totalPages = state.pages.size,
                    scaleMode = state.scaleMode,
                    zoom = state.zoom,
                    requestedPan = state.pan,
                    pageContent = pageContent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    intrinsicSize = intrinsicSize,
                )
            }
        }
        displayPages.size == 1 -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val page = displayPages.single()
            val pageIndex = spread.pageIndices.firstOrNull()
                ?: state.pages.indexOfFirst { it.id == page.id }.coerceAtLeast(0)
            val intrinsicSize = pageSizes[page.id]
            ReaderPageFrame(
                page = page.withIntrinsicSize(intrinsicSize),
                pageIndex = pageIndex,
                totalPages = state.pages.size,
                scaleMode = state.scaleMode,
                zoom = state.zoom,
                requestedPan = state.pan,
                pageContent = pageContent,
                modifier = if (state.mode.isDualPage) {
                    Modifier.width(viewportWidth / 2).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                },
                intrinsicSize = intrinsicSize,
            )
        }
    }
}
