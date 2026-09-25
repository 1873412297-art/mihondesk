package mihon.sync.server

import java.io.Closeable

data class ChangesetRecord(
    val deviceId: String,
    val cursor: Long,
    val producedAt: Long,
    val payload: ByteArray,
    val receivedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChangesetRecord) return false
        if (deviceId != other.deviceId) return false
        if (cursor != other.cursor) return false
        if (producedAt != other.producedAt) return false
        if (!payload.contentEquals(other.payload)) return false
        if (receivedAt != other.receivedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + cursor.hashCode()
        result = 31 * result + producedAt.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + receivedAt.hashCode()
        return result
    }
}

interface ChangesetStore : Closeable {
    fun insertOrReplace(record: ChangesetRecord)
    fun getChangesets(sinceCursors: Map<String, Long> = emptyMap(), excludeDeviceId: String = ""): List<ChangesetRecord>
    fun getHeadCursor(excludeDeviceId: String = ""): Long
    fun cleanup(retentionDays: Int = 30, maxChangesets: Int = 5000): Int
}
