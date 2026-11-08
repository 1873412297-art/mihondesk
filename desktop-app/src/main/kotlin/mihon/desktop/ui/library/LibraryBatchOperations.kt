package mihon.desktop.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import java.util.concurrent.atomic.AtomicBoolean

/** Runs library-wide batch mutations one transaction at a time and reports progress. */
internal class LibraryBatchOperations(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort?,
    private val downloader: DesktopDownloader?,
    private val selected: () -> Set<Long>,
    private val batchState: MutableStateFlow<LibraryBatchState>,
    private val onSelectionCleared: () -> Unit,
    private val onBatchFinished: () -> Unit,
) {
    private val lock = AtomicBoolean(false)

    fun setCategories(categoryIds: List<Long>) {
        val selected = selected()
        runBatch(selected) { ids, progress ->
            ids.forEachIndexed { index, id ->
                setMangaCategories(id, categoryIds)
                progress(index + 1)
            }
        }
    }

    fun markRead(read: Boolean) {
        val selected = selected()
        runBatch(selected) { ids, progress ->
            ids.forEachIndexed { index, mangaId ->
                for (ch in chaptersForManga(mangaId)) {
                    updateChapter(ch.copy(read = read))
                }
                progress(index + 1)
            }
        }
    }

    fun download(chaptersCount: Int) {
        val dl = downloader ?: return
        val selected = selected()
        scope.launch(Dispatchers.IO) {
            val allManga = repository.librarySnapshot(null).associateBy { it.id }
            for (mangaId in selected) {
                val manga = allManga[mangaId] ?: continue
                val chapters = repository.chapterSnapshot(mangaId)
                val unreadChapters = chapters.filter { !it.read }
                val targetChapters = if (chaptersCount <= 0) {
                    unreadChapters
                } else {
                    unreadChapters.take(chaptersCount)
                }
                if (targetChapters.isNotEmpty()) {
                    dl.enqueue(manga, targetChapters)
                }
            }
        }
        onSelectionCleared()
    }

    fun removeFromLibrary(onRemoved: ((Set<Long>) -> Unit)? = null) {
        val selected = selected()
        if (selected.isEmpty()) return
        val copy = selected.toSet()
        runBatch(selected) { ids, progress ->
            val selectedManga = repository.librarySnapshot(null).filter { it.id in ids }
            selectedManga.forEachIndexed { index, manga ->
                val record = findManga(manga.sourceId, manga.url)
                if (record != null) {
                    val now = System.currentTimeMillis()
                    updateManga(
                        record.copy(
                            favorite = false,
                            dateAdded = 0L,
                            lastModifiedAt = now,
                            favoriteModifiedAt = now,
                        ),
                    )
                }
                progress(index + 1)
            }
        }
        onRemoved?.invoke(copy)
    }

    fun removeOne(mangaId: Long, onRemoved: (() -> Unit)? = null) {
        runBatch(setOf(mangaId)) { ids, progress ->
            val manga = repository.librarySnapshot(null).firstOrNull { it.id in ids }
            if (manga != null) {
                val record = findManga(manga.sourceId, manga.url)
                if (record != null) {
                    val now = System.currentTimeMillis()
                    updateManga(
                        record.copy(
                            favorite = false,
                            dateAdded = 0L,
                            lastModifiedAt = now,
                            favoriteModifiedAt = now,
                        ),
                    )
                }
            }
            progress(1)
        }
        onRemoved?.invoke()
    }

    fun restore(mangaIds: Set<Long>) {
        if (mangaIds.isEmpty()) return
        runBatch(mangaIds) { ids, progress ->
            val all = repository.allMangaSnapshot()
            ids.forEachIndexed { index, mangaId ->
                val record = all.firstOrNull { it.id == mangaId }
                if (record != null) {
                    val now = System.currentTimeMillis()
                    updateManga(
                        record.copy(
                            favorite = true,
                            dateAdded = record.dateAdded.takeIf { it > 0L } ?: now,
                            lastModifiedAt = now,
                            favoriteModifiedAt = now,
                        ),
                    )
                }
                progress(index + 1)
            }
        }
    }

    fun toggleRead(mangaId: Long, read: Boolean) {
        runBatch(setOf(mangaId)) { _, progress ->
            for (ch in chaptersForManga(mangaId)) {
                updateChapter(ch.copy(read = read))
            }
            progress(1)
        }
    }

    fun downloadMangaChapters(mangaId: Long, amount: Int = 1) {
        val dl = downloader ?: return
        scope.launch(Dispatchers.IO) {
            val allManga = repository.librarySnapshot(null).associateBy { it.id }
            val manga = allManga[mangaId] ?: return@launch
            val chapters = repository.chapterSnapshot(mangaId).filter { !it.read }
            val target = if (amount <= 0) chapters else chapters.take(amount)
            if (target.isNotEmpty()) {
                dl.enqueue(manga, target)
            }
        }
    }

    private fun runBatch(selected: Set<Long>, block: LibraryMutationPort.(Set<Long>, (Int) -> Unit) -> Unit) {
        val mutations = mutationPort ?: return
        if (selected.isEmpty() || !lock.compareAndSet(false, true)) return
        batchState.value = LibraryBatchState(running = true, total = selected.size)
        scope.launch(Dispatchers.IO) {
            try {
                val context = kotlinx.coroutines.currentCoroutineContext()
                mutations.transaction {
                    block(selected) { count ->
                        context.ensureActive()
                        if (count % 25 == 0 || count == selected.size) {
                            batchState.value = batchState.value.copy(processed = count)
                        }
                    }
                }
                onSelectionCleared()
                batchState.value = batchState.value.copy(running = false)
                onBatchFinished()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                batchState.value = batchState.value.copy(running = false)
                throw cancelled
            } catch (error: Exception) {
                batchState.value = batchState.value.copy(
                    running = false,
                    error = error.message ?: "Library operation failed",
                )
            } finally {
                lock.set(false)
            }
        }
    }
}
