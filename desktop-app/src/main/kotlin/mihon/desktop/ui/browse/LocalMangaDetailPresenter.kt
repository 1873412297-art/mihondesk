package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.ui.library.ChapterBrowseModel
import mihon.desktop.ui.library.ChapterFilterState
import mihon.desktop.ui.library.ChapterSortState
import mihon.desktop.ui.library.MangaDetailActions
import mihon.desktop.ui.library.MangaDetailUiState

/** Detail page of a manga opened from the local source: loads it from the library and edits its chapters. */
internal class LocalMangaDetailPresenter(
    private val sourceId: Long,
    private val mangaUrl: String,
    private val repository: LibraryRepository,
    private val mutations: LibraryMutationPort,
    private val readerLibrary: ReaderLibraryPort,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val presenterScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val loaded = MutableStateFlow(LocalMangaDetailState())
    private val filter = MutableStateFlow(ChapterFilterState())
    private val sort = MutableStateFlow(ChapterSortState())
    private val chapterBrowse = ChapterBrowseModel()
    private var loadJob: Job? = null

    val state: StateFlow<MangaDetailUiState> = combine(loaded, filter, sort, chapterBrowse.state) { detail, f, s, b ->
        detail.toUiState(f, s, b)
    }.stateIn(presenterScope, SharingStarted.Eagerly, LocalMangaDetailState().toUiState())

    fun load() {
        loadJob?.cancel()
        loaded.update { it.copy(loading = true, errorMessage = null) }
        loadJob = presenterScope.launch {
            loaded.value = try {
                withContext(ioDispatcher) {
                    loadLocalMangaDetail(sourceId, mangaUrl, repository, mutations, readerLibrary)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DesktopLogger.warn("LocalMangaDetail", "Failed to load local manga", error)
                LocalMangaDetailState(loading = false, errorMessage = error.message ?: "Failed to load local manga")
            }
        }
    }

    fun actions(onReadChapter: (Long) -> Unit): MangaDetailActions = MangaDetailActions(
        onReadChapter = onReadChapter,
        onChapterFilterChange = { filter.value = it },
        onChapterSortChange = { sort.value = it },
        onToggleBookmark = { id ->
            editChapters { chapters -> chapters.edit(id) { it.copy(bookmark = !it.bookmark) } }
        },
        onToggleRead = { id -> editChapters { chapters -> chapters.edit(id) { it.copy(read = !it.read) } } },
        onMarkPreviousRead = { id -> editChapters { chapters -> chapters.markReadThrough(id) } },
        onChapterBrowseAction = { chapterBrowse.onAction(loaded.value.manga?.id, it) },
    )

    /** Applies [change] to this manga's chapters, writes back the records it returns, then reloads. */
    private fun editChapters(change: (List<ChapterRecord>) -> List<ChapterRecord>) {
        val mangaId = loaded.value.mangaId ?: return
        presenterScope.launch {
            try {
                withContext(ioDispatcher) {
                    val chapters = repository.allChaptersSnapshot().filter { it.mangaId == mangaId }
                    change(chapters).forEach(mutations::updateChapter)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DesktopLogger.warn("LocalMangaDetail", "Failed to update chapters of manga $mangaId", error)
            }
            load()
        }
    }

    override fun close() {
        presenterScope.cancel()
    }
}

private fun List<ChapterRecord>.edit(chapterId: Long, change: (ChapterRecord) -> ChapterRecord): List<ChapterRecord> =
    filter { it.id == chapterId }.map(change)

/** Every unread chapter up to and including [chapterId] in source order, marked read. */
internal fun List<ChapterRecord>.markReadThrough(chapterId: Long): List<ChapterRecord> {
    val ordered = sortedBy { it.sourceOrder }
    val target = ordered.indexOfFirst { it.id == chapterId }
    if (target < 0) return emptyList()
    return ordered.take(target + 1).filter { !it.read }.map { it.copy(read = true) }
}
