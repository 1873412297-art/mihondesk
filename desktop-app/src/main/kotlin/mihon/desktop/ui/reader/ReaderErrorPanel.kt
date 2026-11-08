package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.hint
import mihon.desktop.i18n.text
import mihon.desktop.i18n.title
import mihon.reader.model.ReaderErrorCode
import mihon.reader.session.ReaderSessionError

@Composable
internal fun ReaderErrorPanel(message: String, error: ReaderSessionError, retryable: Boolean, onRetry: () -> Unit) {
    val strings = LocalStrings.current
    val reason = error.cause?.let { mihon.desktop.download.classifyDownloadFailure(it) }?.takeIf {
        error.cause is mihon.reader.source.ReaderFailure.RemoteImage ||
            (
                error.code == ReaderErrorCode.SOURCE_UNAVAILABLE &&
                    it != mihon.desktop.download.DownloadFailureReason.UNKNOWN
                )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 760.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (reason == null) message else strings.text(reason.title),
                    modifier = Modifier.testTag("reader-error"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (reason != null) {
                    Text(
                        strings.text(reason.hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    mihon.desktop.ui.common.ErrorDetails(error.cause?.message, "reader-error")
                }
                if (retryable) {
                    FilledTonalButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 12.dp).testTag("reader-error-retry"),
                    ) { Text(strings.downloadsRetry) }
                }
            }
        }
    }
}
