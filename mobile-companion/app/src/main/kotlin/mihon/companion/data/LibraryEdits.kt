package mihon.companion.data

import mihon.sync.core.merge.ReadIntent
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.MangaKey

/** Local edits made on the phone. Every edit stamps "now" and marks the entry dirty for the next push. */
object LibraryEdits {
    fun setChaptersRead(
        library: StoredLibrary,
        key: MangaKey,
        chapterUrls: Set<String>,
        read: Boolean,
        now: Long,
        deviceId: String,
    ): StoredLibrary = editManga(library, key) { stored ->
        val manga = stored.manga
        val chapters = manga.chapters.map { chapter ->
            if (chapter.url !in chapterUrls || chapter.read == read) {
                chapter
            } else {
                chapter.copy(
                    read = read,
                    lastPageRead = if (read) chapter.lastPageRead else 0,
                    lastModifiedAt = now,
                    memo = ReadIntent.stamp(
                        SyncMergePolicy.injectDeviceIdIntoMemoBytes(chapter.memo, deviceId).decodeToString(),
                        read,
                        now,
                    ).encodeToByteArray(),
                )
            }
        }
        val newest = manga.chapters.filter { it.url in chapterUrls }.maxByOrNull { it.chapterNumber }?.url
        val history = if (read && newest != null) touchHistory(manga.history, newest, now) else manga.history
        stored.copy(manga = manga.copy(chapters = chapters, history = history, lastModifiedAt = now))
    }

    fun setFavorite(
        library: StoredLibrary,
        key: MangaKey,
        favorite: Boolean,
        now: Long,
        deviceId: String,
    ): StoredLibrary =
        editManga(library, key) { stored ->
            stored.copy(
                manga = stored.manga.copy(
                    favorite = favorite,
                    favoriteModifiedAt = now,
                    lastModifiedAt = now,
                    dateAdded = if (favorite && stored.manga.dateAdded == 0L) now else stored.manga.dateAdded,
                    memo = SyncMergePolicy.injectDeviceIdIntoMemoBytes(stored.manga.memo, deviceId),
                ),
            )
        }

    /** Only the highest-numbered chapter of a "mark read" batch becomes the history entry. */
    private fun touchHistory(history: List<AndroidBackupHistory>, url: String, now: Long): List<AndroidBackupHistory> {
        val previous = history.firstOrNull { it.url == url }
        return history.filterNot { it.url == url } +
            AndroidBackupHistory(url = url, lastRead = now, readDuration = previous?.readDuration ?: 0)
    }

    private inline fun editManga(
        library: StoredLibrary,
        key: MangaKey,
        edit: (StoredManga) -> StoredManga,
    ): StoredLibrary {
        val seq = library.seq + 1
        var changed = false
        val mangas = library.mangas.map { stored ->
            if (stored.key != key) {
                stored
            } else {
                changed = true
                edit(stored).copy(dirtySeq = seq)
            }
        }
        return if (changed) library.copy(mangas = mangas, seq = seq) else library
    }
}
