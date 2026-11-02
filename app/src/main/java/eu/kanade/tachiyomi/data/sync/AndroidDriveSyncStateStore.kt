package eu.kanade.tachiyomi.data.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.sync.engine.SyncStateStore
import java.io.File

@Serializable
internal data class DriveSyncStateFile(
    val lastPushWatermark: Long = 0,
    val lastPushCursor: Long = 0,
    val currentCursor: Long = 0,
    val peers: Map<String, Long> = emptyMap(),
    val lastSyncAt: Long = 0,
    val lastSummary: String = "",
)

/**
 * Watermarks and cursors for Google Drive sync, kept in a small JSON file and fully separate from
 * the legacy [AndroidSyncStateStore] tables (only the device id is shared).
 */
class AndroidDriveSyncStateStore(
    private val file: File,
    private val deviceId: suspend () -> String,
) : SyncStateStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    internal fun read(): DriveSyncStateFile = synchronized(lock) {
        if (!file.exists()) return DriveSyncStateFile()
        runCatching { json.decodeFromString<DriveSyncStateFile>(file.readText()) }
            .getOrDefault(DriveSyncStateFile())
    }

    private fun update(block: (DriveSyncStateFile) -> DriveSyncStateFile): Unit = synchronized(lock) {
        val next = block(read())
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(DriveSyncStateFile.serializer(), next))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not write ${file.name}" }
        }
    }

    fun lastSyncAt(): Long = read().lastSyncAt

    fun lastSummary(): String = read().lastSummary

    fun recordSync(at: Long, summary: String) = update { it.copy(lastSyncAt = at, lastSummary = summary) }

    fun reset() {
        synchronized(lock) { file.delete() }
    }

    override suspend fun getDeviceId(): String = deviceId()

    override suspend fun getLastPushWatermark(): Long = read().lastPushWatermark

    override suspend fun setLastPushWatermark(watermark: Long) = update { it.copy(lastPushWatermark = watermark) }

    override suspend fun getLastPushCursor(): Long = read().lastPushCursor

    override suspend fun setLastPushCursor(cursor: Long) =
        update { it.copy(lastPushCursor = cursor, currentCursor = maxOf(it.currentCursor, cursor)) }

    override suspend fun nextCursor(): Long {
        var next = 0L
        update { s -> s.copy(currentCursor = s.currentCursor + 1).also { next = it.currentCursor } }
        return next
    }

    override suspend fun getLastPullWatermark(peerDeviceId: String): Long = read().peers[peerDeviceId] ?: 0L

    override suspend fun setLastPullWatermark(peerDeviceId: String, watermark: Long) =
        update { it.copy(peers = it.peers + (peerDeviceId to watermark)) }

    override suspend fun getAllPeerPullWatermarks(): Map<String, Long> = read().peers

    companion object {
        fun file(context: Context): File = File(context.filesDir, "sync/gdrive-state.json")
    }
}
