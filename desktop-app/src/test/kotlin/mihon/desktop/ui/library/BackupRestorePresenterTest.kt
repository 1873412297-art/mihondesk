package mihon.desktop.ui.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.backup.AndroidBackup
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupManga
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.backup.ImportCheckpoint
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.local.LocalMangaImporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackupRestorePresenterTest {
    @TempDir lateinit var directory: Path

    @BeforeEach
    fun verifyPackagedClasses() {
        System.getenv("MIHON_RESTORE_APP")?.let { directory ->
            listOf(
                "mihon.desktop.ui.library.BackupRestorePresenter",
                "mihon.desktop.library.backup.AndroidBackupImporter",
                "mihon.desktop.library.backup.BackupImportControl",
            ).forEach { name ->
                val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                check(origin.startsWith(Path.of(directory))) { "Expected packaged class $name: $origin" }
                println("PACKAGED_RESTORE_CLASS $name $origin")
            }
        }
    }

    @Test
    fun `cancel waits for rollback rejects concurrent start and permits a clean retry`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        DesktopLibraryDatabaseFactory.open(directory.resolve("cancel.db")).use { repository ->
            val controller = LibraryImportController(
                AndroidBackupImporter(
                    AndroidBackupCodec(),
                    AndroidBackupValidator(),
                    repository,
                    checkpoint = ImportCheckpoint {
                        ready.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    },
                ),
                LocalMangaImporter(repository),
                directory.resolve("local"),
            )
            val presenter = BackupRestorePresenter(scope, controller::importBackup)
            try {
                val path = fixture()
                assertTrue(presenter.start(path))
                assertTrue(withContext(Dispatchers.IO) { ready.await(10, TimeUnit.SECONDS) })
                assertFalse(presenter.start(path))
                assertTrue(presenter.cancel())
                assertTrue((presenter.state.value as BackupRestoreState.Running).cancelling)
                presenter.dismiss()
                assertTrue(presenter.state.value is BackupRestoreState.Running)
                release.countDown()
                withTimeout(10_000) { presenter.state.first { it == BackupRestoreState.Cancelled } }
                assertEquals(emptyList<Any>(), repository.allMangaSnapshot())
                assertEquals(null, repository.latestImportReport())
                presenter.dismiss()
                assertEquals(BackupRestoreState.Idle, presenter.state.value)
                assertTrue(presenter.start(path))
                val result = withTimeout(10_000) {
                    presenter.state.first { it is BackupRestoreState.Finished } as BackupRestoreState.Finished
                }
                assertTrue(result.outcome is ImportActionState.Completed)
                assertEquals(1, repository.allMangaSnapshot().size)
                assertFalse(presenter.cancel())
            } finally {
                release.countDown()
                presenter.close()
                scope.cancel()
            }
        }
    }

    @Test
    fun `late cancellation and disposal cannot relabel a committed restore as cancelled`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val committed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        DesktopLibraryDatabaseFactory.open(directory.resolve("commit.db")).use { repository ->
            val controller = LibraryImportController(
                AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), repository),
                LocalMangaImporter(repository),
                directory.resolve("local"),
            )
            val presenter = BackupRestorePresenter(scope) { path, control ->
                val result = controller.importBackup(path, control)
                committed.complete(Unit)
                release.await()
                result
            }
            try {
                assertTrue(presenter.start(fixture()))
                withTimeout(10_000) { committed.await() }
                assertFalse((presenter.state.value as BackupRestoreState.Running).canCancel)
                assertFalse(presenter.cancel())
                presenter.close()
                release.complete(Unit)
                val result = withTimeout(10_000) {
                    presenter.state.first { it is BackupRestoreState.Finished } as BackupRestoreState.Finished
                }
                assertTrue(result.outcome is ImportActionState.Completed)
                assertEquals(1, repository.allMangaSnapshot().size)
                assertFalse(presenter.start(fixture()))
            } finally {
                release.complete(Unit)
                presenter.close()
                scope.cancel()
            }
        }
    }

    private fun fixture(): Path = directory.resolve("input.tachibk").also {
        AndroidBackupCodec().encode(AndroidBackup(listOf(AndroidBackupManga(42, "/manga", "Restore me"))), it)
    }
}
