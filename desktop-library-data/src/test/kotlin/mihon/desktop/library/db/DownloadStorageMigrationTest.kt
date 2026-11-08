package mihon.desktop.library.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LocalMangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The version 1 → 2 migration that splits one download root per manga into one storage root per
 * chapter. The stored root must survive both the upgrade itself and a later change of the manga's
 * root: chapters already on disk stay where they are.
 *
 * Roots are real paths under the temp directory rather than Windows literals like
 * `C:/downloads/original`. On macOS and Linux that string is a *relative* path, so the repository
 * resolves it against the working directory and the assertion could never match exactly.
 */
class DownloadStorageMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `version two download keeps its original root after migration and later root changes`() {
        val originalRoot = tempDir.resolve("downloads/original").toString()
        val file = tempDir.resolve("download-roots.db")
        createVersionTwoDownloadFixture(file, originalRoot)
        withConnection(file) { connection ->
            userVersion(connection) shouldBe 2L
            hasTable(connection, "local_chapter_storage") shouldBe false
        }

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            // A new root for the manga must not relocate the chapters that are already downloaded.
            repository.insertLocalManga(LocalMangaRecord(1, tempDir.resolve("downloads/new").toString(), "", 2))
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe resolved(originalRoot)
            repository.checkIntegrity() shouldBe listOf("ok")
        }
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe resolved(originalRoot)
        }
    }

    @Test
    fun `failed per chapter storage migration rolls back the table and version`() {
        val originalRoot = tempDir.resolve("downloads/original").toString()
        val file = tempDir.resolve("download-roots-rollback.db")
        createVersionTwoDownloadFixture(file, originalRoot)
        shouldThrow<DesktopLibraryDatabaseOpenException.MigrationFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.contains("INSERT OR IGNORE INTO local_chapter_storage")) {
                    error("injected storage migration failure")
                }
            }
        }
        withConnection(file) { connection ->
            userVersion(connection) shouldBe 2L
            hasTable(connection, "local_chapter_storage") shouldBe false
            queryString(connection, "SELECT storage_path FROM local_manga_entry WHERE manga_id = 1") shouldBe
                originalRoot
        }
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe resolved(originalRoot)
        }
    }

    /** The same normalisation the repository applies before handing the root to the reader. */
    private fun resolved(root: String): Path = Path.of(root).toAbsolutePath().normalize()
}

private fun createVersionTwoDownloadFixture(file: Path, storageRoot: String) {
    createVersionOneFixture(file)
    JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
        DesktopLibraryDatabase.Schema.migrate(driver, 1L, 2L)
        driver.execute(null, "PRAGMA user_version = 2", 0)
    }
    withConnection(file) { connection ->
        connection.prepareStatement("INSERT INTO local_manga_entry VALUES (1, ?, '', 1)").use { statement ->
            statement.setString(1, storageRoot)
            statement.executeUpdate()
        }
        connection.execute("INSERT INTO local_chapter_asset VALUES (1, 'Chapter 1', 'DIRECTORY', 123, 1)")
    }
}
