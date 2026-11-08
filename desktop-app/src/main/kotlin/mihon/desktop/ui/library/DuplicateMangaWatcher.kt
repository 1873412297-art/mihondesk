package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

/**
 * Watches the library for duplicate titles and owns the queue of duplicate dialogs. Copies keep
 * running even when the caller is not composing, so the watcher is closed with the presenter.
 */
internal class DuplicateMangaWatcher(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort?,
    selectedCategoryId: Flow<Long>,
    private val onMigrated: () -> Unit,
) : AutoCloseable {
    val dialogState = MutableStateFlow<DuplicateMangaDialogState?>(null)
    private val lock = Any()
    private val knownMangaIds = mutableSetOf<Long>()
    private val dismissedDuplicateGroupKeys = mutableSetOf<String>()
    private val queuedDuplicateDialogs = ArrayDeque<DuplicateMangaDialogState>()
    private val watcherJob = scope.launch {
        // The selected-category flow already observes the whole library when "All" is active.
        // For a filtered category, watch the full library separately so additions outside the
        // category still trigger duplicate detection.
        selectedCategoryId
            .flatMapLatest { categoryId ->
                if (categoryId == SYSTEM_ALL_CATEGORY.id) {
                    emptyFlow()
                } else {
                    repository.observeLibrary(null)
                        .flowOn(Dispatchers.IO)
                        .catch { }
                }
            }
            .catch { }
            .collect { items -> observe(items) }
    }

    fun observe(items: List<LibraryManga>) {
        synchronized(lock) {
            val currentIds = items.mapTo(mutableSetOf()) { it.id }
            val newIds = currentIds - knownMangaIds
            knownMangaIds.addAll(currentIds)
            if (items.isEmpty()) return

            findDuplicateGroups(items).forEach { (normalizedTitle, members) ->
                val groupKey = normalizedTitle + "|" + members.map { it.id }.sorted().joinToString(",")
                if (groupKey in dismissedDuplicateGroupKeys) return@forEach
                if (dialogState.value?.groupKey == groupKey) return@forEach
                if (queuedDuplicateDialogs.any { it.groupKey == groupKey }) return@forEach

                val target = members.firstOrNull { it.id in newIds } ?: members.first()
                val candidates = members
                    .filter { it.id != target.id && it.sourceId != target.sourceId }
                    .map { it.toDuplicateCandidate() }
                if (candidates.isEmpty()) return@forEach

                val dialog = DuplicateMangaDialogState(
                    groupKey = groupKey,
                    target = target.toDuplicateCandidate(),
                    candidates = candidates,
                )
                if (dialogState.value == null) {
                    dialogState.value = dialog
                } else {
                    queuedDuplicateDialogs.addLast(dialog)
                }
            }
        }
    }

    fun advance(markDismissed: Boolean) {
        synchronized(lock) {
            val current = dialogState.value ?: return
            if (markDismissed) {
                dismissedDuplicateGroupKeys += current.groupKey
            }
            dialogState.value = queuedDuplicateDialogs.removeFirstOrNull()
        }
    }

    private fun migrateManga(sourceMangaId: Long, targetMangaId: Long) {
        val port = mutationPort ?: return
        val source = repository.mangaSnapshot(sourceMangaId) ?: return
        repository.mangaSnapshot(targetMangaId) ?: return
        val sourceChapters = repository.chapterSnapshot(sourceMangaId)
        val targetChaptersByUrl = repository.chapterSnapshot(targetMangaId).associateBy { it.url }
        val now = System.currentTimeMillis()
        port.transaction {
            for (chapter in sourceChapters) {
                val existing = targetChaptersByUrl[chapter.url]
                if (existing == null) {
                    updateChapter(chapter.copy(mangaId = targetMangaId, lastModifiedAt = now).toChapterRecord())
                } else {
                    updateChapter(
                        existing.copy(
                            read = existing.read || chapter.read,
                            bookmark = existing.bookmark || chapter.bookmark,
                            lastPageRead = maxOf(existing.lastPageRead, chapter.lastPageRead),
                            lastModifiedAt = now,
                        ).toChapterRecord(),
                    )
                }
            }

            val sourceCategoryIds = repository.mangaCategoryLinksSnapshot()[sourceMangaId].orEmpty()
            val targetCategoryIds = repository.mangaCategoryLinksSnapshot()[targetMangaId].orEmpty().toSet()
            sourceCategoryIds.filterNot { it in targetCategoryIds }.forEach { categoryId ->
                linkCategory(targetMangaId, categoryId)
            }

            for (tracking in repository.trackingSnapshot(sourceMangaId)) {
                if (findTracking(targetMangaId, tracking.trackerId) == null) {
                    insertTracking(tracking.copy(id = 0L, mangaId = targetMangaId))
                }
                deleteTracking(sourceMangaId, tracking.trackerId)
            }

            updateManga(
                source.toMangaRecord().copy(
                    favorite = false,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                ),
            )
        }
    }

/** Moves the duplicate dialog to the next queued group and copies chapters into the chosen manga. */
    internal fun migrateTo(existingMangaId: Long) {
        val dialog = dialogState.value ?: return
        val sourceMangaId = dialog.target.id
        advance(markDismissed = true)
        if (sourceMangaId == existingMangaId) {
            onMigrated()
            return
        }
        // Migrating rewrites every chapter of the source manga, so it must not run on the UI thread.
        scope.launch(Dispatchers.IO) {
            migrateManga(sourceMangaId, existingMangaId)
            onMigrated()
        }
    }

    override fun close() {
        watcherJob.cancel()
    }
}
