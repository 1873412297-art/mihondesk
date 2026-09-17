package mihon.desktop.library.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BackupRestoreScaleTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `large restore can roll back midway then restore all manga with monotonic progress`() {
        val backup = AndroidBackup(
            List(10_000) { index ->
                AndroidBackupManga(
                    42,
                    "/manga/$index",
                    "漫画 $index",
                    chapters = List(4) { chapter ->
                        AndroidBackupChapter("/chapter/$chapter", "Chapter $chapter", bookmark = true, lastPageRead = 3)
                    },
                )
            },
        )
        val file = directory.resolve("large.tachibk")
        AndroidBackupCodec().encode(backup, file)
        DesktopLibraryDatabaseFactory.open(directory.resolve("library.db")).use { repository ->
            repository.insertManga(MangaRecord(sourceId = 1, url = "/existing", title = "Preserve me"))
            val importer = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository)
            lateinit var cancelled: BackupImportControl
            var cancelledAt = 0L
            cancelled = BackupImportControl(onProgress = {
                if (it.stage == BackupImportStage.RESTORING && it.completedManga == 250) {
                    cancelledAt = System.nanoTime()
                    cancelled.cancel() shouldBe true
                }
            })
            shouldThrow<CancellationException> { importer.import(file, 1, cancelled) }
            val rollbackMillis = (System.nanoTime() - cancelledAt) / 1_000_000
            repository.allMangaSnapshot().single().title shouldBe "Preserve me"
            repository.allChaptersSnapshot().size shouldBe 0
            repository.latestImportReport() shouldBe null

            var completed = 0
            val control = BackupImportControl(onProgress = {
                if (it.stage == BackupImportStage.RESTORING) {
                    check(it.completedManga >= completed)
                    it.totalManga shouldBe 10_000
                    completed = it.completedManga
                }
            })
            val started = System.nanoTime()
            val report = importer.import(file, 2, control)
            report.counts.mangaInserted shouldBe 10_000L
            report.counts.chaptersInserted shouldBe 40_000L
            completed shouldBe 10_000
            repository.allMangaSnapshot().size shouldBe 10_001
            repository.allChaptersSnapshot().count { it.bookmark && it.lastPageRead == 3L } shouldBe 40_000
            repository.checkIntegrity() shouldBe listOf("ok")
            val restoreMillis = (System.nanoTime() - started) / 1_000_000
            println("RESTORE_SCALE manga=10000 chapters=40000 rollbackMs=$rollbackMillis restoreMs=$restoreMillis")
        }
    }
}
