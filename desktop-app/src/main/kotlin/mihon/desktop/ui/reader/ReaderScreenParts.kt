package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.input.ClickRegionPolicy
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderSessionError
import mihon.reader.session.ReaderState

@Composable
internal fun ReaderBody(
    state: ReaderState,
    session: ReaderSession,
    onAction: (ReaderAction) -> Unit,
    onRetryChapter: suspend () -> Unit,
    backgroundColor: ReaderBackgroundColor,
    webtoonMaxWidth: Int,
    webtoonSidePadding: Int,
    pageSizes: Map<PageId, PageSize>,
    pageContent: ReaderPageContent,
    onCancelLoading: () -> Unit,
    panProvider: () -> ReaderPan = { state.pan },
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    when (val load = state.loadState) {
        ReaderLoadState.Idle,
        is ReaderLoadState.Loading,
        -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(strings.readerLoadingChapter, modifier = Modifier.padding(top = 12.dp).testTag("reader-loading"))
                FilledTonalButton(
                    onClick = onCancelLoading,
                    modifier = Modifier.padding(top = 16.dp).testTag("reader-loading-cancel"),
                ) {
                    Text(strings.dialogCancel)
                }
            }
        }
        ReaderLoadState.Ready -> ReaderCanvas(
            state = state,
            onAction = onAction,
            panProvider = panProvider,
            backgroundColor = backgroundColor,
            webtoonMaxWidth = webtoonMaxWidth,
            webtoonSidePadding = webtoonSidePadding,
            pageSizes = pageSizes,
            modifier = Modifier.fillMaxSize(),
            pageContent = pageContent,
        )
        is ReaderLoadState.Failed -> ReaderErrorPanel(
            message = strings.readerErrorMessage(load.error),
            error = load.error,
            retryable = load.error.code != ReaderErrorCode.EMPTY_CHAPTER,
            onRetry = {
                scope.launch {
                    state.pages.getOrNull(state.selectedIndex)?.id?.let { session.retry(it) } ?: onRetryChapter()
                }
            },
        )
        ReaderLoadState.Closed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.readerClosed, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReaderClickRegions(policy: ClickRegionPolicy, onAction: (ReaderClickAction) -> Unit) {
    val regions = policy.regions
    Row(Modifier.fillMaxSize().testTag("reader-input-surface")) {
        ClickRegion(
            tag = "reader-previous-region",
            weight = (regions[0].end - regions[0].start) * 100f,
            action = regions[0].action,
            onAction = onAction,
        )
        ClickRegion(
            tag = "reader-center-region",
            weight = (regions[1].end - regions[1].start) * 100f,
            action = regions[1].action,
            onAction = onAction,
        )
        ClickRegion(
            tag = "reader-next-region",
            weight = (regions[2].end - regions[2].start) * 100f,
            action = regions[2].action,
            onAction = onAction,
        )
    }
}

@Composable
internal fun androidx.compose.foundation.layout.RowScope.ClickRegion(
    tag: String,
    weight: Float,
    action: ReaderClickAction,
    onAction: (ReaderClickAction) -> Unit,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .testTag(tag)
            .semantics {
                onClick {
                    onAction(action)
                    true
                }
            },
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun BoxScope.ReaderEdgeReveal(tag: String, modifier: Modifier = Modifier, onReveal: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP_REVEAL_HEIGHT)
            .testTag(tag)
            .onPointerEvent(PointerEventType.Move) { onReveal() },
    )
}

@Composable
internal fun ReaderPagePlaceholder(pageIndex: Int, modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (pageIndex + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

fun readerErrorMessage(error: ReaderSessionError): String = EnglishStrings.readerErrorMessage(error)

internal fun ReaderState.toDesktopSettings() = DesktopReaderSettings(
    mode = mode,
    coverOffset = coverOffset,
    scaleMode = scaleMode,
)

/** Synchronous pan target used while asynchronous session dispatches catch up. */
internal class ReaderPanAccumulator {
    var value: ReaderPan? = null
}

internal const val CHROME_HIDE_DELAY_MILLIS = 2_500L
internal const val READER_PAGE_INDICATOR_MILLIS = 1_000L
internal const val READER_ZOOM_HUD_MILLIS = 1_200L
internal const val READER_PAGE_INDICATOR_FADE_MILLIS = 250
internal val TOP_REVEAL_HEIGHT = 24.dp

internal const val READER_TOAST_MILLIS = 4_000L

/** Auto-scroll only makes sense where pages flow continuously. */
internal fun mihon.reader.model.ReadingMode.isContinuousForAutoScroll(): Boolean =
    this == mihon.reader.model.ReadingMode.WEBTOON || this == mihon.reader.model.ReadingMode.VERTICAL
