package mihon.companion.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mihon.companion.data.StoredLibrary
import mihon.sync.core.model.CategoryKey
import mihon.sync.core.model.MangaKey

data class LibraryItem(
    val key: MangaKey,
    val title: String,
    val thumbnailUrl: String?,
    val unread: Int,
)

data class LibraryUiState(
    val query: String = "",
    val categories: List<String> = emptyList(),
    /** null = all categories. */
    val selectedCategory: String? = null,
    val items: List<LibraryItem> = emptyList(),
    val libraryEmpty: Boolean = true,
    val pendingChanges: Int = 0,
)

sealed interface LibraryAction {
    data class Search(val query: String) : LibraryAction
    data class SelectCategory(val name: String?) : LibraryAction
}

private data class LibraryFilter(val query: String = "", val category: String? = null)

class LibraryPresenter(library: StateFlow<StoredLibrary>) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())

    val state: StateFlow<LibraryUiState> = combine(library, filter, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onAction(action: LibraryAction) {
        filter.value = when (action) {
            is LibraryAction.Search -> filter.value.copy(query = action.query)
            is LibraryAction.SelectCategory -> filter.value.copy(category = action.name)
        }
    }

    private fun toUiState(library: StoredLibrary, filter: LibraryFilter): LibraryUiState {
        val shelf = library.mangas.filter { it.manga.favorite }
        val categories = library.categories.sortedBy { it.category.order }.map { it.category.name }
        val selected = filter.category?.takeIf { name -> categories.any { same(it, name) } }
        val query = filter.query.trim()
        val items = shelf.asSequence()
            .filter { stored -> selected == null || stored.categoryNames.any { same(it, selected) } }
            .filter { stored ->
                query.isEmpty() ||
                    stored.manga.title.contains(query, ignoreCase = true) ||
                    stored.manga.author.orEmpty().contains(query, ignoreCase = true)
            }
            .sortedBy { it.manga.title.lowercase() }
            .map { stored ->
                LibraryItem(
                    key = stored.key,
                    title = stored.manga.title,
                    thumbnailUrl = stored.manga.thumbnailUrl,
                    unread = stored.manga.chapters.count { !it.read },
                )
            }
            .toList()
        return LibraryUiState(
            query = filter.query,
            categories = categories,
            selectedCategory = selected,
            items = items,
            libraryEmpty = shelf.isEmpty(),
            pendingChanges = library.pendingChanges,
        )
    }

    private fun same(a: String, b: String) = CategoryKey.normalize(a) == CategoryKey.normalize(b)
}
