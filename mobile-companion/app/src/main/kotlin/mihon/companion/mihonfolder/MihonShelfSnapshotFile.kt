package mihon.companion.mihonfolder

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.companion.CompanionLog
import mihon.companion.data.MihonShelfSnapshot
import java.io.File

/** The previous Mihon backup's shelf and read chapters, kept to detect what the user undid. */
internal class MihonShelfSnapshotFile(private val file: File) {
    /** null when there is nothing to compare with yet (first import, new folder, unreadable file). */
    fun read(): MihonShelfSnapshot? {
        if (!file.exists()) return null
        return try {
            ProtoBuf.decodeFromByteArray<MihonShelfSnapshot>(file.readBytes())
        } catch (error: Exception) {
            CompanionLog.warn(TAG, "Previous Mihon snapshot unreadable; this import detects no undo", error)
            null
        }
    }

    fun write(snapshot: MihonShelfSnapshot) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(ProtoBuf.encodeToByteArray(snapshot))
        if (!temp.renameTo(file)) CompanionLog.warn(TAG, "Could not save the Mihon snapshot")
    }

    fun clear() {
        if (file.exists() && !file.delete()) CompanionLog.warn(TAG, "Could not delete the Mihon snapshot")
    }

    private companion object {
        const val TAG = "MihonSnapshot"
    }
}
