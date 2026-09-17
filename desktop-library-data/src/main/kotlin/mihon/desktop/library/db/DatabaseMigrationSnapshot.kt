package mihon.desktop.library.db

import app.cash.sqldelight.db.SqlDriver
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.sql.DriverManager

/** A standalone, validated pre-migration database, including committed WAL content. */
internal object DatabaseMigrationSnapshot {
    fun create(driver: SqlDriver, database: Path, fromVersion: Long, toVersion: Long): Path {
        val directory = database.resolveSibling("migration-backups")
        var temporary: Path? = null
        try {
            Files.createDirectories(directory)
            val pending = Files.createTempFile(directory, "${database.fileName}.v$fromVersion-to-v$toVersion-", ".tmp")
            temporary = pending
            // No transaction may be active here. Bind the path so quotes and Unicode are safe.
            driver.execute(null, "VACUUM main INTO ?", 1) { bindString(0, pending.toString()) }
            validate(pending, fromVersion)
            FileChannel.open(pending, StandardOpenOption.WRITE).use { it.force(true) }
            val destination = pending.resolveSibling(pending.fileName.toString().removeSuffix(".tmp") + ".db")
            // Never publish partial output or replace a previous recovery snapshot.
            Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE)
            return destination
        } catch (error: Throwable) {
            temporary?.let { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (cleanupError: Throwable) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw DesktopLibraryDatabaseOpenException.SnapshotFailed(database, directory, error)
        }
    }

    private fun validate(path: Path, expectedVersion: Long) {
        DriverManager.getConnection("jdbc:sqlite:${path.toUri()}?mode=ro").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    check(rows.next() && rows.getString(1) == "ok" && !rows.next()) {
                        "The pre-migration database snapshot failed integrity validation."
                    }
                }
                statement.executeQuery("PRAGMA user_version").use { rows ->
                    check(rows.next() && rows.getLong(1) == expectedVersion) {
                        "The pre-migration database snapshot has an unexpected schema version."
                    }
                }
            }
        }
    }
}
