package mihon.sync.engine

interface SyncStateStore {
    suspend fun getDeviceId(): String

    suspend fun getLastPushWatermark(): Long
    suspend fun setLastPushWatermark(watermark: Long)

    suspend fun getLastPushCursor(): Long
    suspend fun setLastPushCursor(cursor: Long)

    suspend fun nextCursor(): Long

    suspend fun getLastPullWatermark(peerDeviceId: String): Long
    suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long)
    suspend fun getAllPeerPullWatermarks(): Map<String, Long>

    // Legacy/backward compatibility helpers:
    suspend fun getLastPullCursor(): Long = 0L
    suspend fun setLastPullCursor(cursor: Long) {}
    suspend fun getLastPullCursor(peerDeviceId: String): Long = getLastPullWatermark(peerDeviceId)
    suspend fun setLastPullCursor(peerDeviceId: String, cursor: Long) = setLastPullWatermark(peerDeviceId, cursor)
}

class InMemorySyncStateStore(
    private val deviceId: String,
    private var lastPushWatermark: Long = 0L,
    private var lastPushCursor: Long = 0L,
    private var currentCursor: Long = 0L,
    private val peerPullWatermarks: MutableMap<String, Long> = mutableMapOf(),
) : SyncStateStore {

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getLastPushWatermark(): Long = lastPushWatermark

    override suspend fun setLastPushWatermark(watermark: Long) {
        lastPushWatermark = watermark
    }

    override suspend fun getLastPushCursor(): Long = lastPushCursor

    override suspend fun setLastPushCursor(cursor: Long) {
        lastPushCursor = cursor
        if (cursor > currentCursor) {
            currentCursor = cursor
        }
    }

    override suspend fun nextCursor(): Long {
        currentCursor++
        return currentCursor
    }

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long =
        peerPullWatermarks[peerDeviceId] ?: 0L

    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long) {
        peerPullWatermarks[peerDeviceId] = watermark
    }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> =
        peerPullWatermarks.toMap()
}
