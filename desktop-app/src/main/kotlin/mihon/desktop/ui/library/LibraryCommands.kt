package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.repository.LibraryRepository

/** Query, selection and retry commands that only touch the presenter's own state. */
internal class LibraryCommands(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val query: MutableStateFlow<String>,
    private val selectedMangaId: MutableStateFlow<Long?>,
    private val detailMangaId: MutableStateFlow<Long?>,
    private val selectedCategoryId: MutableStateFlow<Long>,
    private val retryRequest: MutableStateFlow<Long>,
    private val detailRetryRequest: MutableStateFlow<Long>,
    private val items: () -> List<LibraryManga>,
) {
    fun setQuery(value: String) {
        query.value = value
    }

    /**
     * Opens the next chapter of [mangaId] straight from the library: resolves it off the UI thread
     * with [nextChapterToRead] and hands its id to [openChapter]; does nothing for a manga without chapters.
     */
    fun continueReading(mangaId: Long, openChapter: (Long) -> Unit) {
        scope.launch {
            val chapterId = runCatching {
                withContext(Dispatchers.IO) { nextChapterToRead(repository.chapterSnapshot(mangaId))?.id }
            }.getOrNull()
            if (chapterId != null) openChapter(chapterId)
        }
    }

    fun selectManga(id: Long?) {
        val selected = id?.takeIf { candidate -> items().any { it.id == candidate } }
        selectedMangaId.value = selected
        detailMangaId.value = selected
    }

    fun openMangaDetail(id: Long) {
        detailMangaId.value = id
    }
    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun retry() {
        retryRequest.update { it + 1 }
    }

    fun retryDetail() {
        detailRetryRequest.update { it + 1 }
    }
}
