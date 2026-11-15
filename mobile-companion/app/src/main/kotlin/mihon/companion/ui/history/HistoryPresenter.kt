package mihon.companion.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.companion.data.StoredLibrary
import mihon.sync.core.model.MangaKey

data class HistoryEntry(
    val key: MangaKey,
    val title: String,
    val thumbnailUrl: String?,
    val chapterName: String,
    val lastRead: Long,
)

data class HistoryUiState(val entries: List<HistoryEntry> = emptyList())

/** Latest read chapter per manga, newest first — what you read on any synced device. */
class HistoryPresenter(library: StateFlow<StoredLibrary>) : ViewModel() {
    val state: StateFlow<HistoryUiState> = library.map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private fun toUiState(library: StoredLibrary): HistoryUiState {
        val entries = library.mangas.mapNotNull { stored ->
            val latest =
                stored.manga.history.filter { it.lastRead > 0 }.maxByOrNull { it.lastRead } ?: return@mapNotNull null
            val chapter = stored.manga.chapters.firstOrNull { it.url == latest.url }
            HistoryEntry(
                key = stored.key,
                title = stored.manga.title,
                thumbnailUrl = stored.manga.thumbnailUrl,
                chapterName = chapter?.name ?: latest.url,
                lastRead = latest.lastRead,
            )
        }
        return HistoryUiState(entries.sortedByDescending { it.lastRead }.take(MAX_ENTRIES))
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
