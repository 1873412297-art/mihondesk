package mihon.companion.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.companion.R
import mihon.companion.ui.components.Cover
import mihon.companion.ui.components.EmptyState
import mihon.companion.ui.components.PageHeader
import mihon.companion.ui.relativeTime
import mihon.sync.core.model.MangaKey

@Composable
fun HistoryScreen(state: HistoryUiState, onOpen: (MangaKey) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageHeader(stringResource(R.string.history_title)) }
        if (state.entries.isEmpty()) {
            item { EmptyState(stringResource(R.string.history_empty), null) }
        }
        items(state.entries, key = { it.key.toKeyString() }) { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(entry.key) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Cover(entry.thumbnailUrl, entry.title, Modifier.width(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.chapterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    relativeTime(entry.lastRead),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
