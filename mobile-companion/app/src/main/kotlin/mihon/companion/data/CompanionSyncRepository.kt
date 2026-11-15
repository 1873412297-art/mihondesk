package mihon.companion.data

import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncLocalRepository

/**
 * [SyncLocalRepository] over [LibraryStore].
 *
 * Unlike the desktop, the phone does not export "rows modified after a timestamp": data imported
 * from a Mihon backup may carry old timestamps and would never be pushed. Instead it exports every
 * entry changed locally since the last acknowledged push ([StoredLibrary.ackedSeq]). After a
 * successful push the caller acknowledges [exportedSeq].
 */
class CompanionSyncRepository(
    private val store: LibraryStore,
    private val deviceId: suspend () -> String,
) : SyncLocalRepository {

    /** The library seq covered by the last [exportDelta]; acknowledge it once the push succeeded. */
    var exportedSeq: Long = 0
        private set

    override suspend fun exportDelta(sinceCursor: Long): EntityDelta {
        val library = store.load()
        exportedSeq = library.seq
        val dirty = library.dirtyMangas()
        if (dirty.isEmpty() && !library.hasDirtyCategories()) return EntityDelta()
        val self = deviceId()
        val categories = library.categories.map { it.category }
        val mangas = dirty.map { stored ->
            val manga = stored.manga
            val memo = if (SyncMergePolicy.extractDeviceIdFromMemoBytes(manga.memo).isBlank()) {
                SyncMergePolicy.injectDeviceIdIntoMemoBytes(manga.memo, self)
            } else {
                manga.memo
            }
            manga.copy(categories = CategoryNames.toOrders(stored.categoryNames, categories), memo = memo)
        }
        return EntityDelta(mangas = mangas, categories = categories, sources = library.sources)
    }

    override suspend fun applyChangeset(changeset: Changeset): SyncApplyResult {
        val incoming = IncomingLibrary(
            mangas = changeset.upserts.mangas,
            categories = changeset.upserts.categories,
            sources = changeset.upserts.sources,
            deviceId = changeset.deviceId,
        )
        val stats = store.update { LibraryMerge.merge(it, incoming, markDirty = false) }
        return SyncApplyResult(
            mangaInserted = stats.mangaInserted,
            mangaMerged = stats.mangaMerged,
            categoriesLinked = stats.categoriesAdded,
        )
    }
}
