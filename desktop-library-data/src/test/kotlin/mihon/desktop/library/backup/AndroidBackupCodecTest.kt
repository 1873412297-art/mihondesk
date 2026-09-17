package mihon.desktop.library.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.ForwardingSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalSerializationApi::class)
class AndroidBackupCodecTest {
    @TempDir
    lateinit var tempDir: Path

    private val codec = AndroidBackupCodec()

    @Test
    fun `decode cancellation closes the input without classifying it as corrupt`() {
        var cancel = false
        var closed = false
        val source = object : ForwardingSource(Buffer().write(gzip(encodedBackup()))) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val result = super.read(sink, byteCount)
                cancel = true
                return result
            }
            override fun close() {
                closed = true
                super.close()
            }
        }
        val controlled = AndroidBackupCodec(compressedSize = { 1 }, sourceFactory = { source })
        shouldThrow<kotlinx.coroutines.CancellationException> {
            controlled.decode(tempDir.resolve("cancelled.tachibk"), checkCancelled = {
                if (cancel) throw kotlinx.coroutines.CancellationException("cancel")
            })
        }
        closed shouldBe true
    }

    @Test
    fun `failed publication preserves destination and removes its temporary file`() {
        val target = Files.createDirectory(tempDir.resolve("existing.tachibk"))
        val original = target.resolve("original.tachibk")
        Files.write(original, gzip(encodedBackup()))
        val before = Files.readAllBytes(original).toList()

        shouldThrow<IOException> { codec.encode(codec.decode(original), target) }

        Files.readAllBytes(original).toList() shouldBe before
        codec.decode(original).backupManga.single().title shouldBe "Series"
        Files.list(tempDir).use { it.toList() } shouldBe listOf(target)
    }

    @Test
    fun `decodes a valid raw Android ProtoBuf backup`() {
        val path = write("raw.proto", encodedBackup())

        val decoded = codec.decode(
            path,
            BackupLimits.DEFAULT.copy(maxCompressedBytes = Files.size(path)),
        )

        decoded.backupManga.single().run {
            source shouldBe 42L
            url shouldBe "/series"
            title shouldBe "Series"
            updateStrategy shouldBe AndroidUpdateStrategy.ONLY_FETCH_ONCE
            chapters.single().name shouldBe "Chapter 1"
        }
        decoded.backupPreferences.single().run {
            key shouldBe "pref_display_mode_library"
            value shouldBe AndroidStringPreferenceValue("COMPACT_GRID")
        }
    }

    @Test
    fun `decodes the same Android ProtoBuf backup through gzip`() {
        val path = write("backup.proto.gz", gzip(encodedBackup()))

        val decoded = codec.decode(path)

        decoded.backupManga.single().title shouldBe "Series"
        decoded.backupCategories.single().name shouldBe "Reading"
    }

    @Test
    fun `classifies a truncated gzip stream as corrupt gzip`() {
        val gzip = gzip(encodedBackup())
        val path = write("truncated.gz", gzip.copyOf(gzip.size - 3))

        decodeFailure(path) shouldBe BackupDecodeException.Kind.CORRUPT_GZIP
    }

    @Test
    fun `classifies random bytes as invalid ProtoBuf`() {
        val path = write("random.bin", byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()))

        decodeFailure(path) shouldBe BackupDecodeException.Kind.INVALID_PROTOBUF
    }

    @Test
    fun `rejects every legacy JSON signature before ProtoBuf decoding`() {
        listOf(
            byteArrayOf('{'.code.toByte(), '}'.code.toByte()),
            byteArrayOf('{'.code.toByte(), '"'.code.toByte()),
            byteArrayOf('{'.code.toByte(), '\n'.code.toByte()),
        ).forEachIndexed { index, bytes ->
            val path = write("legacy-$index.json", bytes)

            decodeFailure(path) shouldBe BackupDecodeException.Kind.LEGACY_JSON
        }
    }

    @Test
    fun `rejects compressed input one byte above its bound`() {
        val limits = BackupLimits.DEFAULT.copy(maxCompressedBytes = 16)
        val path = write("too-large.bin", ByteArray(17))

        decodeFailure(path, limits) shouldBe BackupDecodeException.Kind.COMPRESSED_LIMIT
    }

    @Test
    fun `rejects a source that grows beyond the compressed bound after its size check`() {
        val encoded = encodedBackup()
        val limits = BackupLimits.DEFAULT.copy(maxCompressedBytes = encoded.size.toLong())
        val growingCodec = AndroidBackupCodec(
            compressedSize = { encoded.size.toLong() },
            sourceFactory = { Buffer().write(encoded).writeByte(0) },
        )

        shouldThrow<BackupDecodeException> {
            growingCodec.decode(tempDir.resolve("growing.proto"), limits)
        }.kind shouldBe BackupDecodeException.Kind.COMPRESSED_LIMIT
    }

    @Test
    fun `stops a gzip payload immediately after it expands one byte above its bound`() {
        val limits = BackupLimits.DEFAULT.copy(maxExpandedBytes = 64)
        val path = write("gzip-bomb.gz", gzip(ByteArray(65)))

        decodeFailure(path, limits) shouldBe BackupDecodeException.Kind.EXPANDED_LIMIT
    }

    @Test
    fun `preference subclasses retain Android serializer names`() {
        AndroidIntPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue"
        AndroidLongPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue"
        AndroidFloatPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue"
        AndroidStringPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue"
        AndroidBooleanPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue"
        AndroidStringSetPreferenceValue.serializer().descriptor.serialName shouldBe
            "eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue"
    }

    private fun encodedBackup(): ByteArray = ProtoBuf.encodeToByteArray(
        AndroidBackup.serializer(),
        AndroidBackup(
            backupManga = listOf(
                AndroidBackupManga(
                    source = 42,
                    url = "/series",
                    title = "Series",
                    chapters = listOf(AndroidBackupChapter(url = "/chapter-1", name = "Chapter 1")),
                    categories = listOf(7),
                    updateStrategy = AndroidUpdateStrategy.ONLY_FETCH_ONCE,
                ),
            ),
            backupCategories = listOf(AndroidBackupCategory(name = "Reading", order = 7, id = 11)),
            backupPreferences = listOf(
                AndroidBackupPreference(
                    key = "pref_display_mode_library",
                    value = AndroidStringPreferenceValue("COMPACT_GRID"),
                ),
            ),
        ),
    )

    private fun decodeFailure(
        path: Path,
        limits: BackupLimits = BackupLimits.DEFAULT,
    ): BackupDecodeException.Kind = shouldThrow<BackupDecodeException> {
        codec.decode(path, limits)
    }.kind

    private fun write(name: String, bytes: ByteArray): Path =
        tempDir.resolve(name).also { Files.write(it, bytes) }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }
}
