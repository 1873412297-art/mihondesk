package mihon.desktop.library.db

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies the connection pragmas in [DesktopLibraryDatabaseFactory] by their observable effects:
 * WAL snapshot isolation and the busy handler. Reading the values back would only prove that SQLite
 * accepted the same statements, not that concurrent writers actually wait.
 */
class SqliteConcurrencyBehaviourTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `the database file is left in wal journal mode after the repository closes`() {
        val file = tempDir.resolve("journal-mode.db")
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.insertManga(MangaRecord(sourceId = 1, url = "/a", title = "A"))
        }

        // journal_mode persists in the file header, so a later connection reports what the
        // repository's own connection selected. A rejected switch silently falls back to "delete".
        withRawConnection(file) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA journal_mode").use { rows ->
                    rows.next() shouldBe true
                    rows.getString(1) shouldBe "wal"
                }
            }
        }
    }

    @Test
    fun `readers keep the committed snapshot while another connection holds the write lock`() {
        val file = tempDir.resolve("wal-readers.db")
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            val mangaId = repository.insertManga(
                MangaRecord(sourceId = 1, url = "/wal", title = "Committed"),
            )

            withRawConnection(file) { writer ->
                writer.autoCommit = false
                writer.createStatement().use { statement ->
                    statement.executeUpdate("UPDATE manga SET title = 'Uncommitted' WHERE id = $mangaId")
                }
                // A rollback journal would block this reader behind the writer instead of
                // serving the last committed snapshot.
                repository.librarySnapshot().single().title shouldBe "Committed"
                writer.rollback()
            }

            repository.librarySnapshot().single().title shouldBe "Committed"
        }
    }

    @Test
    fun `a blocked writer waits for the lock to clear instead of failing with sqlite busy`() {
        val file = tempDir.resolve("busy-timeout.db")
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.insertManga(MangaRecord(sourceId = 1, url = "/seed", title = "Seed"))

            withRawConnection(file) { blocker ->
                blocker.autoCommit = false
                blocker.createStatement().use { statement ->
                    statement.executeUpdate("UPDATE manga SET title = 'Blocked' WHERE source_id = 1")
                }
                val committed = commitAfterDelay(blocker)

                val startedAt = System.nanoTime()
                // With busy_timeout=0 this throws SQLITE_BUSY at once instead of waiting.
                repository.insertManga(MangaRecord(sourceId = 1, url = "/waited", title = "Waited"))
                val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

                check(committed.await(5, TimeUnit.SECONDS)) { "the blocking transaction never committed" }
                (elapsedMillis >= MINIMUM_WAIT_MILLIS) shouldBe true
                repository.findManga(1, "/waited").shouldNotBeNull()
                repository.findManga(1, "/seed")!!.title shouldBe "Blocked"
            }
        }
    }

    /** Commits [blocker] from another thread after [LOCK_HOLD_MILLIS], releasing the write lock. */
    private fun commitAfterDelay(blocker: Connection): CountDownLatch {
        val released = CountDownLatch(1)
        val thread = Thread({
            try {
                Thread.sleep(LOCK_HOLD_MILLIS)
                blocker.commit()
            } finally {
                released.countDown()
            }
        }, "sqlite-lock-holder")
        thread.isDaemon = true
        thread.start()
        return released
    }

    private fun withRawConnection(file: Path, block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use(block)
    }

    private companion object {
        /** How long the blocking connection holds the write lock. */
        const val LOCK_HOLD_MILLIS = 400L

        /** Far below the 5 s busy timeout but above scheduling noise on a loaded machine. */
        const val MINIMUM_WAIT_MILLIS = 150L
    }
}
