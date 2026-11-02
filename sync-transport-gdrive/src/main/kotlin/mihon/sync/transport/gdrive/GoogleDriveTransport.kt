package mihon.sync.transport.gdrive

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncCursorConflictException
import mihon.sync.transport.api.SyncTransport

/**
 * [SyncTransport] that keeps every device's changesets inside ONE snapshot file in the
 * Google Drive appDataFolder. Older changesets of a device are compacted so the file stays small;
 * compaction keeps the newest cursor so peers' pull watermarks stay valid.
 */
class GoogleDriveTransport(
    private val store: DriveSnapshotStore,
    private val maxChangesetsPerDevice: Int = 8,
    private val maxWriteAttempts: Int = 3,
) : SyncTransport {
    private val mutex = Mutex()

    override suspend fun push(changeset: Changeset) = mutex.withLock {
        var lastError: Exception? = null
        repeat(maxWriteAttempts) {
            val snapshot = readSnapshot()
            val own = snapshot.changesets.filter { it.deviceId == changeset.deviceId }
            val existing = own.firstOrNull { it.cursor == changeset.cursor }
            if (existing != null) {
                if (ProtoBuf.encodeToByteArray(existing) contentEquals
                    ProtoBuf.encodeToByteArray(changeset)
                ) {
                    return@withLock
                }
                throw SyncCursorConflictException(
                    "cursor ${changeset.cursor} already stored with different payload",
                    expectedCursor = own.maxOf { it.cursor } + 1,
                )
            }
            val head = own.maxOfOrNull { it.cursor } ?: 0L
            if (changeset.cursor <= head) {
                throw SyncCursorConflictException("cursor ${changeset.cursor} <= head $head", expectedCursor = head + 1)
            }
            val updated = snapshot.copy(
                changesets = snapshot.changesets.filter { it.deviceId != changeset.deviceId } +
                    compact(own + changeset),
            )
            store.write(ProtoBuf.encodeToByteArray(updated))
            // Read back: another device may have written concurrently (Drive has no cheap CAS here).
            val verify = readSnapshot()
            if (verify.changesets.any { it.deviceId == changeset.deviceId && it.cursor == changeset.cursor }) {
                return@withLock
            }
            lastError = DriveException("snapshot write was overwritten concurrently")
        }
        throw lastError ?: DriveException("push failed")
    }

    override suspend fun pull(sinceCursors: Map<String, Long>, excludeDeviceId: String?): List<Changeset> =
        readSnapshot().changesets
            .filter { it.deviceId != excludeDeviceId }
            .filter { it.cursor > (sinceCursors[it.deviceId] ?: 0L) }
            .sortedWith(compareBy({ it.producedAt }, { it.deviceId }, { it.cursor }))

    override suspend fun headCursor(excludeDeviceId: String?): Long =
        readSnapshot().changesets.filter { it.deviceId != excludeDeviceId }.maxOfOrNull { it.cursor } ?: 0L

    private fun compact(own: List<Changeset>): List<Changeset> {
        val sorted = own.sortedBy { it.cursor }
        if (sorted.size <= maxChangesetsPerDevice) return sorted
        val keep = maxChangesetsPerDevice - 1
        val old = sorted.dropLast(keep)
        return listOf(ChangesetCompactor.merge(old)) + sorted.takeLast(keep)
    }

    private suspend fun readSnapshot(): DriveSyncSnapshot {
        val bytes = store.read() ?: return DriveSyncSnapshot()
        if (bytes.isEmpty()) return DriveSyncSnapshot()
        return try {
            ProtoBuf.decodeFromByteArray<DriveSyncSnapshot>(bytes)
        } catch (e: Exception) {
            throw DriveSnapshotCorruptException("Drive sync snapshot is unreadable", e)
        }
    }
}
