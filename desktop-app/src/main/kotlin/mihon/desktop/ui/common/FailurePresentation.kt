package mihon.desktop.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import mihon.desktop.download.DownloadFailureReason
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.hint
import mihon.desktop.i18n.text
import mihon.desktop.i18n.title

@Composable
internal fun FailureExplanation(reason: DownloadFailureReason) {
    val strings = LocalStrings.current
    Text(
        strings.text(reason.title),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        strings.text(reason.hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ErrorDetails(raw: String?, tag: String) {
    if (raw.isNullOrBlank()) return
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(raw) { mutableStateOf(false) }
    var copied by rememberSaveable(raw) { mutableStateOf(false) }
    Column {
        FlowRow {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("$tag-details")) {
                Text(strings.text(if (expanded) UiText.HideErrorDetails else UiText.ErrorDetails))
            }
            if (expanded) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(raw))
                    copied = true
                }, modifier = Modifier.testTag("$tag-copy")) {
                    Text(strings.text(if (copied) UiText.ErrorCopied else UiText.CopyErrorDetails))
                }
            }
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    raw,
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
