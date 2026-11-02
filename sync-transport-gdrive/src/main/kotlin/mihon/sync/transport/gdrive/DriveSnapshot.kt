package mihon.sync.transport.gdrive

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.sync.core.model.Changeset
import java.io.IOException

/** The single library snapshot stored in the Drive appDataFolder. */
@Serializable
data class DriveSyncSnapshot(
    @ProtoNumber(1) val version: Int = 1,
    @ProtoNumber(2) val changesets: List<Changeset> = emptyList(),
)

/** Byte storage for the snapshot. Real implementation: [DriveRestSnapshotStore]. */
interface DriveSnapshotStore {
    suspend fun read(): ByteArray?
    suspend fun write(data: ByteArray)
}

open class DriveException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Token missing/expired/revoked: user has to sign in again. */
class DriveAuthException(message: String, cause: Throwable? = null) : DriveException(message, cause)

class DriveSnapshotCorruptException(message: String, cause: Throwable? = null) : DriveException(message, cause)
