package mihon.desktop.sync.gdrive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.desktop.logging.DesktopLogger
import mihon.sync.engine.SyncStateStore
import mihon.sync.transport.gdrive.DriveException
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
    val categoriesFingerprint: String = "",
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

    internal fun read(): DriveSyncStateFile = synchronized(lock) { readLocked() }

    internal fun update(block: (DriveSyncStateFile) -> DriveSyncStateFile): Unit = synchronized(lock) {
        if (preserveCorrupt) {
            // Retry the quarantine: the lock/AV scanner that blocked it may be gone by now.
            preserveCorrupt = false
            readLocked()
            if (preserveCorrupt) {
                throw DriveException(
                    "Cannot persist Drive sync state: corrupt file at $file could not be quarantined",
                )
            }
        }
        val next = block(readLocked())
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, json.encodeToString(DriveSyncStateFile.serializer(), next))
        mihon.desktop.platform.moveReplacing(tmp, file)
        Unit
    }

    /** A corrupt file is moved aside. Until that move succeeds, writes are refused so zeros cannot replace it. */
    private var preserveCorrupt = false

    private fun readLocked(): DriveSyncStateFile {
        if (!Files.exists(file)) return DriveSyncStateFile()
        return try {
            json.decodeFromString<DriveSyncStateFile>(Files.readString(file))
        } catch (error: Exception) {
            DesktopLogger.warn(
                "GoogleDrive",
                "Sync state at $file is corrupt; keeping it until it can be quarantined",
                error,
            )
            quarantineCorrupt()
            DriveSyncStateFile()
        }
    }

    private fun quarantineCorrupt() {
        val quarantine = file.resolveSibling("${file.fileName}.corrupt")
        val moved = runCatching {
            mihon.desktop.platform.moveReplacing(file, quarantine)
        }.onFailure { error ->
            DesktopLogger.warn("GoogleDrive", "Cannot quarantine corrupt sync state at $file", error)
        }.isSuccess
        preserveCorrupt = !moved
    }

    fun lastSyncAt(): Long = read().lastSyncAt

    fun recordSync(at: Long, summary: String) = update { it.copy(lastSyncAt = at, lastSummary = summary) }

    fun reset() {
        synchronized(lock) {
            Files.deleteIfExists(file)
            preserveCorrupt = false
        }
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

    override suspend fun getCategoriesFingerprint(): String = read().categoriesFingerprint

    override suspend fun setCategoriesFingerprint(value: String) =
        update { it.copy(categoriesFingerprint = value) }
}
