package mihon.desktop.ui.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mihon.desktop.library.backup.AndroidBackup
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupManga
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.backup.ImportCheckpoint
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.local.LocalMangaImporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackupRestoreCancellationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `cancelling the import coroutine before commit rolls back the restored library`() = runBlocking {
        val codec = AndroidBackupCodec()
        val backup = directory.resolve("restore.tachibk")
        codec.encode(AndroidBackup(listOf(AndroidBackupManga(42, "/series", "Cancelled restore"))), backup)
        DesktopLibraryDatabaseFactory.open(directory.resolve("library.db")).use { repository ->
            val readyToCommit = CountDownLatch(1)
            val release = CountDownLatch(1)
            val importer = AndroidBackupImporter(
                codec,
                AndroidBackupValidator(),
                repository,
                checkpoint = ImportCheckpoint {
                    readyToCommit.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                },
            )
            val controller =
                LibraryImportController(importer, LocalMangaImporter(repository), directory.resolve("local"))
            val job = launch(Dispatchers.Default) { controller.importBackup(backup) }
            try {
                assertTrue(withContext(Dispatchers.IO) { readyToCommit.await(10, TimeUnit.SECONDS) })
                job.cancel()
            } finally {
                release.countDown()
                job.join()
            }
            assertEquals(emptyList<Any>(), repository.allMangaSnapshot())
            assertEquals(null, repository.latestImportReport())
            assertEquals(listOf("ok"), repository.checkIntegrity())
        }
    }
}
