package mihon.companion.data

import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.merge.mergeCategory
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupSource
import mihon.sync.core.model.CategoryKey
import mihon.sync.core.model.MangaKey

/** What a merge brought in, for the sync / import summaries. */
data class MergeStats(val mangaInserted: Int = 0, val mangaMerged: Int = 0, val categoriesAdded: Int = 0)

/** Incoming library data: a peer's changeset from Drive, or a Mihon backup file. */
data class IncomingLibrary(
    val mangas: List<AndroidBackupManga>,
    val categories: List<AndroidBackupCategory>,
    val sources: List<AndroidBackupSource> = emptyList(),
    /** Device that produced the data; breaks last-write-wins ties exactly like the desktop. */
    val deviceId: String,
)

/**
 * Pure merge of incoming data into the phone library, using the shared [SyncMergePolicy] so phone
 * and desktop converge on identical results.
 *
 * [markDirty] = true is used for Mihon backup imports: whatever changed must be pushed to Drive.
 * Data pulled from Drive is never marked dirty, so it is not echoed back.
 */
object LibraryMerge {
    /** Mihon's own sources: the local-files source has no cross-device identity. */
    private const val LOCAL_SOURCE_ID = 0L

    fun merge(library: StoredLibrary, incoming: IncomingLibrary, markDirty: Boolean): Pair<StoredLibrary, MergeStats> {
        val dirtySeq = if (markDirty) library.seq + 1 else 0L
        val (categories, categoriesAdded) = mergeCategories(library.categories, incoming, dirtySeq)
        val byKey = LinkedHashMap<MangaKey, StoredManga>()
        library.mangas.forEach { byKey[it.key] = it }
        var inserted = 0
        var merged = 0
        for (manga in incoming.mangas) {
            if (manga.source == LOCAL_SOURCE_ID) continue
            val key = MangaKey(manga.source, manga.url)
            val names = CategoryNames.fromOrders(manga.categories, incoming.categories)
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = StoredManga(manga.copy(categories = emptyList()), names, dirtySeq)
                inserted++
                continue
            }
            val result = SyncMergePolicy.mergeManga(
                existing = existing.manga,
                incoming = manga.copy(categories = emptyList()),
                incomingDeviceId = incoming.deviceId,
            )
            val mergedNames = (existing.categoryNames + names).distinctBy(CategoryKey::normalize)
            if (!sameManga(result, existing.manga) || mergedNames.size != existing.categoryNames.size) {
                byKey[key] = existing.copy(
                    manga = result,
                    categoryNames = mergedNames,
                    dirtySeq = if (markDirty) dirtySeq else existing.dirtySeq,
                )
                merged++
            }
        }
        val changed = inserted + merged + categoriesAdded > 0
        val updated = library.copy(
            mangas = byKey.values.toList(),
            categories = categories,
            sources = mergeSources(library.sources, incoming.sources),
            seq = if (markDirty && changed) dirtySeq else library.seq,
        )
        return updated to MergeStats(inserted, merged, categoriesAdded)
    }

    private fun mergeCategories(
        local: List<StoredCategory>,
        incoming: IncomingLibrary,
        dirtySeq: Long,
    ): Pair<List<StoredCategory>, Int> {
        val result = local.toMutableList()
        var added = 0
        for (category in incoming.categories) {
            val index = result.indexOfFirst {
                CategoryKey.normalize(it.category.name) ==
                    CategoryKey.normalize(category.name)
            }
            if (index < 0) {
                // A category new to this device takes the next free position here, not the peer's.
                val order = (result.maxOfOrNull { it.category.order } ?: -1L) + 1
                result += StoredCategory(category.copy(order = order, id = 0), dirtySeq)
                added++
            } else {
                val current = result[index]
                val merged = SyncMergePolicy.mergeCategory(current.category, incoming = category)
                    .copy(order = current.category.order, name = current.category.name)
                if (merged != current.category) result[index] = current.copy(category = merged)
            }
        }
        return result to added
    }

    private fun mergeSources(
        local: List<AndroidBackupSource>,
        incoming: List<AndroidBackupSource>,
    ): List<AndroidBackupSource> {
        if (incoming.isEmpty()) return local
        val byId = LinkedHashMap<Long, AndroidBackupSource>()
        local.forEach { byId[it.sourceId] = it }
        incoming.forEach { source ->
            val current = byId[source.sourceId]
            if (current == null || current.name.isBlank()) byId[source.sourceId] = source
        }
        return byId.values.toList()
    }

    private fun sameManga(a: AndroidBackupManga, b: AndroidBackupManga): Boolean =
        ProtoBuf.encodeToByteArray(a).contentEquals(ProtoBuf.encodeToByteArray(b))
}
