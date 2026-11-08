package mihon.desktop.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.image.LocalCustomCoverManager
import mihon.desktop.image.LocalImageLoader
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.desktop.ui.common.MangaCover
import mihon.desktop.ui.common.coverHeaders

/** Cover, title, metadata, description, genres, categories and notes for the detail header. */
@Composable
internal fun MangaDetailInfoRow(
    manga: MangaDetails,
    strings: DesktopStrings,
    sourceBaseUrl: String?,
    coverRefreshKey: Int,
    hasCustomCover: Boolean,
    onCoverClick: () -> Unit,
    onCoverChanged: () -> Unit,
    onEditNotes: () -> Unit,
    onCoverLoadFailed: ((Int) -> Unit)?,
) {
    val customCoverManager = LocalCustomCoverManager.current
    val imageLoader = LocalImageLoader.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoverColumn(
            manga = manga,
            strings = strings,
            sourceBaseUrl = sourceBaseUrl,
            coverRefreshKey = coverRefreshKey,
            hasCustomCover = hasCustomCover,
            onCoverClick = onCoverClick,
            onChangeCover = {
                val picked = chooseCoverImage(strings.text(UiText.ChooseCover))
                if (picked != null) {
                    customCoverManager?.setCustomCover(manga.id, picked)
                    imageLoader?.clearMemoryCache()
                    onCoverChanged()
                }
            },
            onResetCover = {
                customCoverManager?.removeCustomCover(manga.id)
                imageLoader?.clearMemoryCache()
                onCoverChanged()
            },
            onCoverLoadFailed = onCoverLoadFailed,
        )
        MangaInfoColumn(manga, strings, onEditNotes)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverColumn(
    manga: MangaDetails,
    strings: DesktopStrings,
    sourceBaseUrl: String?,
    coverRefreshKey: Int,
    hasCustomCover: Boolean,
    onCoverClick: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
    onCoverLoadFailed: ((Int) -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        key(coverRefreshKey) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onCoverClick)
                    .testTag("manga-detail-cover-clickable"),
            ) {
                val detailCoverHeaders = remember(sourceBaseUrl, manga.thumbnailUrl) {
                    coverHeaders(sourceBaseUrl, manga.thumbnailUrl)
                }
                val onCoverError = remember(onCoverLoadFailed) {
                    { code: Int ->
                        if (code == 403 || code == 404 || code == 410) {
                            onCoverLoadFailed?.invoke(code)
                        }
                    }
                }
                MangaCover(
                    thumbnailUrl = manga.thumbnailUrl,
                    mangaId = manga.id,
                    contentDescription = manga.title,
                    modifier = Modifier.width(140.dp).height(200.dp),
                    shape = MaterialTheme.shapes.medium,
                    headers = detailCoverHeaders,
                    onCoverHttpError = onCoverError,
                )
            }
        }
        OutlinedButton(
            onClick = onChangeCover,
            modifier = Modifier.testTag("manga-detail-change-cover-btn"),
        ) {
            Text(strings.mangaDetailChangeCover)
        }
        if (hasCustomCover) {
            TextButton(
                onClick = onResetCover,
                modifier = Modifier.testTag("manga-detail-reset-cover-btn"),
            ) {
                Text(strings.mangaDetailResetCover)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MangaInfoColumn(
    manga: MangaDetails,
    strings: DesktopStrings,
    onEditNotes: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MangaInfoHeader(manga, strings)
        MangaGenresSection(manga, strings)
        MangaCategoriesSection(manga, strings)
        MangaNotesSection(manga, strings, onEditNotes)
    }
}

@Composable
private fun MangaInfoHeader(manga: MangaDetails, strings: DesktopStrings) {
    Text(
        manga.title,
        modifier = Modifier.testTag("manga-detail-title"),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )
    MangaStatusBadge(manga.status, strings)
    manga.author?.takeIf(String::isNotBlank)?.let {
        MetadataLine(strings.mangaDetailAuthor, it)
    }
    manga.artist?.takeIf(String::isNotBlank)?.let {
        MetadataLine(strings.mangaDetailArtist, it)
    }
    manga.description?.takeIf(String::isNotBlank)?.let {
        ExpandableMangaDescription(it)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaGenresSection(manga: MangaDetails, strings: DesktopStrings) {
    val genres = decodeGenres(manga.genreJson)
    if (genres.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            strings.mangaDetailGenres,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Retain for semantic & accessibility assertions
        Text(
            text = genres.joinToString(" · "),
            modifier = Modifier.height(0.dp).alpha(0f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            genres.forEach { genre ->
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(genre, style = MaterialTheme.typography.labelSmall)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MangaCategoriesSection(manga: MangaDetails, strings: DesktopStrings) {
    if (manga.categories.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            strings.mangaDetailCategories,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = manga.categories.joinToString(" · ") { it.name },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun MangaNotesSection(manga: MangaDetails, strings: DesktopStrings, onEditNotes: () -> Unit) {
    if (manga.notes.isNotBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MetadataLine(strings.mangaDetailNotes, manga.notes)
            }
            DesktopTooltipBox(text = strings.mangaNotesEdit) {
                IconButton(
                    onClick = onEditNotes,
                    modifier = Modifier.testTag("manga-detail-edit-notes-btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = strings.mangaNotesEdit,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    } else {
        TextButton(
            onClick = onEditNotes,
            modifier = Modifier.testTag("manga-detail-add-notes-btn"),
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(strings.mangaNotesTitle)
        }
    }
}
