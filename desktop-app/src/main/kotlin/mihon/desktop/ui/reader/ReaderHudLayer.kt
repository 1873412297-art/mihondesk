package mihon.desktop.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.DesktopReaderSettings
import mihon.reader.session.ReaderState

/**
 * Settings drawer plus the transient HUD surfaces (toast, page/zoom/auto-scroll indicators, flash).
 * Drawn inside the reader's box so every surface can align to its edges.
 */
@Composable
internal fun BoxScope.ReaderHudLayer(
    state: ReaderState,
    settings: DesktopReaderSettings,
    strings: DesktopStrings,
    chromeVisible: Boolean,
    ready: Boolean,
    showSettings: Boolean,
    onSettingsChange: (DesktopReaderSettings) -> Unit,
    onDismissSettings: () -> Unit,
    pageActionMessage: String?,
    onDismissPageActionMessage: () -> Unit,
    pageIndicatorIndex: Int?,
    pageIndicatorAlpha: () -> Float,
    zoomHudVisible: Boolean,
    autoScrolling: Boolean,
    autoScrollHudTick: Int,
    isPageFlashing: Boolean,
) {
    ReaderSettingsDrawer(showSettings, settings, onSettingsChange, onDismissSettings)
    ReaderToast(pageActionMessage, chromeVisible, strings, onDismissPageActionMessage)
    if (!chromeVisible && pageIndicatorIndex != null && ready) {
        ReaderPageIndicator(pageIndicatorIndex, state.pages.size, pageIndicatorAlpha, strings)
    }
    if (zoomHudVisible && !chromeVisible && ready) {
        ReaderZoomHud(state.zoom, strings)
    }
    ReaderAutoScrollHud(autoScrollHudTick, autoScrolling, settings.autoScrollSpeed, strings, ready)
    if (isPageFlashing) ReaderPageFlash()
}

@Composable
private fun BoxScope.ReaderSettingsDrawer(
    visible: Boolean,
    settings: DesktopReaderSettings,
    onChange: (DesktopReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.CenterEnd),
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        ReaderSettingsPanel(settings = settings, onChange = onChange, onDismiss = onDismiss)
    }
}

@Composable
private fun BoxScope.ReaderToast(
    message: String?,
    chromeVisible: Boolean,
    strings: DesktopStrings,
    onDismiss: () -> Unit,
) {
    val text = message ?: return
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(READER_TOAST_MILLIS)
        onDismiss()
    }
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = if (chromeVisible) 128.dp else 32.dp)
            .widthIn(max = 560.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("reader-toast"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f, fill = false))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("reader-page-action-message-ok"),
            ) {
                Text(strings.dialogOk, color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

@Composable
private fun BoxScope.ReaderPageIndicator(
    pageIndex: Int?,
    pageCount: Int,
    alpha: () -> Float,
    strings: DesktopStrings,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 40.dp)
            .alpha(alpha()),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xB3000000),
        contentColor = Color.White,
    ) {
        Text(
            text = strings.text(UiText.PageNumberOverlay, (pageIndex ?: 0) + 1, pageCount.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("reader-page-indicator"),
        )
    }
}

@Composable
private fun BoxScope.ReaderZoomHud(zoom: Float, strings: DesktopStrings) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("reader-zoom-hud"),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xB3000000),
        contentColor = Color.White,
    ) {
        Text(
            text = strings.text(UiText.ReaderZoomOverlay, (zoom * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BoxScope.ReaderAutoScrollHud(
    tick: Int,
    autoScrolling: Boolean,
    speed: Int,
    strings: DesktopStrings,
    ready: Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        visible = true
        kotlinx.coroutines.delay(READER_ZOOM_HUD_MILLIS)
        visible = false
    }
    if (!visible || !ready) return
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 72.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("reader-auto-scroll-hud"),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xB3000000),
        contentColor = Color.White,
    ) {
        Text(
            text = if (autoScrolling) {
                strings.text(UiText.AutoScrollStatus, "$speed dp/s")
            } else {
                strings.text(UiText.AutoScrollOff)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BoxScope.ReaderPageFlash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reader-page-flash-overlay"),
    )
}
