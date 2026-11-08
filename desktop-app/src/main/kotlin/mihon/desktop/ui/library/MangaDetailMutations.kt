package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

/** Chapter and metadata mutations for the manga detail page, all off the UI thread. */
internal class MangaDetailMutations(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort?,
    private val downloader: DesktopDownloader?,
    private val detailMangaId: () -> Long?,
    private val detailManga: () -> MangaDetails?,
    private val detailChapters: () -> List<LibraryChapter>,
    private val onChanged: () -> Unit,
    private val onEditInfoClosed: () -> Unit,
    private val onLibraryChanged: () -> Unit,
) {
    fun toggleChapterBookmark(chapterId: Long) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val chapter = repository.chapterSnapshot(mangaId).find { it.id == chapterId } ?: return@launch
            val updated = chapter.copy(bookmark = !chapter.bookmark)
            mutationPort?.updateChapter(updated.toChapterRecord())
            onChanged()
        }
    }

    fun toggleChapterRead(chapterId: Long) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val chapter = repository.chapterSnapshot(mangaId).find { it.id == chapterId } ?: return@launch
            val newRead = !chapter.read
            val updated = chapter.copy(
                read = newRead,
                lastPageRead = if (!newRead) 0L else chapter.lastPageRead,
                lastModifiedAt = System.currentTimeMillis(),
            )
            mutationPort?.updateChapter(updated.toChapterRecord())
            onChanged()
        }
    }

    fun batchBookmarkChapters(chapterIds: Set<Long>, bookmark: Boolean) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val currentChapters = repository.chapterSnapshot(mangaId)
            val toUpdate = currentChapters.filter { it.id in chapterIds && it.bookmark != bookmark }
            if (toUpdate.isEmpty()) return@launch
            mutationPort?.transaction {
                toUpdate.forEach { ch ->
                    updateChapter(ch.copy(bookmark = bookmark).toChapterRecord())
                }
            }
            onChanged()
        }
    }

    fun batchMarkChaptersRead(chapterIds: Set<Long>, read: Boolean) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val currentChapters = repository.chapterSnapshot(mangaId)
            val toUpdate = currentChapters.filter { it.id in chapterIds && it.read != read }
            if (toUpdate.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            mutationPort?.transaction {
                toUpdate.forEach { ch ->
                    updateChapter(
                        ch.copy(
                            read = read,
                            lastPageRead = if (!read) 0L else ch.lastPageRead,
                            lastModifiedAt = now,
                        ).toChapterRecord(),
                    )
                }
            }
            onChanged()
        }
    }

    fun batchDownloadChapters(chapterIds: Set<Long>) {
        val manga = detailManga() ?: return
        val chapters = detailChapters().filter { it.id in chapterIds }
        if (chapters.isEmpty()) return
        downloadChapters(chapters)
    }

    fun batchDeleteChapterDownloads(chapterIds: Set<Long>) {
        val manga = detailManga() ?: return
        val chapters = detailChapters().filter { it.id in chapterIds }
        if (chapters.isEmpty()) return
        chapters.forEach { ch ->
            downloader?.deleteDownloadedChapter(manga, ch)
        }
        onChanged()
    }

    fun markPreviousChaptersRead(chapterId: Long) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val currentChapters = repository.chapterSnapshot(mangaId)
            val target = currentChapters.find { it.id == chapterId } ?: return@launch
            val now = System.currentTimeMillis()
            mutationPort?.transaction {
                currentChapters.forEach { ch ->
                    if (ch.sourceOrder > target.sourceOrder || ch.chapterNumber < target.chapterNumber) {
                        if (!ch.read) {
                            updateChapter(ch.copy(read = true, lastModifiedAt = now).toChapterRecord())
                        }
                    }
                }
            }
            onChanged()
        }
    }

    fun downloadChapter(chapterId: Long) {
        val manga = detailManga() ?: return
        val ch = detailChapters().find { it.id == chapterId } ?: return
        downloadChapters(listOf(ch))
    }

    fun downloadChapters(chapters: List<LibraryChapter>) {
        val manga = detailManga() ?: return
        if (chapters.isEmpty()) return
        scope.launch {
            downloader?.enqueue(
                sourceId = manga.sourceId,
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapters = chapters,
            )
        }
    }

    fun downloadNextChapters(amount: Int?, unreadOnly: Boolean = true) {
        val all = detailChapters()
        val sorted = all.sortedBy { it.sourceOrder }
        val target = if (unreadOnly) sorted.filter { !it.read } else sorted
        val toDownload = if (amount != null && amount > 0) target.take(amount) else target
        downloadChapters(toDownload)
    }

    fun deleteChapterDownload(chapterId: Long) {
        val manga = detailManga() ?: return
        val ch = detailChapters().find { it.id == chapterId } ?: return
        downloader?.deleteDownloadedChapter(manga, ch)
        onChanged()
    }

    /** Marks a chapter read from surfaces that do not have the detail page open (e.g. Updates). */
    fun markChapterRead(mangaId: Long, chapterId: Long) {
        scope.launch(Dispatchers.IO) {
            val chapter = repository.chapterSnapshot(mangaId).find { it.id == chapterId } ?: return@launch
            if (chapter.read) return@launch
            mutationPort?.updateChapter(
                chapter.copy(read = true, lastModifiedAt = System.currentTimeMillis()).toChapterRecord(),
            )
        }
    }

    /** Enqueues a chapter download from surfaces that do not have the detail page open. */
    fun enqueueChapterDownload(mangaId: Long, chapterId: Long) {
        scope.launch(Dispatchers.IO) {
            val manga = repository.librarySnapshot().firstOrNull { it.id == mangaId } ?: return@launch
            val chapter = repository.chapterSnapshot(mangaId).find { it.id == chapterId } ?: return@launch
            downloader?.enqueue(
                sourceId = manga.sourceId,
                mangaId = manga.id,
                mangaTitle = manga.title,
                chapters = listOf(chapter),
            )
        }
    }

    fun updateMangaInfo(
        mangaId: Long,
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) {
        scope.launch(Dispatchers.IO) {
            val existing = repository.allMangaSnapshot().find { it.id == mangaId } ?: return@launch
            val genreJson = Json.encodeToString(genres)
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                title = title,
                author = author,
                artist = artist,
                description = description,
                genreJson = genreJson,
                status = status,
                notes = notes,
                lastModifiedAt = now,
            )
            mutationPort?.updateManga(updated)
            onEditInfoClosed()
            onChanged()
        }
    }

    fun resetMangaInfo(mangaId: Long) {
        scope.launch(Dispatchers.IO) {
            val existing = repository.allMangaSnapshot().find { it.id == mangaId } ?: return@launch
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                notes = "",
                lastModifiedAt = now,
            )
            mutationPort?.updateManga(updated)
            onEditInfoClosed()
            onChanged()
        }
    }

    fun updateSelectedMangaInfo(
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) {
        val id = detailMangaId() ?: return
        updateMangaInfo(id, title, author, artist, description, genres, status, notes)
    }

    fun resetSelectedMangaInfo() {
        val id = detailMangaId() ?: return
        resetMangaInfo(id)
    }

    fun setDetailFavorite(favorite: Boolean, onResult: (Boolean) -> Unit = {}) {
        val mangaId = detailMangaId()
        if (mangaId == null) {
            onResult(false)
            return
        }
        scope.launch(Dispatchers.IO) {
            val success = runCatching {
                val record = repository.allMangaSnapshot().firstOrNull { it.id == mangaId }
                    ?: return@runCatching false
                if (record.favorite == favorite) return@runCatching true
                val now = System.currentTimeMillis()
                mutationPort?.updateManga(
                    record.copy(
                        favorite = favorite,
                        dateAdded = if (favorite) record.dateAdded.takeIf { it > 0L } ?: now else 0L,
                        lastModifiedAt = now,
                        favoriteModifiedAt = now,
                    ),
                ) ?: return@runCatching false
                onChanged()
                onLibraryChanged()
                true
            }.getOrDefault(false)
            scope.launch { onResult(success) }
        }
    }
}
