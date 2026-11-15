package mihon.companion.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.companion.data.LibraryEdits
import mihon.companion.data.LibraryStore
import mihon.companion.data.StoredLibrary
import mihon.sync.core.model.MangaKey

data class ChapterRow(
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val dateUpload: Long,
)

data class DetailUiState(
    val found: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val status: Int = 0,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val sourceName: String? = null,
    val sourceId: Long = 0,
    val favorite: Boolean = false,
    val chapters: List<ChapterRow> = emptyList(),
    val lastReadAt: Long? = null,
) {
    val readCount: Int get() = chapters.count { it.read }
}

sealed interface DetailAction {
    data class ToggleRead(val url: String) : DetailAction
    data object MarkAllRead : DetailAction
    data object ToggleFavorite : DetailAction
}

/** One manga's details and the edits a phone can make without a reader: read marks and shelf membership. */
class DetailPresenter(
    private val key: MangaKey,
    private val store: LibraryStore,
    private val deviceId: () -> String,
    private val onEdited: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<DetailUiState> = store.library.map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(store.library.value))

    fun onAction(action: DetailAction) {
        val current = state.value
        viewModelScope.launch {
            store.edit { library ->
                when (action) {
                    is DetailAction.ToggleRead -> {
                        val read = current.chapters.firstOrNull { it.url == action.url }?.read ?: return@edit library
                        LibraryEdits.setChaptersRead(library, key, setOf(action.url), !read, clock(), deviceId())
                    }
                    DetailAction.MarkAllRead -> LibraryEdits.setChaptersRead(
                        library,
                        key,
                        current.chapters.filterNot { it.read }.map { it.url }.toSet(),
                        read = true,
                        now = clock(),
                        deviceId = deviceId(),
                    )
                    DetailAction.ToggleFavorite ->
                        LibraryEdits.setFavorite(library, key, !current.favorite, clock(), deviceId())
                }
            }
            onEdited()
        }
    }

    private fun toUiState(library: StoredLibrary): DetailUiState {
        val stored = library.mangas.firstOrNull { it.key == key } ?: return DetailUiState()
        val manga = stored.manga
        return DetailUiState(
            found = true,
            title = manga.title,
            author = listOfNotNull(manga.author, manga.artist).distinct().joinToString(" · ").ifBlank { null },
            status = manga.status,
            description = manga.description?.trim()?.ifBlank { null },
            genres = manga.genre,
            thumbnailUrl = manga.thumbnailUrl,
            sourceName = library.sourceName(manga.source),
            sourceId = manga.source,
            favorite = manga.favorite,
            chapters = manga.chapters.sortedBy { it.sourceOrder }.map {
                ChapterRow(it.url, it.name, it.scanlator, it.read, it.bookmark, it.dateUpload)
            },
            lastReadAt = manga.history.maxOfOrNull { it.lastRead }?.takeIf { it > 0 },
        )
    }
}
