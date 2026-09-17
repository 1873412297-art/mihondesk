package mihon.desktop.library.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.library.model.LocalMangaRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DesktopLibraryDatabaseMigrationTest {
    // Exercises the version-aware create/migrate paths used by DesktopLibraryDatabaseFactory.
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun verifyPackagedOriginWhenRequested() {
        System.getenv("MIHON_UPGRADE_APP")?.let { packaged ->
            listOf(DesktopLibraryDatabaseFactory::class.java, DatabaseMigrationSnapshot::class.java).forEach { type ->
                val origin = Path.of(type.protectionDomain.codeSource.location.toURI()).toAbsolutePath()
                origin.startsWith(Path.of(packaged).toAbsolutePath()) shouldBe true
                println("PACKAGED_UPGRADE ${type.name}: $origin")
            }
        }
    }

    @Test
    fun `upgrade first saves a standalone old schema snapshot including committed WAL rows`() {
        val directory = Files.createDirectories(tempDir.resolve("reader's library 数据"))
        val file = directory.resolve("library.db")
        createVersionOneFixture(file)
        withConnection(file) { writer ->
            writer.execute("PRAGMA journal_mode = WAL")
            writer.execute("PRAGMA wal_autocheckpoint = 0")
            writer.execute("UPDATE manga SET title = 'Committed WAL title' WHERE id = 1")
            (Files.size(Path.of("$file-wal")) > 0L) shouldBe true
            val before = databaseRows(file)

            DesktopLibraryDatabaseFactory.open(file).use { repository ->
                repository.librarySnapshot().single().title shouldBe "Committed WAL title"
            }

            val snapshot = snapshots(file).single()
            databaseRows(snapshot) shouldBe before
            withConnection(snapshot) { connection ->
                userVersion(connection) shouldBe 1L
                queryString(connection, "PRAGMA integrity_check") shouldBe "ok"
                hasTable(connection, "library_metadata") shouldBe false
            }
            Files.exists(Path.of("$snapshot-wal")) shouldBe false

            val restored = Files.createDirectories(tempDir.resolve("restored")).resolve("library.db")
            Files.copy(snapshot, restored)
            DesktopLibraryDatabaseFactory.open(restored).use { repository ->
                repository.librarySnapshot().single().title shouldBe "Committed WAL title"
                repository.historySnapshot().single().readDuration shouldBe 42L
                repository.checkIntegrity() shouldBe listOf("ok")
            }
            databaseRows(restored) shouldBe databaseRows(file)
        }
    }

    @Test
    fun `unavailable snapshot directory prevents migration without modifying old data`() {
        val file = tempDir.resolve("library.db")
        createVersionOneFixture(file)
        val before = databaseRows(file)
        Files.writeString(tempDir.resolve("migration-backups"), "occupied")

        shouldThrow<DesktopLibraryDatabaseOpenException.SnapshotFailed> {
            DesktopLibraryDatabaseFactory.open(file).close()
        }

        databaseRows(file) shouldBe before
        withConnection(file) { userVersion(it) shouldBe 1L }
        Files.readString(tempDir.resolve("migration-backups")) shouldBe "occupied"
    }

    @Test
    fun `interrupted snapshot is removed and migration never starts`() {
        val file = tempDir.resolve("library.db")
        createVersionOneFixture(file)
        val before = databaseRows(file)
        shouldThrow<DesktopLibraryDatabaseOpenException.SnapshotFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.startsWith("VACUUM")) error("injected snapshot failure")
            }.close()
        }
        databaseRows(file) shouldBe before
        withConnection(file) { userVersion(it) shouldBe 1L }
        Files.list(tempDir.resolve("migration-backups")).use { it.count() } shouldBe 0L
        DesktopLibraryDatabaseFactory.open(file).close()
        snapshots(file).size shouldBe 1
    }

    @Test
    fun `damaged snapshot is rejected before publication or migration`() {
        val file = tempDir.resolve("library.db")
        createVersionOneFixture(file)
        val before = databaseRows(file)
        shouldThrow<DesktopLibraryDatabaseOpenException.SnapshotFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.startsWith("VACUUM")) {
                    val pending = Files.list(tempDir.resolve("migration-backups")).use { it.toList().single() }
                    Files.writeString(pending, "injected corruption")
                }
            }.close()
        }
        databaseRows(file) shouldBe before
        withConnection(file) { userVersion(it) shouldBe 1L }
        Files.list(tempDir.resolve("migration-backups")).use { it.count() } shouldBe 0L
    }

    @Test
    fun `atomic publication failure preserves old database and never leaves a partial snapshot`() {
        val file = tempDir.resolve("library.db")
        createVersionOneFixture(file)
        val before = databaseRows(file)
        var occupied: Path? = null
        shouldThrow<DesktopLibraryDatabaseOpenException.SnapshotFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.startsWith("VACUUM")) {
                    val pending = Files.list(tempDir.resolve("migration-backups")).use { it.toList().single() }
                    val destination = pending.resolveSibling(pending.fileName.toString().removeSuffix(".tmp") + ".db")
                    Files.createDirectory(destination)
                    occupied = destination.resolve("sentinel")
                    Files.writeString(occupied, "keep")
                }
            }.close()
        }
        databaseRows(file) shouldBe before
        withConnection(file) { userVersion(it) shouldBe 1L }
        Files.readString(occupied) shouldBe "keep"
        Files.list(tempDir.resolve("migration-backups")).use { it.anyMatch(Files::isRegularFile) } shouldBe false
    }

    @Test
    fun `migration failure preserves recovery snapshot and retry never overwrites it`() {
        val file = tempDir.resolve("library.db")
        createVersionOneFixture(file)
        val before = databaseRows(file)
        shouldThrow<DesktopLibraryDatabaseOpenException.MigrationFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.contains("CREATE TABLE IF NOT EXISTS library_metadata")) error("injected migration failure")
            }.close()
        }
        val first = snapshots(file).single()
        val bytes = Files.readAllBytes(first).toList()
        databaseRows(first) shouldBe before
        databaseRows(file) shouldBe before
        DesktopLibraryDatabaseFactory.open(file).close()
        snapshots(file).size shouldBe 2
        Files.readAllBytes(first).toList() shouldBe bytes
    }

    @Test
    fun `fresh current and rejected schemas create no upgrade snapshots`() {
        val file = tempDir.resolve("library.db")
        DesktopLibraryDatabaseFactory.open(file).close()
        DesktopLibraryDatabaseFactory.open(file).close()
        Files.exists(tempDir.resolve("migration-backups")) shouldBe false
        withConnection(file) { it.execute("PRAGMA user_version = 999") }
        shouldThrow<DesktopLibraryDatabaseOpenException.UnsupportedVersion> {
            DesktopLibraryDatabaseFactory.open(file)
        }
        Files.exists(tempDir.resolve("migration-backups")) shouldBe false
    }

    @Test
    fun `version two download keeps its original root after migration and later root changes`() {
        val file = tempDir.resolve("download-roots.db")
        createVersionTwoDownloadFixture(file)
        withConnection(file) { connection ->
            userVersion(connection) shouldBe 2L
            hasTable(connection, "local_chapter_storage") shouldBe false
        }

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.insertLocalManga(LocalMangaRecord(1, "C:/downloads/new", "", 2))
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe Path.of("C:/downloads/original")
            repository.checkIntegrity() shouldBe listOf("ok")
        }
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe Path.of("C:/downloads/original")
        }
    }

    @Test
    fun `failed per chapter storage migration rolls back the table and version`() {
        val file = tempDir.resolve("download-roots-rollback.db")
        createVersionTwoDownloadFixture(file)
        shouldThrow<DesktopLibraryDatabaseOpenException.MigrationFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.contains(
                        "INSERT OR IGNORE INTO local_chapter_storage",
                    )
                ) {
                    error("injected storage migration failure")
                }
            }
        }
        withConnection(file) { connection ->
            userVersion(connection) shouldBe 2L
            hasTable(connection, "local_chapter_storage") shouldBe false
            queryString(connection, "SELECT storage_path FROM local_manga_entry WHERE manga_id = 1") shouldBe
                "C:/downloads/original"
        }
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            requireNotNull(repository.chapterAsset(1)).storageRoot shouldBe Path.of("C:/downloads/original")
        }
    }

    @Test
    fun `fresh database is created at the current schema version`() {
        val file = tempDir.resolve("fresh.db")

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.librarySnapshot() shouldBe emptyList()
        }

        withConnection(file) { connection ->
            userVersion(connection) shouldBe DesktopLibraryDatabase.Schema.version
            hasTable(connection, "library_metadata") shouldBe true
            hasTable(connection, "manga") shouldBe true
        }
    }

    @Test
    fun `version one database upgrades to the current version and preserves representative rows`() {
        val file = tempDir.resolve("upgrade.db")
        createVersionOneFixture(file)

        withConnection(file) { connection ->
            userVersion(connection) shouldBe 1L
            hasTable(connection, "library_metadata") shouldBe false
        }

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.librarySnapshot().single().apply {
                title shouldBe "Preserved Manga"
                chapterCount shouldBe 1L
                unreadCount shouldBe 0L
            }
            repository.categoriesSnapshot().single().name shouldBe "Preserved Category"
            repository.historySnapshot().single().apply {
                mangaTitle shouldBe "Preserved Manga"
                chapterName shouldBe "Preserved Chapter"
                lastRead shouldBe 123_456_789L
                readDuration shouldBe 42L
            }
        }

        withConnection(file) { connection ->
            userVersion(connection) shouldBe DesktopLibraryDatabase.Schema.version
            hasTable(connection, "library_metadata") shouldBe true
            queryString(connection, "SELECT title FROM manga WHERE id = 1") shouldBe "Preserved Manga"
            queryString(connection, "SELECT name FROM category WHERE id = 1") shouldBe "Preserved Category"
            queryLong(connection, "SELECT read_duration FROM history WHERE chapter_id = 1") shouldBe 42L
            queryString(
                connection,
                "SELECT value FROM library_metadata WHERE name = 'schema_migration_1'",
            ) shouldBe "applied"
        }
    }

    @Test
    fun `reopening a migrated database is idempotent`() {
        val file = tempDir.resolve("reopen.db")
        createVersionOneFixture(file)

        DesktopLibraryDatabaseFactory.open(file).close()

        val statements = mutableListOf<String>()
        DesktopLibraryDatabaseFactory.open(file) { statements += it }.use { repository ->
            repository.librarySnapshot().single().title shouldBe "Preserved Manga"
        }

        statements.none { it.contains("library_metadata", ignoreCase = true) } shouldBe true
        withConnection(file) { connection ->
            userVersion(connection) shouldBe DesktopLibraryDatabase.Schema.version
            hasTable(connection, "library_metadata") shouldBe true
            queryLong(connection, "SELECT COUNT(*) FROM library_metadata") shouldBe 1L
            queryString(connection, "SELECT title FROM manga WHERE id = 1") shouldBe "Preserved Manga"
        }
    }

    @Test
    fun `baseline migration statements can be replayed without corrupting data`() {
        val file = tempDir.resolve("replay.db")
        createVersionOneFixture(file)

        JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
            DesktopLibraryDatabase.Schema.migrate(driver, 1L, DesktopLibraryDatabase.Schema.version)
            DesktopLibraryDatabase.Schema.migrate(driver, 1L, DesktopLibraryDatabase.Schema.version)
        }

        withConnection(file) { connection ->
            hasTable(connection, "library_metadata") shouldBe true
            queryLong(connection, "SELECT COUNT(*) FROM library_metadata") shouldBe 1L
            queryString(connection, "SELECT title FROM manga WHERE id = 1") shouldBe "Preserved Manga"
        }
    }

    @Test
    fun `newer than supported database version fails clearly without recreating`() {
        val file = tempDir.resolve("future.db")
        val futureVersion = DesktopLibraryDatabase.Schema.version + 1
        withConnection(file) { connection ->
            connection.execute("CREATE TABLE sentinel (id INTEGER NOT NULL PRIMARY KEY)")
            connection.execute("PRAGMA user_version = $futureVersion")
        }

        val error = shouldThrow<DesktopLibraryDatabaseOpenException.UnsupportedVersion> {
            DesktopLibraryDatabaseFactory.open(file)
        }
        error.version shouldBe futureVersion
        error.supportedVersion shouldBe DesktopLibraryDatabase.Schema.version
        error.message.orEmpty() shouldContain "newer"

        withConnection(file) { connection ->
            userVersion(connection) shouldBe futureVersion
            hasTable(connection, "sentinel") shouldBe true
            hasTable(connection, "library_metadata") shouldBe false
        }
    }

    @Test
    fun `negative database version fails clearly`() {
        val file = tempDir.resolve("negative.db")
        withConnection(file) { connection ->
            connection.execute("PRAGMA user_version = -7")
        }

        val error = shouldThrow<DesktopLibraryDatabaseOpenException.NegativeVersion> {
            DesktopLibraryDatabaseFactory.open(file)
        }
        error.version shouldBe -7L

        withConnection(file) { connection ->
            userVersion(connection) shouldBe -7L
        }
    }

    @Test
    fun `existing schema without a version fails instead of being recreated`() {
        val file = tempDir.resolve("missing-version.db")
        withConnection(file) { connection ->
            connection.execute("CREATE TABLE orphan (id INTEGER NOT NULL PRIMARY KEY)")
        }

        shouldThrow<DesktopLibraryDatabaseOpenException.MissingVersion> {
            DesktopLibraryDatabaseFactory.open(file)
        }

        withConnection(file) { connection ->
            userVersion(connection) shouldBe 0L
            hasTable(connection, "orphan") shouldBe true
            hasTable(connection, "library_metadata") shouldBe false
        }
    }

    @Test
    fun `migration failure rolls back and leaves the version one database usable`() {
        val file = tempDir.resolve("rollback.db")
        createVersionOneFixture(file)

        val error = shouldThrow<DesktopLibraryDatabaseOpenException.MigrationFailed> {
            DesktopLibraryDatabaseFactory.open(file) { sql ->
                if (sql.contains("library_metadata", ignoreCase = true)) {
                    error("injected migration failure")
                }
            }
        }
        error.fromVersion shouldBe 1L
        error.toVersion shouldBe DesktopLibraryDatabase.Schema.version
        error.cause?.message shouldBe "injected migration failure"

        withConnection(file) { connection ->
            userVersion(connection) shouldBe 1L
            hasTable(connection, "library_metadata") shouldBe false
            queryString(connection, "SELECT title FROM manga WHERE id = 1") shouldBe "Preserved Manga"
            queryLong(connection, "SELECT read_duration FROM history WHERE chapter_id = 1") shouldBe 42L
        }

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.librarySnapshot().single().title shouldBe "Preserved Manga"
        }

        withConnection(file) { connection ->
            userVersion(connection) shouldBe DesktopLibraryDatabase.Schema.version
            hasTable(connection, "library_metadata") shouldBe true
            queryString(connection, "SELECT title FROM manga WHERE id = 1") shouldBe "Preserved Manga"
        }
    }
}

