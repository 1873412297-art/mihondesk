package mihon.companion.data

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MihonBackupCodecTest {
    @Test
    fun `round trip converts Mihon seconds to millis and back`() {
        val seconds = 1_700_000_000L
        val backup = MihonBackup(
            backupManga = listOf(manga("/a", modified = seconds, chapters = listOf(chapter("c1", modified = seconds)))),
            backupCategories = listOf(category("Reading", 0)),
        )
        val bytes = ByteArrayOutputStream()
        // Simulate a file written by Mihon: encode() expects millis and writes seconds.
        MihonBackupCodec.encode(
            backup.copy(
                backupManga = backup.backupManga.map {
                    it.copy(
                        lastModifiedAt =
                        seconds * 1000,
                    )
                },
            ),
            bytes,
        )

        val decoded = MihonBackupCodec.decode(ByteArrayInputStream(bytes.toByteArray()))
        decoded.backupManga.single().lastModifiedAt shouldBe seconds * 1000
        decoded.backupManga.single().chapters.single().lastModifiedAt shouldBe seconds * 1000
        decoded.backupCategories.single().name shouldBe "Reading"
    }

    @Test
    fun `export keeps only shelf entries with category orders`() {
        val library = LibraryMerge.merge(
            StoredLibrary(),
            IncomingLibrary(
                mangas = listOf(manga("/a", categories = listOf(5)), manga("/b").copy(favorite = false)),
                categories = listOf(category("Done", 5)),
                deviceId = "desktop",
            ),
            markDirty = false,
        ).first

        val backup = MihonBackupCodec.fromLibrary(library)
        backup.backupManga.map { it.url } shouldBe listOf("/a")
        backup.backupManga.single().categories shouldBe listOf(0L)
        backup.backupSources.single().sourceId shouldBe 42
    }

    @Test
    fun `garbage is rejected with a format error`() {
        assertThrows<BackupFormatException> {
            MihonBackupCodec.decode(ByteArrayInputStream(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3)))
        }
    }
}
