package eu.kanade.tachiyomi.data.sync

import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncTransport
import mihon.sync.transport.file.FileTransport
import java.io.IOException

@OptIn(ExperimentalSerializationApi::class)
class UniFileSyncTransport(
    private val directory: UniFile,
    private val localDeviceId: String,
    private val protoBuf: ProtoBuf = ProtoBuf,
) : SyncTransport {

    override suspend fun push(changeset: Changeset): Unit = withContext(Dispatchers.IO) {
        val targetFileName = "${changeset.deviceId}.${changeset.cursor}.pb"
        val tempFileName = ".sync-${System.currentTimeMillis()}-${changeset.cursor}.tmp"
        val tempFile = directory.createFile(tempFileName)
            ?: throw IOException("Failed to create temporary sync file: $tempFileName")
        try {
            val bytes = protoBuf.encodeToByteArray(Changeset.serializer(), changeset)
            tempFile.openOutputStream().use { it.write(bytes) }
            val existing = directory.findFile(targetFileName)
            existing?.delete()
            if (!tempFile.renameTo(targetFileName)) {
                // If renameTo is not supported by SAF provider, write directly and clean temp
                val targetFile = directory.createFile(targetFileName)
                    ?: throw IOException("Failed to create target sync file: $targetFileName")
                targetFile.openOutputStream().use { it.write(bytes) }
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    override suspend fun pull(
        sinceCursors: Map<String, Long>,
        excludeDeviceId: String?,
    ): List<Changeset> = withContext(
        Dispatchers.IO,
    ) {
        val files = directory.listFiles() ?: return@withContext emptyList()
        val excluded = excludeDeviceId ?: localDeviceId
        val entries = mutableListOf<Triple<UniFile, String, Long>>()

        for (file in files) {
            val name = file.name ?: continue
            if (name.endsWith(".pb")) {
                val parsed = FileTransport.parseFileName(name)
                if (parsed != null) {
                    val (deviceId, cursor) = parsed
                    val minCursor = sinceCursors[deviceId] ?: 0L
                    if (deviceId != excluded && cursor > minCursor) {
                        entries.add(Triple(file, deviceId, cursor))
                    }
                }
            }
        }

        entries.sortWith(compareBy<Triple<UniFile, String, Long>> { it.third }.thenBy { it.second })

        entries.mapNotNull { (file, _, _) ->
            try {
                val bytes = file.openInputStream().use { it.readBytes() }
                protoBuf.decodeFromByteArray(Changeset.serializer(), bytes)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun pull(sinceCursor: Long, excludeDeviceId: String?): List<Changeset> = withContext(
        Dispatchers.IO,
    ) {
        val files = directory.listFiles() ?: return@withContext emptyList()
        val excluded = excludeDeviceId ?: localDeviceId
        val entries = mutableListOf<Triple<UniFile, String, Long>>()

        for (file in files) {
            val name = file.name ?: continue
            if (name.endsWith(".pb")) {
                val parsed = FileTransport.parseFileName(name)
                if (parsed != null) {
                    val (deviceId, cursor) = parsed
                    if (deviceId != excluded && cursor > sinceCursor) {
                        entries.add(Triple(file, deviceId, cursor))
                    }
                }
            }
        }

        entries.sortWith(compareBy<Triple<UniFile, String, Long>> { it.third }.thenBy { it.second })

        entries.mapNotNull { (file, _, _) ->
            try {
                val bytes = file.openInputStream().use { it.readBytes() }
                protoBuf.decodeFromByteArray(Changeset.serializer(), bytes)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun headCursor(excludeDeviceId: String?): Long = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext 0L
        val excluded = excludeDeviceId ?: localDeviceId
        var maxCursor = 0L

        for (file in files) {
            val name = file.name ?: continue
            if (name.endsWith(".pb")) {
                val parsed = FileTransport.parseFileName(name)
                if (parsed != null) {
                    val (deviceId, cursor) = parsed
                    if (deviceId != excluded && cursor > maxCursor) {
                        maxCursor = cursor
                    }
                }
            }
        }

        maxCursor
    }
}
