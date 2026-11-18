package mihon.companion.mihonfolder

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mihon.companion.data.LibraryStore
import mihon.companion.data.MihonBackup
import mihon.companion.data.MihonBackupCodec
import mihon.companion.data.chapter
import mihon.companion.data.manga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class MihonAutoImporterTest {
    @TempDir
    lateinit var dir: File

    /** A Mihon folder in memory: file name -> (modified time, backup bytes). */
    private class FakeFolder : MihonBackupSource {
        val files = linkedMapOf<String, Pair<Long, ByteArray>>()
        var revoked = false

        fun write(name: String, modified: Long, backup: MihonBackup) {
            val bytes = ByteArrayOutputStream().also { MihonBackupCodec.encode(backup, it) }.toByteArray()
            files[name] = modified to bytes
        }

        override fun newest(folderUri: String): NewestBackup {
            if (revoked) return NewestBackup.PermissionLost
            val (name, entry) = files.entries.maxByOrNull { it.value.first }?.toPair() ?: return NewestBackup.NoBackups
            return NewestBackup.Found(MihonBackupFile(name, entry.first, name))
        }

        override fun open(file: MihonBackupFile): InputStream = ByteArrayInputStream(files.getValue(file.uri).second)
    }

    private val folder = FakeFolder()
    private val store by lazy { LibraryStore(File(dir, "library.pb"), Dispatchers.Unconfined) }

    private fun importer() = MihonAutoImporter(
        store = store,
        source = folder,
        stateFile = File(dir, "mihon-folder.json"),
        deviceId = { "phone" },
        io = Dispatchers.Unconfined,
        clock = { 1_000 },
    )

    @Test
    fun `reads the newest backup once and again only when Mihon writes a newer one`(): Unit = runBlocking {
        val importer = importer()
        importer.importIfNewer() shouldBe MihonImportOutcome.NotConfigured

        importer.setFolder("content://mihon", "Mihon")
        importer.importIfNewer() shouldBe MihonImportOutcome.NoBackups

        folder.write("mihon_1.tachibk", 100, MihonBackup(listOf(manga("/a", chapters = listOf(chapter("c1"))))))
        importer.importIfNewer().shouldBeInstanceOf<MihonImportOutcome.Imported>()
        store.load().pendingChanges shouldBe 1

        // Reopening the app without a new Mihon backup changes nothing — also after a restart.
        importer().importIfNewer() shouldBe MihonImportOutcome.UpToDate

        folder.write(
            "mihon_2.tachibk",
            200,
            MihonBackup(listOf(manga("/a", chapters = listOf(chapter("c1", read = true))))),
        )
        val second = importer().importIfNewer()
        second.shouldBeInstanceOf<MihonImportOutcome.Imported>().file shouldBe "mihon_2.tachibk"
        store.load().mangas.single().manga.chapters.single().read shouldBe true
    }

    @Test
    fun `a revoked folder is reported instead of failing silently`(): Unit = runBlocking {
        val importer = importer()
        importer.setFolder("content://mihon", "Mihon")
        folder.revoked = true

        importer.importIfNewer() shouldBe MihonImportOutcome.PermissionLost
        importer.status.value.problem shouldBe MihonImportOutcome.PermissionLost
    }

    @Test
    fun `a damaged backup becomes a visible failure`(): Unit = runBlocking {
        val importer = importer()
        importer.setFolder("content://mihon", "Mihon")
        folder.files["broken.tachibk"] = 50L to byteArrayOf(0x1f, 0x8b.toByte(), 9, 9)

        importer.importIfNewer().shouldBeInstanceOf<MihonImportOutcome.Failed>()
        importer.status.value.problem.shouldBeInstanceOf<MihonImportOutcome.Failed>()
    }

    @Test
    fun `unread and shelf removal in Mihon reach the library through the next backup`(): Unit = runBlocking {
        val importer = importer()
        importer.setFolder("content://mihon", "Mihon")
        folder.write(
            "b1.tachibk",
            // Real-world units: Mihon stores modification times in seconds, file times are millis.
            1_700_000_100_000,
            MihonBackup(
                listOf(
                    manga("/a", chapters = listOf(chapter("c1", read = true, modified = 1_700_000_000))),
                    manga("/b"),
                ),
            ),
        )
        importer.importIfNewer()

        // In Mihon: c1 marked unread, /b removed from the shelf; then the next auto-backup.
        folder.write(
            "b2.tachibk",
            1_700_000_600_000,
            MihonBackup(listOf(manga("/a", chapters = listOf(chapter("c1", read = false, modified = 1_700_000_500))))),
        )
        importer.importIfNewer()

        val library = store.load()
        library.mangas.first { it.manga.url == "/a" }.manga.chapters.single().read shouldBe false
        library.mangas.first { it.manga.url == "/b" }.manga.favorite shouldBe false
    }
}
