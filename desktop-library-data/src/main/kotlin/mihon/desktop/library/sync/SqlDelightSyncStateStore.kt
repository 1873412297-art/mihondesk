package mihon.desktop.library.sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.desktop.library.backup.BackupMergePolicy
import mihon.desktop.library.backup.memoJson
import mihon.desktop.library.backup.mergeWith
import mihon.desktop.library.backup.toChapterRecord
import mihon.desktop.library.backup.toMangaRecord
import mihon.desktop.library.backup.toTrackingRecord
import mihon.desktop.library.db.DesktopLibraryDatabase
import mihon.desktop.library.db.Tracking
import mihon.desktop.library.db.toChapterRecord
import mihon.desktop.library.db.toMangaRecord
import mihon.desktop.library.db.toTrackingRecord
import mihon.desktop.library.model.MangaRecord
import mihon.sync.core.merge.SyncMergePolicy
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupHistory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupTracking
import mihon.sync.core.model.AndroidUpdateStrategy
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.core.model.EntityType
import mihon.sync.core.model.Tombstone
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.engine.SyncOverriddenItem
import mihon.sync.engine.SyncStateStore
import java.util.Locale
import java.util.UUID

class SqlDelightSyncStateStore(
    private val database: DesktopLibraryDatabase,
    private val defaultDeviceId: () -> String = { UUID.randomUUID().toString() },
) : SyncStateStore {
    private val queries = database.libraryQueries

    override suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        val existing = queries.selectMetadata("sync_device_id").executeAsOneOrNull()
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = defaultDeviceId()
            queries.upsertMetadata("sync_device_id", generated)
            generated
        }
    }

    override suspend fun getLastPushWatermark(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_push_watermark").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPushWatermark(watermark: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_push_watermark", watermark.toString())
        }
    }

    override suspend fun getLastPushCursor(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_push_cursor").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPushCursor(cursor: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_push_cursor", cursor.toString())
        }
    }

    override suspend fun getLastPullCursor(): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_last_pull_cursor").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPullCursor(cursor: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_last_pull_cursor", cursor.toString())
        }
    }

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long = withContext(Dispatchers.IO) {
        queries.selectMetadata("sync_peer_pull_$peerDeviceId").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long) {
        withContext(Dispatchers.IO) {
            queries.upsertMetadata("sync_peer_pull_$peerDeviceId", watermark.toString())
        }
    }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> = withContext(Dispatchers.IO) {
        queries.selectAllMetadataWithPrefix("sync_peer_pull_%").executeAsList().associate {
            it.name.removePrefix("sync_peer_pull_") to (it.value_.toLongOrNull() ?: 0L)
        }
    }

    override suspend fun nextCursor(): Long = withContext(Dispatchers.IO) {
        database.transactionWithResult {
            val current = queries.selectMetadata("sync_clock").executeAsOneOrNull()?.toLongOrNull() ?: 0L
            val next = current + 1L
            queries.upsertMetadata("sync_clock", next.toString())
            next
        }
    }
}
