package mihon.desktop.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Selection mode and the set of selected manga for the library grid. */
internal class LibrarySelection {
    val state = MutableStateFlow(LibrarySelectionState())

    fun toggleMode(enabled: Boolean) {
        state.update {
            if (!enabled) LibrarySelectionState() else it.copy(isSelectionMode = true)
        }
    }

    fun toggle(mangaId: Long) {
        state.update { current ->
            val set = current.selectedMangaIds
            val updated = if (set.contains(mangaId)) set - mangaId else set + mangaId
            current.copy(
                // Deselecting the last title leaves selection mode: there is no persistent mode toggle any more.
                isSelectionMode = updated.isNotEmpty(),
                selectedMangaIds = updated,
            )
        }
    }

    fun selectAll(ids: Set<Long>) {
        state.update {
            it.copy(isSelectionMode = true, selectedMangaIds = ids)
        }
    }

    fun clear() {
        state.value = LibrarySelectionState()
    }
}
