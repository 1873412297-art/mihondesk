package mihon.sync.engine

import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityType

data class SyncOverriddenItem(
    val entityType: EntityType,
    val entityKey: String,
    val localWatermark: Long,
    val remoteWatermark: Long,
    val reason: String,
)

data class SyncReport(
    val success: Boolean,
    val pulledChangesetCount: Int,
    val mangaInserted: Int,
    val mangaMerged: Int,
    val chapterInserted: Int,
    val chapterMerged: Int,
    val categoriesLinked: Int,
    val pushedChangeset: Changeset?,
    val overriddenItems: List<SyncOverriddenItem> = emptyList(),
    val errorMessage: String? = null,
    val durationMs: Long = 0L,
) {
    val totalChanged: Int get() = mangaInserted + mangaMerged + chapterInserted + chapterMerged + categoriesLinked
}
