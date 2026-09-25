package mihon.sync.engine

import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.Tombstone

data class SyncApplyResult(
    val mangaInserted: Int = 0,
    val mangaMerged: Int = 0,
    val chapterInserted: Int = 0,
    val chapterMerged: Int = 0,
    val categoriesLinked: Int = 0,
    val overriddenItems: List<SyncOverriddenItem> = emptyList(),
)

interface SyncLocalRepository {
    /**
     * Exports local entities that were updated since [sinceCursor].
     */
    suspend fun exportDelta(sinceCursor: Long): EntityDelta

    /**
     * Exports local tombstones that occurred since [sinceCursor].
     */
    suspend fun exportTombstones(sinceCursor: Long): List<Tombstone> = emptyList()

    /**
     * Applies an incoming changeset to the local database, performing LWW merging
     * and reporting any overwritten local state.
     */
    suspend fun applyChangeset(changeset: Changeset): SyncApplyResult
}
