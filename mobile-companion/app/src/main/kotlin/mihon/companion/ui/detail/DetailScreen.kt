package mihon.companion.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.companion.R
import mihon.companion.ui.components.Cover
import mihon.companion.ui.components.EmptyState
import mihon.companion.ui.components.SectionLabel
import mihon.companion.ui.relativeTime
import mihon.companion.ui.statusLabel

@Composable
fun DetailScreen(state: DetailUiState, onAction: (DetailAction) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        if (!state.found) {
            EmptyState(stringResource(R.string.library_no_match), null)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { DetailHeader(state) }
            item { DetailActions(state, onAction) }
            item { Synopsis(state.description, state.genres) }
            item { ChaptersHeader(state) }
            items(state.chapters, key = { it.url }) { chapter ->
                ChapterItem(chapter, onClick = { onAction(DetailAction.ToggleRead(chapter.url)) })
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(state: DetailUiState) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Cover(state.thumbnailUrl, state.title, Modifier.width(112.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            state.author?.let { Meta(it) }
            Meta(stringResource(statusLabel(state.status)))
            Meta(
                state.sourceName?.let { stringResource(R.string.detail_source, it) }
                    ?: stringResource(R.string.detail_source_id, state.sourceId),
            )
            state.lastReadAt?.let { Meta(stringResource(R.string.detail_last_read, relativeTime(it))) }
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DetailActions(state: DetailUiState, onAction: (DetailAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onAction(DetailAction.ToggleFavorite) }, shape = MaterialTheme.shapes.small) {
            Icon(
                if (state.favorite) Icons.Outlined.BookmarkRemove else Icons.Outlined.LibraryAdd,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(stringResource(if (state.favorite) R.string.detail_remove else R.string.detail_add))
        }
        if (state.readCount < state.chapters.size) {
            OutlinedButton(onClick = { onAction(DetailAction.MarkAllRead) }, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.detail_mark_all_read))
            }
        }
    }
}

@Composable
private fun ChaptersHeader(state: DetailUiState) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.detail_chapters, state.chapters.size))
        Text(
            text = stringResource(R.string.detail_progress, state.readCount, state.chapters.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
        )
    }
}
