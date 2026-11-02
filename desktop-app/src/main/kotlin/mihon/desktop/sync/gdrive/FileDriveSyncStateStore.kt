package mihon.desktop.sync.gdrive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.sync.engine.SyncStateStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
 * Watermarks/cursors for the Drive transport only, kept apart from the LAN/HTTP sync state so
 * the two transports never skip each other's data.
 */
class FileDriveSyncStateStore(
    private val file: Path,
    private val deviceId: suspend () -> String,
) : SyncStateStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    internal fun read(): DriveSyncStateFile = synchronized(lock) {
        if (!Files.exists(file)) return DriveSyncStateFile()
        runCatching {
            json.decodeFromString<DriveSyncStateFile>(Files.readString(file))
        }.getOrDefault(DriveSyncStateFile())
    }

    internal fun update(block: (DriveSyncStateFile) -> DriveSyncStateFile): Unit = synchronized(lock) {
        val next = block(read())
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, json.encodeToString(DriveSyncStateFile.serializer(), next))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    fun lastSyncAt(): Long = read().lastSyncAt

    fun recordSync(at: Long, summary: String) = update { it.copy(lastSyncAt = at, lastSummary = summary) }

    fun reset() {
        synchronized(lock) { Files.deleteIfExists(file) }
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
}
