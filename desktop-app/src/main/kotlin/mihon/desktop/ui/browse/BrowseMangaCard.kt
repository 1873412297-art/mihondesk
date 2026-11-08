package mihon.desktop.ui.browse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.builtin.isLocalSource
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.DesktopTooltipBox
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

/**
 * A Browse grid card. Hovering the cover reveals an add/remove-library button; the same action is
 * also on the right-click (or Menu / Shift+F10) context menu and on the details page, so hover is
 * never the only way to reach it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BrowseMangaCard(
    manga: SManga,
    inLibrary: Boolean,
    libraryPending: Boolean,
    chapterCount: Long?,
    coverHeaders: Map<String, String>,
    onOpen: () -> Unit,
    onToggleLibrary: (() -> Unit)?,
) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val libraryLabel = if (inLibrary) strings.mangaDetailInLibrary else strings.mangaDetailAddToLibrary
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) menuOpen = true
                }
                .onPreviewKeyEvent { event ->
                    val opensMenu = event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))
                    if (opensMenu) menuOpen = true
                    opensMenu
                }
                .clickable(onClick = onOpen)
                .testTag("manga-card-" + manga.url),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    mihon.desktop.ui.common.MangaCover(
                        thumbnailUrl = manga.thumbnailUrl,
                        contentDescription = manga.title,
                        modifier = Modifier.fillMaxSize(),
                        headers = coverHeaders,
                    )
                    if (inLibrary) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        ) {
                            Text(
                                text = strings.browseInLibraryBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (onToggleLibrary != null && (hovered || libraryPending)) {
                        DesktopTooltipBox(
                            text = libraryLabel,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = onToggleLibrary,
                                enabled = !libraryPending,
                                modifier = Modifier.size(36.dp).testTag("browse-library-toggle-${manga.url}"),
                            ) {
                                if (libraryPending) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (inLibrary) {
                                            Icons.Rounded.Favorite
                                        } else {
                                            Icons.Rounded.FavoriteBorder
                                        },
                                        contentDescription = libraryLabel,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chapterCount?.let { count ->
                    Text(
                        text = strings.trackingTotalChapters(count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.testTag("manga-chapter-count-${manga.url}"),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag("browse-card-menu-${manga.url}"),
        ) {
            DropdownMenuItem(
                text = { Text(strings.text(UiText.BrowseOpenDetails)) },
                onClick = {
                    menuOpen = false
                    onOpen()
                },
            )
            if (onToggleLibrary != null) {
                DropdownMenuItem(
                    text = { Text(libraryLabel) },
                    leadingIcon = {
                        Icon(
                            if (inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    enabled = !libraryPending,
                    onClick = {
                        menuOpen = false
                        onToggleLibrary()
                    },
                    modifier = Modifier.testTag("browse-card-menu-library-${manga.url}"),
                )
            }
        }
    }
}
