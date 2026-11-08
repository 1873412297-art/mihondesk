package mihon.desktop.library.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

// Shared fixtures and assertions for the schema-upgrade tests in this package.

/**
 * Builds a v1-shaped fixture: the current create schema without the baseline migration's
 * metadata table, with user_version pinned to 1. This mirrors a database created by the
 * pre-migration desktop build.
 */
internal fun createVersionOneFixture(file: Path) {
    JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").use { driver ->
        DesktopLibraryDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE local_chapter_storage", 0)
        driver.execute(null, "DROP TABLE library_metadata", 0)
        // Version 1 predates the category sync timestamps table; the fixture is built from the current schema.
        driver.execute(null, "DROP TABLE category_sync", 0)
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

internal fun <T> withConnection(path: Path, block: (Connection) -> T): T =
    DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use(block)

internal fun Connection.execute(sql: String) {
    createStatement().use { it.execute(sql) }
}

internal fun userVersion(connection: Connection): Long = queryLong(connection, "PRAGMA user_version")

internal fun hasTable(connection: Connection, name: String): Boolean =
    connection.prepareStatement(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
    ).use { statement ->
        statement.setString(1, name)
        statement.executeQuery().use { rows ->
            check(rows.next())
            rows.getLong(1) > 0L
        }
    }

internal fun queryString(connection: Connection, sql: String): String? =
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            if (rows.next()) rows.getString(1) else null
        }
    }

internal fun queryLong(connection: Connection, sql: String): Long =
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            check(rows.next()) { "Expected a row for query: $sql" }
            rows.getLong(1)
        }
    }

internal fun snapshots(file: Path): List<Path> =
    Files.list(file.parent.resolve("migration-backups")).use { entries ->
        entries.filter { it.fileName.toString().endsWith(".db") }.toList()
    }

internal fun databaseRows(file: Path): Map<String, List<List<String?>>> = withConnection(file) { connection ->
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
