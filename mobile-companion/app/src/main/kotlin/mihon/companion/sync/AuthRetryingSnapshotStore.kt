package mihon.companion.sync

import mihon.sync.transport.gdrive.DriveAuthException
import mihon.sync.transport.gdrive.DriveSnapshotStore

/** Retries a Drive call once after a 401, giving [onUnauthorized] the chance to refresh the token. */
class AuthRetryingSnapshotStore(
    private val delegate: DriveSnapshotStore,
    private val onUnauthorized: suspend () -> Unit,
) : DriveSnapshotStore {
    override suspend fun read(): ByteArray? = retrying { delegate.read() }
    override suspend fun write(data: ByteArray) = retrying { delegate.write(data) }
    override suspend fun readAll(): List<ByteArray> = retrying { delegate.readAll() }
    override fun deviceStore(deviceId: String): DriveSnapshotStore =
        AuthRetryingSnapshotStore(delegate.deviceStore(deviceId), onUnauthorized)

    private suspend fun <T> retrying(block: suspend () -> T): T = try {
        block()
    } catch (expired: DriveAuthException) {
        onUnauthorized()
        try {
            block()
        } catch (again: DriveAuthException) {
            again.addSuppressed(expired)
            throw again
        }
    }
}
