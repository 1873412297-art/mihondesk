package mihon.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.chapterCountLabel
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.coverHeaders

@Composable
internal fun ListMangaItem(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showUnreadBadge: Boolean,
    showDownloadedBadge: Boolean,
    sourceBaseUrl: String? = null,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick)
            .librarySelectionSemantics(isSelectionMode, isSelected, strings),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.listRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("item-select-${manga.id}"),
                )
            }

            val itemCoverHeaders = remember(sourceBaseUrl, manga.thumbnailUrl) {
                coverHeaders(sourceBaseUrl, manga.thumbnailUrl)
            }
            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier
                    .width(LocalSpacing.current.listCoverHeight * (55f / 78f))
                    .height(LocalSpacing.current.listCoverHeight)
                    .clip(RoundedCornerShape(6.dp)),
                headers = itemCoverHeaders,
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                    manga.author?.takeIf { it.isNotBlank() } ?: strings.text(UiText.SourceFallback, manga.sourceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = strings.chapterCountLabel(manga.chapterCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            if (showUnreadBadge && manga.unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = strings.libraryUnreadCount(manga.unreadCount.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (showDownloadedBadge && manga.downloadedCount > 0) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = strings.text(UiText.LibraryBadgeDownloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).testTag("library-item-downloaded-${manga.id}"),
                )
            }
        }
    }
}
