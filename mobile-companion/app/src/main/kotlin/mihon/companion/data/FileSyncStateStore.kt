package mihon.companion.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.companion.CompanionLog
import mihon.sync.engine.SyncStateStore
import java.io.File
import java.io.IOException
import java.util.UUID

@Serializable
data class SyncStateFile(
    val deviceId: String = "",
    val lastPushWatermark: Long = 0,
    val lastPushCursor: Long = 0,
    val currentCursor: Long = 0,
    val peers: Map<String, Long> = emptyMap(),
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    val accountEmail: String? = null,
    val autoSync: Boolean = true,
)

/** Cursor/watermark bookkeeping for [mihon.sync.engine.SyncEngine], plus the phone's sync settings. */
class FileSyncStateStore(private val file: File) : SyncStateStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun read(): SyncStateFile = synchronized(lock) { readLocked() }

    fun update(block: (SyncStateFile) -> SyncStateFile): SyncStateFile = synchronized(lock) {
        val next = block(readLocked())
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(SyncStateFile.serializer(), next))
        if (!temp.renameTo(file)) throw IOException("Could not replace ${file.name}")
        next
    }

    /** Forget every peer watermark so the next sync pulls the whole library from Drive again. */
    fun resetPeers() {
        update { it.copy(peers = emptyMap()) }
    }

    private fun readLocked(): SyncStateFile {
        val current = if (file.exists()) {
            try {
                json.decodeFromString<SyncStateFile>(file.readText())
            } catch (error: Exception) {
                val aside = File(file.parentFile, file.name + ".corrupt")
                CompanionLog.warn(TAG, "Sync state unreadable, moved aside; peers will be pulled again", error)
                file.renameTo(aside)
                SyncStateFile()
            }
        } else {
            SyncStateFile()
        }
        if (current.deviceId.isNotBlank()) return current
        // A fresh phone gets its own peer identity; Drive keeps one document per device id.
        val created = current.copy(deviceId = "android-" + UUID.randomUUID().toString())
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(SyncStateFile.serializer(), created))
        return created
    }

    override suspend fun getDeviceId(): String = read().deviceId
    override suspend fun getLastPushWatermark(): Long = read().lastPushWatermark
    override suspend fun setLastPushWatermark(watermark: Long) {
        update { it.copy(lastPushWatermark = watermark) }
    }

    override suspend fun getLastPushCursor(): Long = read().lastPushCursor
    override suspend fun setLastPushCursor(cursor: Long) {
        update { it.copy(lastPushCursor = cursor, currentCursor = maxOf(it.currentCursor, cursor)) }
    }

    override suspend fun nextCursor(): Long = update { it.copy(currentCursor = it.currentCursor + 1) }.currentCursor

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long = read().peers[peerDeviceId] ?: 0L
    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long) {
        update { it.copy(peers = it.peers + (peerDeviceId to watermark)) }
    }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> = read().peers

    private companion object {
        const val TAG = "SyncState"
    }
}
