package eu.kanade.tachiyomi.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.sync.engine.SyncStateStore
import tachiyomi.data.Database
import java.util.UUID

class AndroidSyncStateStore(
    private val database: Database,
    private val defaultDeviceId: () -> String = { UUID.randomUUID().toString() },
) : SyncStateStore {
    private val queries = database.sync_stateQueries
    private val peerQueries = database.sync_peer_stateQueries

    override suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        val existing = queries.getFirstSyncState().awaitAsOneOrNull()
        if (existing != null && existing.device_id.isNotBlank()) {
            existing.device_id
        } else {
            val generated = defaultDeviceId()
            val now = System.currentTimeMillis()
            queries.upsertSyncState(
                deviceId = generated,
                lastPushCursor = 0L,
                lastPullCursor = 0L,
                updatedAt = now,
                syncClock = 0L,
                lastPushWatermark = 0L,
            )
            generated
        }
    }

    override suspend fun getLastPushWatermark(): Long = withContext(Dispatchers.IO) {
        queries.getFirstSyncState().awaitAsOneOrNull()?.last_push_watermark ?: 0L
    }

    override suspend fun setLastPushWatermark(watermark: Long): Unit = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()
        queries.updateLastPushWatermark(
            lastPushWatermark = watermark,
            updatedAt = System.currentTimeMillis(),
            deviceId = deviceId,
        )
    }

    override suspend fun getLastPushCursor(): Long = withContext(Dispatchers.IO) {
        queries.getFirstSyncState().awaitAsOneOrNull()?.last_push_cursor ?: 0L
    }

    override suspend fun setLastPushCursor(cursor: Long): Unit = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()
        queries.updateLastPushCursor(
            lastPushCursor = cursor,
            updatedAt = System.currentTimeMillis(),
            deviceId = deviceId,
        )
    }

    override suspend fun getLastPullCursor(): Long = withContext(Dispatchers.IO) {
        queries.getFirstSyncState().awaitAsOneOrNull()?.last_pull_cursor ?: 0L
    }

    override suspend fun setLastPullCursor(cursor: Long): Unit = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId()
        queries.updateLastPullCursor(
            lastPullCursor = cursor,
            updatedAt = System.currentTimeMillis(),
            deviceId = deviceId,
        )
    }

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long = withContext(Dispatchers.IO) {
        peerQueries.getPeerWatermark(peerDeviceId).awaitAsOneOrNull() ?: 0L
    }

    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long): Unit = withContext(
        Dispatchers.IO,
    ) {
        peerQueries.upsertPeerWatermark(
            peerDeviceId = peerDeviceId,
            lastPullWatermark = watermark,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> = withContext(Dispatchers.IO) {
        peerQueries.getAllPeerStates().awaitAsList().associate {
            it.peer_device_id to it.last_pull_watermark
        }
    }

    override suspend fun nextCursor(): Long = withContext(Dispatchers.IO) {
        database.transactionWithResult {
            val state = queries.getFirstSyncState().awaitAsOneOrNull()
            val deviceId = state?.device_id ?: getDeviceId()
            val currentClock = state?.sync_clock ?: 0L
            val next = currentClock + 1L
            val now = System.currentTimeMillis()
            if (state == null) {
                queries.upsertSyncState(
                    deviceId = deviceId,
                    lastPushCursor = 0L,
                    lastPullCursor = 0L,
                    updatedAt = now,
                    syncClock = next,
                    lastPushWatermark = 0L,
                )
            } else {
                queries.updateSyncClock(
                    syncClock = next,
                    updatedAt = now,
                    deviceId = deviceId,
                )
            }
            next
        }
    }
}
