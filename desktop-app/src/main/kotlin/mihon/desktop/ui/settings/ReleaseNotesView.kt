package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.updates.ReleaseNoteBlock
import mihon.desktop.updates.releaseNoteBlocks

/** `**bold**`, `` `code` `` and `[label](url)` (shown as its label); anything else stays literal. */
private val inlineMarkdown = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\([^)]*\\)")

internal fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var position = 0
    for (match in inlineMarkdown.findAll(text)) {
        append(text.substring(position, match.range.first))
        val (bold, code, link) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            else -> append(link)
        }
        position = match.range.last + 1
    }
    append(text.substring(position))
}

/**
 * "What's new" of a release, rendered from its Markdown body; nothing when the body has no
 * change notes (only install instructions, or empty). The full notes stay on the release page.
 */
@Composable
internal fun ReleaseNotesView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { releaseNoteBlocks(markdown) }
    if (blocks.isEmpty()) return
    val strings = LocalStrings.current
    Column(
        modifier.fillMaxWidth().testTag("app-update-release-notes"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(strings.text(UiText.AppUpdateWhatsNew), style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Column(
                Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                blocks.forEach { block -> ReleaseNoteLine(block) }
            }
        }
    }
}

@Composable
private fun ReleaseNoteLine(block: ReleaseNoteBlock) {
    val body = MaterialTheme.typography.bodyMedium
    when (block) {
        is ReleaseNoteBlock.Heading -> Text(
            inlineMarkdown(block.text),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        is ReleaseNoteBlock.Paragraph -> Text(inlineMarkdown(block.text), style = body)
        is ReleaseNoteBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", style = body, color = MaterialTheme.colorScheme.primary)
            Text(inlineMarkdown(block.text), style = body)
        }
    }
}
