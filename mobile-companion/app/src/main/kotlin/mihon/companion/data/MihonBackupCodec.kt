package mihon.companion.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.core.model.AndroidBackupSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The subset of Mihon's `.tachibk` backup this app reads and writes. Field numbers match Mihon's
 * `Backup` message; everything else (preferences, extension repos, …) is skipped on read.
 */
@Serializable
data class MihonBackup(
    @ProtoNumber(1) val backupManga: List<AndroidBackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<AndroidBackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<AndroidBackupSource> = emptyList(),
)

class BackupFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** gzip + protobuf, exactly like Mihon. Mihon stores modification times in seconds; sync uses millis. */
object MihonBackupCodec {
    /** Guards against a decompression bomb from a hostile file. */
    const val MAX_DECOMPRESSED_BYTES = 256L * 1024 * 1024

    fun decode(input: InputStream): MihonBackup {
        val raw = input.buffered().use { stream ->
            stream.mark(2)
            val gzip = stream.read() == 0x1f && stream.read() == 0x8b
            stream.reset()
            try {
                if (gzip) GZIPInputStream(stream).use(::readLimited) else readLimited(stream)
            } catch (format: BackupFormatException) {
                throw format
            } catch (error: IOException) {
                throw BackupFormatException("Backup file is damaged", error)
            }
        }
        val backup = try {
            ProtoBuf.decodeFromByteArray<MihonBackup>(raw)
        } catch (error: Exception) {
            throw BackupFormatException("Not a Mihon backup (.tachibk)", error)
        }
        return backup.copy(backupManga = backup.backupManga.map(::secondsToMillis))
    }

    fun encode(backup: MihonBackup, output: OutputStream) {
        val wire = backup.copy(backupManga = backup.backupManga.map(::millisToSeconds))
        GZIPOutputStream(output).use { it.write(ProtoBuf.encodeToByteArray(wire)) }
    }

    /** Library → Mihon backup. Only shelf (favorite) entries are written, as Mihon itself does. */
    fun fromLibrary(library: StoredLibrary): MihonBackup {
        val categories = library.categories.map { it.category }.sortedBy { it.order }
        val mangas = library.mangas.filter { it.manga.favorite }.map { stored ->
            stored.manga.copy(categories = CategoryNames.toOrders(stored.categoryNames, categories))
        }
        val usedSources = mangas.map { it.source }.toSet()
        val sources = usedSources.map { id ->
            library.sources.firstOrNull { it.sourceId == id } ?: AndroidBackupSource(name = "", sourceId = id)
        }
        return MihonBackup(mangas, categories.map { it.copy(lastModifiedAt = 0) }, sources)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_DECOMPRESSED_BYTES) throw BackupFormatException("Backup is too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    // Values below this are seconds (year 5138 in seconds vs. 1973 in millis).
    private const val SECONDS_LIMIT = 100_000_000_000L

    private fun toMillis(value: Long) = if (value in 1 until SECONDS_LIMIT) value * 1000 else value
    private fun toSeconds(value: Long) = if (value >= SECONDS_LIMIT) value / 1000 else value

    private fun secondsToMillis(manga: AndroidBackupManga) = manga.copy(
        lastModifiedAt = toMillis(manga.lastModifiedAt),
        favoriteModifiedAt = manga.favoriteModifiedAt?.let(::toMillis),
        chapters = manga.chapters.map { it.copy(lastModifiedAt = toMillis(it.lastModifiedAt)) },
    )

    private fun millisToSeconds(manga: AndroidBackupManga) = manga.copy(
        lastModifiedAt = toSeconds(manga.lastModifiedAt),
        favoriteModifiedAt = manga.favoriteModifiedAt?.let(::toSeconds),
        chapters = manga.chapters.map { it.copy(lastModifiedAt = toSeconds(it.lastModifiedAt)) },
    )
}
