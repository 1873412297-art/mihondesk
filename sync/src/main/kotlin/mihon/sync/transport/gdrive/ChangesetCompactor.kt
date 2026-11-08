package mihon.sync.transport.gdrive

import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.Changeset

/** Folds several changesets of one device into one using the shared per-item last-modified-wins policy. */
object ChangesetCompactor {
    fun merge(changesets: List<Changeset>): Changeset {
        require(changesets.isNotEmpty())
        val sorted = changesets.sortedBy { it.cursor }
        val last = sorted.last()
        val merged = sorted.drop(1).fold(sorted.first()) { acc, next -> SyncMergePolicy.mergeChangeset(acc, next) }
        return merged.copy(
            deviceId = last.deviceId,
            cursor = last.cursor,
            producedAt = sorted.maxOf { it.producedAt },
            upserts = merged.upserts.copy(
                sources = sorted.flatMap { it.upserts.sources }.distinct(),
                preferences = sorted.reversed().flatMap { it.upserts.preferences }.distinctBy { it.key },
                sourcePreferences = sorted.reversed().flatMap {
                    it.upserts.sourcePreferences
                }.distinctBy { it.sourceKey },
            ),
        )
    }
}
