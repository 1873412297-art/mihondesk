package mihon.desktop.ui.library

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.DesktopTooltipBox

/**
 * The chapter "continue reading" opens: the first unread chapter in reading order (chapter number,
 * then source order), or the last chapter once everything has been read. Null when there are none.
 */
fun nextChapterToRead(chapters: List<LibraryChapter>): LibraryChapter? {
    val sortedAscending = chapters.sortedWith(
        compareBy<LibraryChapter> { it.chapterNumber }.thenBy { it.sourceOrder },
    )
    return sortedAscending.firstOrNull { !it.read } ?: sortedAscending.lastOrNull()
}

/** Whether a library card offers the one-click continue-reading button. */
internal fun showsContinueReading(manga: LibraryManga, isSelectionMode: Boolean): Boolean =
    !isSelectionMode && manga.unreadCount > 0

/** Small play button overlaid on a library cover that opens the next chapter without visiting the details page. */
@Composable
internal fun ContinueReadingButton(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    onContinueReading: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onContinueReading == null || !showsContinueReading(manga, isSelectionMode)) return
    val label = LocalStrings.current.text(UiText.ContextResumeReading)
    DesktopTooltipBox(text = label, modifier = modifier) {
        FilledIconButton(
            onClick = { onContinueReading(manga.id) },
            modifier = Modifier.size(34.dp).testTag("library-continue-${manga.id}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
