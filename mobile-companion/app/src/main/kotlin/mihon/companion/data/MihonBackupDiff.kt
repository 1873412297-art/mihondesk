package mihon.companion.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.sync.core.merge.ReadIntent
import mihon.sync.core.model.MangaKey

/** What the previous Mihon backup said: which mangas were on the shelf and which chapters were read. */
@Serializable
data class MihonShelfSnapshot(@ProtoNumber(1) val entries: List<MihonShelfEntry> = emptyList())

@Serializable
data class MihonShelfEntry(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val favorite: Boolean,
    @ProtoNumber(4) val readChapters: List<String> = emptyList(),
) {
    val key: MangaKey get() = MangaKey(source, url)
}

/** Deliberate backwards steps the user took in Mihon between two backups. */
data class MihonChanges(
    val removedFromShelf: Set<MangaKey> = emptySet(),
    val markedUnread: Map<MangaKey, Set<String>> = emptyMap(),
) {
    val isEmpty: Boolean get() = removedFromShelf.isEmpty() && markedUnread.isEmpty()
}

/**
 * A Mihon backup is only a state, not a history, so "read" can never be taken back by merging it
 * alone. Comparing it with the previous backup reveals what the user undid in Mihon: a chapter
 * that was read and now is not was marked unread; a manga that was on the shelf and now is not
 * was removed. Those become timed intents that sync weighs against the other devices.
 */
object MihonBackupDiff {
    fun snapshot(backup: MihonBackup) = MihonShelfSnapshot(
        backup.backupManga.map { manga ->
            MihonShelfEntry(manga.source, manga.url, manga.favorite, manga.chapters.filter { it.read }.map { it.url })
        },
    )

    fun changes(previous: MihonShelfSnapshot, current: MihonBackup): MihonChanges {
        val now = current.backupManga.associateBy { MangaKey(it.source, it.url) }
        val removed = previous.entries
            .filter { it.favorite && now[it.key]?.favorite != true }
            .map { it.key }
            .toSet()
        val unread = previous.entries.mapNotNull { entry ->
            val chapters = now[entry.key]?.chapters ?: return@mapNotNull null
            val wasRead = entry.readChapters.toSet()
            val urls = chapters.filter { !it.read && it.url in wasRead }.map { it.url }.toSet()
            if (urls.isEmpty()) null else entry.key to urls
        }.toMap()
        return MihonChanges(removed, unread)
    }

    /**
     * Applies [changes] as made at [at] (the backup's time). Each one only wins against data that
     * is older: a read or a shelf addition made later on another device stays.
     */
    fun apply(library: StoredLibrary, changes: MihonChanges, at: Long): StoredLibrary {
        if (changes.isEmpty) return library
        val seq = library.seq + 1
        var touched = false
        val mangas = library.mangas.map { stored ->
            val removed = stored.key in changes.removedFromShelf && stored.manga.favorite &&
                (stored.manga.favoriteModifiedAt ?: 0L) < at
            val unread = changes.markedUnread[stored.key].orEmpty()
            if (!removed && unread.isEmpty()) return@map stored
            touched = true
            val manga = stored.manga
            val chapters = manga.chapters.map { chapter ->
                if (chapter.url !in unread) return@map chapter
                val memo = ReadIntent.stamp(chapter.memo.decodeToString(), read = false, at = at)
                val stillRead =
                    ReadIntent.effectiveReadAt(ReadIntent.Side(chapter.read, chapter.lastModifiedAt, memo)) > at
                chapter.copy(
                    read = stillRead,
                    lastPageRead = if (stillRead) chapter.lastPageRead else 0,
                    lastModifiedAt = maxOf(chapter.lastModifiedAt, at),
                    memo = memo.encodeToByteArray(),
                )
            }
            stored.copy(
                manga = manga.copy(
                    chapters = chapters,
                    favorite = if (removed) false else manga.favorite,
                    favoriteModifiedAt = if (removed) at else manga.favoriteModifiedAt,
                    lastModifiedAt = maxOf(manga.lastModifiedAt, at),
                ),
                dirtySeq = seq,
            )
        }
        return if (touched) library.copy(mangas = mangas, seq = seq) else library
    }
}
