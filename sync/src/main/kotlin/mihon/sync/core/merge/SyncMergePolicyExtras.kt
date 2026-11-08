package mihon.sync.core.merge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.CategoryKey
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.MangaKey
import mihon.sync.core.model.Tombstone
import mihon.sync.core.model.categoryKey
import mihon.sync.core.model.mangaKey

fun SyncMergePolicy.mergeHistory(
    existing: AndroidBackupHistory,
    incoming: AndroidBackupHistory,
): AndroidBackupHistory = existing.copy(
    lastRead = maxOf(existing.lastRead, incoming.lastRead),
    readDuration = maxOf(existing.readDuration, incoming.readDuration),
)

fun SyncMergePolicy.mergeCategory(
    existing: AndroidBackupCategory,
    existingDeviceId: String = "",
    incoming: AndroidBackupCategory,
    incomingDeviceId: String = "",
): AndroidBackupCategory {
    // Same LWW rule as mangas: newer timestamp wins, device id breaks ties.
    val incomingWins = when {
        incoming.lastModifiedAt != existing.lastModifiedAt -> incoming.lastModifiedAt > existing.lastModifiedAt
        incoming.flags != existing.flags -> incoming.flags > existing.flags
        else -> incomingDeviceId > existingDeviceId
    }
    val selected = if (incomingWins) incoming else existing
    return existing.copy(
        name = selectedString(existing.name, incoming.name, incomingWins),
        order = maxOf(existing.order, incoming.order),
        flags = selected.flags,
        lastModifiedAt = maxOf(existing.lastModifiedAt, incoming.lastModifiedAt),
    )
}

fun SyncMergePolicy.selectedString(existing: String, incoming: String, incomingWins: Boolean): String {
    val winner = if (incomingWins) incoming else existing
    val loser = if (incomingWins) existing else incoming
    return if (winner.isNotBlank()) winner else loser
}

fun SyncMergePolicy.selectedNullableString(existing: String?, incoming: String?, incomingWins: Boolean): String? {
    val winner = if (incomingWins) incoming else existing
    val loser = if (incomingWins) existing else incoming
    return if (!winner.isNullOrBlank()) winner else loser
}

fun SyncMergePolicy.selectedByteArray(existing: ByteArray, incoming: ByteArray, incomingWins: Boolean): ByteArray {
    val winner = if (incomingWins) incoming else existing
    val loser = if (incomingWins) existing else incoming
    return if (winner.isNotEmpty()) winner else loser
}

fun SyncMergePolicy.earliestNonzero(existing: Long, incoming: Long): Long = when {
    existing == 0L -> incoming
    incoming == 0L -> existing
    else -> minOf(existing, incoming)
}

fun SyncMergePolicy.maxNullable(existing: Long?, incoming: Long?): Long? = when {
    existing == null -> incoming
    incoming == null -> existing
    else -> maxOf(existing, incoming)
}

fun SyncMergePolicy.unionStringLists(existing: List<String>, incoming: List<String>): List<String> =
    (existing + incoming).distinct().sortedWith(UNICODE_CODE_POINT_COMPARATOR)