/**
 * Builds a v1-shaped fixture: the current create schema without the baseline migration's
 * metadata table, with user_version pinned to 1. This mirrors a database created by the
 * pre-migration desktop build.
 */
private fun createVersionOneFixture(file: Path) {
    JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
        DesktopLibraryDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE local_chapter_storage", 0)
        driver.execute(null, "DROP TABLE library_metadata", 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)
    }
    withConnection(file) { connection ->
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "INSERT INTO manga(source_id, url, title) VALUES (7, '/migrated', 'Preserved Manga')",
            )
            statement.executeUpdate(
                "INSERT INTO chapter(manga_id, url, name, read, last_page_read) " +
                    "VALUES (1, '/migrated/1', 'Preserved Chapter', 1, 3)",
            )
            statement.executeUpdate(
                "INSERT INTO category(name, sort_order) VALUES ('Preserved Category', 1)",
            )
            statement.executeUpdate("INSERT INTO manga_category(manga_id, category_id) VALUES (1, 1)")
            statement.executeUpdate(
                "INSERT INTO history(chapter_id, last_read, read_duration) VALUES (1, 123456789, 42)",
            )
        }
    }
}

private fun createVersionTwoDownloadFixture(file: Path) {
    createVersionOneFixture(file)
    JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
        DesktopLibraryDatabase.Schema.migrate(driver, 1L, 2L)
        driver.execute(null, "PRAGMA user_version = 2", 0)
    }
    withConnection(file) { connection ->
        connection.execute("INSERT INTO local_manga_entry VALUES (1, 'C:/downloads/original', '', 1)")
        connection.execute("INSERT INTO local_chapter_asset VALUES (1, 'Chapter 1', 'DIRECTORY', 123, 1)")
    }
}

