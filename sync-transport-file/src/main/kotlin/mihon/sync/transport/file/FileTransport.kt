package mihon.sync.transport.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.sync.core.model.Changeset
import mihon.sync.transport.api.SyncTransport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readBytes

@OptIn(ExperimentalSerializationApi::class)
class FileTransport(
    private val syncDirectory: Path,
    private val localDeviceId: String,
    private val protoBuf: ProtoBuf = ProtoBuf,
) : SyncTransport {

    companion object {
        // Pattern: <deviceId>.<cursor>.pb
        // deviceId may be a UUID or alphanumeric/hyphen/underscore string
        val FILE_REGEX = Regex("""^([a-zA-Z0-9_\-]+)\.([0-9]+)\.pb$""")

        fun parseFileName(fileName: String): Pair<String, Long>? {
            val match = FILE_REGEX.matchEntire(fileName) ?: return null
            val deviceId = match.groupValues[1]
            val cursor = match.groupValues[2].toLongOrNull() ?: return null
            return deviceId to cursor
        }
    }

    override suspend fun push(changeset: Changeset): Unit = withContext(Dispatchers.IO) {
        if (!Files.exists(syncDirectory)) {
            Files.createDirectories(syncDirectory)
        }
        val targetFileName = "${changeset.deviceId}.${changeset.cursor}.pb"
        val targetPath = syncDirectory.resolve(targetFileName)
        val tempFile = Files.createTempFile(syncDirectory, ".sync-", ".tmp")
        try {
            val bytes = protoBuf.encodeToByteArray(Changeset.serializer(), changeset)
            Files.write(tempFile, bytes)
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: IOException) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    override suspend fun pull(
        sinceCursors: Map<String, Long>,
        excludeDeviceId: String?,
    ): List<Changeset> = withContext(
        Dispatchers.IO,
    ) {
        if (!syncDirectory.exists()) return@withContext emptyList()

        val excluded = excludeDeviceId ?: localDeviceId
        val entries = mutableListOf<Triple<Path, String, Long>>()

        Files.newDirectoryStream(syncDirectory).use { stream ->
            for (file in stream) {
                if (file.extension == "pb") {
                    val parsed = parseFileName(file.name)
                    if (parsed != null) {
                        val (deviceId, cursor) = parsed
                        val minCursor = sinceCursors[deviceId] ?: 0L
                        if (deviceId != excluded && cursor > minCursor) {
                            entries.add(Triple(file, deviceId, cursor))
                        }
                    }
                }
            }
        }

        // Sort by cursor ascending, then deviceId for deterministic ordering
        entries.sortWith(compareBy<Triple<Path, String, Long>> { it.third }.thenBy { it.second })

        entries.mapNotNull { (path, _, _) ->
            try {
                val bytes = path.readBytes()
                protoBuf.decodeFromByteArray(Changeset.serializer(), bytes)
            } catch (_: Exception) {
                // Skip corrupted or unreadable files gracefully
                null
            }
        }
    }

    override suspend fun pull(sinceCursor: Long, excludeDeviceId: String?): List<Changeset> = withContext(
        Dispatchers.IO,
    ) {
        if (!syncDirectory.exists()) return@withContext emptyList()

        val excluded = excludeDeviceId ?: localDeviceId
        val entries = mutableListOf<Triple<Path, String, Long>>()

        Files.newDirectoryStream(syncDirectory).use { stream ->
            for (file in stream) {
                if (file.extension == "pb") {
                    val parsed = parseFileName(file.name)
                    if (parsed != null) {
                        val (deviceId, cursor) = parsed
                        if (deviceId != excluded && cursor > sinceCursor) {
                            entries.add(Triple(file, deviceId, cursor))
                        }
                    }
                }
            }
        }

        entries.sortWith(compareBy<Triple<Path, String, Long>> { it.third }.thenBy { it.second })

        entries.mapNotNull { (path, _, _) ->
            try {
                val bytes = path.readBytes()
                protoBuf.decodeFromByteArray(Changeset.serializer(), bytes)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun headCursor(excludeDeviceId: String?): Long = withContext(Dispatchers.IO) {
        if (!syncDirectory.exists()) return@withContext 0L

        val excluded = excludeDeviceId ?: localDeviceId
        var maxCursor = 0L

        Files.newDirectoryStream(syncDirectory).use { stream ->
            for (file in stream) {
                if (file.extension == "pb") {
                    val parsed = parseFileName(file.name)
                    if (parsed != null) {
                        val (deviceId, cursor) = parsed
                        if (deviceId != excluded && cursor > maxCursor) {
                            maxCursor = cursor
                        }
                    }
                }
            }
        }

        maxCursor
    }
}
