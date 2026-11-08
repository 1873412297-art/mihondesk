package mihon.desktop.ui.browse

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.text
import mihon.desktop.ui.common.LocalSpacing
import mihon.desktop.ui.common.onSecondaryClick
import mihon.extension.model.SourceDescriptor

@Composable
internal fun SourcesListView(
    sources: List<SourceDescriptor>,
    pinnedIds: Set<Long>,
    hiddenIds: Set<Long> = emptySet(),
    searchQuery: String,
    selectedLanguage: String? = null,
    installedExtensions: List<InstalledExtension> = emptyList(),
    availableExtensions: List<ExtensionStoreItem> = emptyList(),
    showNsfw: Boolean = false,
    onSourceSelected: (SourceDescriptor, SourceListingMode) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHideSource: (Long) -> Unit = {},
    onLanguageSelected: (String?) -> Unit = {},
    onExtensionSelected: (InstalledExtension) -> Unit = {},
) {
    val strings = LocalStrings.current
    val spacing = LocalSpacing.current

    val availableLanguages = remember(sources, hiddenIds) {
        sources.filterNot { hiddenIds.contains(it.id) }
            .map { it.lang.lowercase() }
            .distinct()
            .sorted()
    }
    val iconUris = remember(installedExtensions, availableExtensions) {
        sourceIconUris(installedExtensions, availableExtensions)
    }

    val filteredSources =
        remember(
            sources,
            searchQuery,
            hiddenIds,
            selectedLanguage,
            showNsfw,
            installedExtensions,
            availableExtensions,
        ) {
            sources.filter { source ->
                if (hiddenIds.contains(source.id)) return@filter false
                if (!showNsfw && isSourceNsfw(source.id, installedExtensions, availableExtensions)) return@filter false
                if (selectedLanguage != null &&
                    !source.lang.equals(selectedLanguage, ignoreCase = true)
                ) {
                    return@filter false
                }
                if (searchQuery.isNotBlank() &&
                    !source.name.contains(searchQuery, ignoreCase = true) &&
                    !source.lang.contains(searchQuery, ignoreCase = true)
                ) {
                    return@filter false
                }
                true
            }
        }

    val pinnedSources = remember(filteredSources, pinnedIds) {
        filteredSources.filter { pinnedIds.contains(it.id) }
    }
    val otherSources = remember(filteredSources, pinnedIds) {
        filteredSources.filterNot { pinnedIds.contains(it.id) }
    }
    val grouped = remember(otherSources) {
        otherSources.groupBy { it.lang.uppercase() }
    }
    var collapsedLangs by remember { mutableStateOf(emptySet<String>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (availableLanguages.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("source-lang-filter"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = selectedLanguage == null,
                        onClick = { onLanguageSelected(null) },
                        label = { Text(strings.browseFilterLanguageAll) },
                        modifier = Modifier.testTag("source-lang-chip-all"),
                    )
                }
                items(availableLanguages, key = { it }) { lang ->
                    FilterChip(
                        selected = selectedLanguage.equals(lang, ignoreCase = true),
                        onClick = {
                            if (selectedLanguage.equals(lang, ignoreCase = true)) {
                                onLanguageSelected(null)
                            } else {
                                onLanguageSelected(lang)
                            }
                        },
                        label = { Text(strings.browseSourceLanguage(lang)) },
                        modifier = Modifier.testTag("source-lang-chip-$lang"),
                    )
                }
            }
        }

        if (filteredSources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = strings.browseNoMangaFound,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("sources-list"),
                verticalArrangement = Arrangement.spacedBy(spacing.listRowGap / 2),
            ) {
                if (pinnedSources.isNotEmpty()) {
                    item(key = "hdr_pinned") {
                        BrowseSectionHeader(
                            title = strings.browsePinnedSection,
                            count = pinnedSources.size,
                            accent = true,
                        )
                    }
                    items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                        SourceListItem(
                            source = source,
                            isPinned = true,
                            iconUri = iconUris[source.id],
                            isNsfw = isSourceNsfw(source.id, installedExtensions, availableExtensions),
                            installedExtensions = installedExtensions,
                            onSourceSelected = onSourceSelected,
                            onTogglePin = onTogglePin,
                            onHide = { onHideSource(source.id) },
                            onExtensionSelected = onExtensionSelected,
                        )
                    }
                }

                grouped.forEach { (lang, langSources) ->
                    val collapsed = lang in collapsedLangs
                    item(key = "hdr_$lang") {
                        BrowseSectionHeader(
                            title = strings.browseSourceLanguage(lang),
                            count = langSources.size,
                            collapsed = collapsed,
                            onToggle = {
                                collapsedLangs = if (collapsed) collapsedLangs - lang else collapsedLangs + lang
                            },
                        )
                    }
                    if (!collapsed) {
                        items(langSources, key = { it.id }) { source ->
                            SourceListItem(
                                source = source,
                                isPinned = false,
                                iconUri = iconUris[source.id],
                                isNsfw = isSourceNsfw(source.id, installedExtensions, availableExtensions),
                                installedExtensions = installedExtensions,
                                onSourceSelected = onSourceSelected,
                                onTogglePin = onTogglePin,
                                onHide = { onHideSource(source.id) },
                                onExtensionSelected = onExtensionSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowseSectionHeader(
    title: String,
    count: Int,
    accent: Boolean = false,
    collapsed: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sectionGap / 2)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collapsed != null) {
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun BrowseItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    onSecondaryClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    val hovered by interactionSource.collectIsHoveredAsState()
    val container = if (hovered) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .hoverable(interactionSource)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(if (onSecondaryClick != null) Modifier.onSecondaryClick { onSecondaryClick() } else Modifier)
                .padding(horizontal = spacing.listRowPadding + 2.dp, vertical = spacing.listRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.listRowPadding + 4.dp),
            content = content,
        )
    }
}