private fun <T> withConnection(path: Path, block: (Connection) -> T): T =
    DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use(block)

private fun Connection.execute(sql: String) {
    createStatement().use { it.execute(sql) }
}

private fun userVersion(connection: Connection): Long = queryLong(connection, "PRAGMA user_version")

private fun hasTable(connection: Connection, name: String): Boolean =
    connection.prepareStatement(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
    ).use { statement ->
        statement.setString(1, name)
        statement.executeQuery().use { rows ->
            check(rows.next())
            rows.getLong(1) > 0L
        }
    }

private fun queryString(connection: Connection, sql: String): String? =
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            if (rows.next()) rows.getString(1) else null
        }
    }

private fun queryLong(connection: Connection, sql: String): Long =
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            check(rows.next()) { "Expected a row for query: $sql" }
            rows.getLong(1)
        }
    }

private fun snapshots(file: Path): List<Path> =
    Files.list(file.parent.resolve("migration-backups")).use { entries ->
        entries.filter { it.fileName.toString().endsWith(".db") }.toList()
    }

private fun databaseRows(file: Path): Map<String, List<List<String?>>> = withConnection(file) { connection ->
    val tables = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use {
            buildList { while (it.next()) add(it.getString(1)) }
        }
    }
    tables.associateWith { table ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM \"$table\"").use { rows ->
                buildList {
                    while (rows.next()) add((1..rows.metaData.columnCount).map { rows.getString(it) })
                }.sortedBy { it.toString() }
            }
        }
    }
}
