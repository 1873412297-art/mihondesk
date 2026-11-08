package mihon.desktop.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.chapterCountLabel
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.coverHeaders

/**
 * In selection mode a card toggles membership instead of opening the manga, so expose that
 * state to screen readers; otherwise Narrator/NVDA only hear "button" with no checked state.
 */
internal fun Modifier.librarySelectionSemantics(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    strings: DesktopStrings,
): Modifier = if (!isSelectionMode) {
    this
} else {
    semantics {
        selected = isSelected
        stateDescription = strings.text(
            if (isSelected) UiText.LibraryItemSelected else UiText.LibraryItemNotSelected,
        )
    }
}

// 1. Comfortable Card: cover-led desktop card with readable metadata

@Composable
internal fun ComfortableMangaCard(
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
            .librarySelectionSemantics(isSelectionMode, isSelected, strings)
            .focusable(),
        color = containerColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
                val itemCoverHeaders = remember(sourceBaseUrl, manga.thumbnailUrl) {
                    coverHeaders(sourceBaseUrl, manga.thumbnailUrl)
                }
                MangaCover(
                    thumbnailUrl = manga.thumbnailUrl,
                    mangaId = manga.id,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    headers = itemCoverHeaders,
                )

                if (showUnreadBadge && manga.unreadCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            text = strings.libraryUnreadCount(manga.unreadCount.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }

                if (showDownloadedBadge && manga.downloadedCount > 0) {
                    DownloadedBadge(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .testTag("library-item-downloaded-${manga.id}"),
                    )
                }

                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = CircleShape,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.testTag("item-select-${manga.id}"),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(
                            manga.author?.takeIf { it.isNotBlank() }
                                ?: strings.text(UiText.SourceFallback, manga.sourceId),
                        )
                        append(" · ${strings.chapterCountLabel(manga.chapterCount)}")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// 2. Compact Card: Aspect ratio cover with dark gradient title overlay

@Composable
internal fun CompactMangaCard(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showUnreadBadge: Boolean,
    showDownloadedBadge: Boolean,
    sourceBaseUrl: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick)
            .librarySelectionSemantics(isSelectionMode, isSelected, LocalStrings.current),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val itemCoverHeaders = remember(sourceBaseUrl, manga.thumbnailUrl) {
                coverHeaders(sourceBaseUrl, manga.thumbnailUrl)
            }
            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                headers = itemCoverHeaders,
            )

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Top-right unread badge
            if (showUnreadBadge && manga.unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = manga.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Selection Checkbox
            if (showDownloadedBadge && manga.downloadedCount > 0) {
                DownloadedBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .testTag("library-item-downloaded-${manga.id}"),
                )
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0x88000000), shape = CircleShape),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("item-select-${manga.id}"),
                    )
                }
            }
        }
    }
}

// 3. Cover Only Card: Seamless cover image matrix

@Composable
internal fun CoverOnlyMangaCard(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showUnreadBadge: Boolean,
    showDownloadedBadge: Boolean,
    sourceBaseUrl: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick)
            .librarySelectionSemantics(isSelectionMode, isSelected, LocalStrings.current),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isSelected) 6.dp else 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val itemCoverHeaders = remember(sourceBaseUrl, manga.thumbnailUrl) {
                coverHeaders(sourceBaseUrl, manga.thumbnailUrl)
            }
            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                headers = itemCoverHeaders,
            )

            // Top-right unread badge
            if (showUnreadBadge && manga.unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = manga.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            // Selection Checkbox
            if (showDownloadedBadge && manga.downloadedCount > 0) {
                DownloadedBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .testTag("library-item-downloaded-${manga.id}"),
                )
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0x88000000), shape = CircleShape),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("item-select-${manga.id}"),
                    )
                }
            }
        }
    }
}

/**
 * Downloaded indicator shared by the library cards. Relies on [LibraryManga.downloadedCount],
 * which the repository derives from registered chapter assets - no disk scanning here.
 */

@Composable
internal fun DownloadedBadge(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = strings.text(UiText.LibraryBadgeDownloaded),
            modifier = Modifier.padding(4.dp).size(14.dp),
        )
    }
}

// 4. List Item: Full horizontal row
