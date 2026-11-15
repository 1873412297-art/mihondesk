package mihon.companion.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupSource
import mihon.sync.core.model.MangaKey

/**
 * The phone's whole library, persisted as one protobuf file. The phone is a regular sync peer:
 * entries carry the same backup DTOs the desktop pushes to Drive, so nothing is lost in transit.
 *
 * Local changes are tracked with a monotonically increasing [seq]: every locally changed entry
 * records the seq it was changed at, and [ackedSeq] is the highest seq that reached Drive.
 */
@Serializable
data class StoredLibrary(
    @ProtoNumber(1) val mangas: List<StoredManga> = emptyList(),
    @ProtoNumber(2) val categories: List<StoredCategory> = emptyList(),
    @ProtoNumber(3) val sources: List<AndroidBackupSource> = emptyList(),
    @ProtoNumber(4) val seq: Long = 0,
    @ProtoNumber(5) val ackedSeq: Long = 0,
) {
    fun dirtyMangas(): List<StoredManga> = mangas.filter { it.dirtySeq > ackedSeq }
    fun hasDirtyCategories(): Boolean = categories.any { it.dirtySeq > ackedSeq }
    val pendingChanges: Int get() = dirtyMangas().size + categories.count { it.dirtySeq > ackedSeq }
    fun sourceName(sourceId: Long): String? = sources.firstOrNull { it.sourceId == sourceId }?.name?.ifBlank { null }
}

/** One manga. [categoryNames] replaces the order-based `manga.categories` of the wire format. */
@Serializable
data class StoredManga(
    @ProtoNumber(1) val manga: AndroidBackupManga,
    @ProtoNumber(2) val categoryNames: List<String> = emptyList(),
    @ProtoNumber(3) val dirtySeq: Long = 0,
) {
    val key: MangaKey get() = MangaKey(manga.source, manga.url)
}

@Serializable
data class StoredCategory(
    @ProtoNumber(1) val category: AndroidBackupCategory,
    @ProtoNumber(2) val dirtySeq: Long = 0,
)
