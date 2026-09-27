package mihon.desktop.library.db

import app.cash.sqldelight.db.SqlDriver
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.sql.DriverManager

/** A standalone, validated pre-migration database, including committed WAL content. */
object DatabaseMigrationSnapshot {
    const val MAX_SNAPSHOTS = 5
    const val MAX_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000L // 30 days

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
            prune(directory)
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

    fun prune(
        directory: Path,
        maxSnapshots: Int = MAX_SNAPSHOTS,
        maxRetentionMillis: Long = MAX_RETENTION_MILLIS,
        now: Long = System.currentTimeMillis(),
    ): Int {
        if (!Files.exists(directory)) return 0
        val snapshotFiles = try {
            Files.list(directory).use { stream ->
                stream.filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith(".db")
                }.toList()
            }
        } catch (_: Throwable) {
            return 0
        }

        val sorted = snapshotFiles.sortedByDescending { path ->
            try {
                Files.getLastModifiedTime(path).toMillis()
            } catch (_: Throwable) {
                0L
            }
        }

        var deletedCount = 0
        sorted.forEachIndexed { index, path ->
            val age = try {
                now - Files.getLastModifiedTime(path).toMillis()
            } catch (_: Throwable) {
                0L
            }
            val shouldDelete = index >= maxSnapshots || (index > 0 && age > maxRetentionMillis)
            if (shouldDelete) {
                try {
                    if (Files.deleteIfExists(path)) {
                        deletedCount++
                    }
                } catch (_: Throwable) {
                    // Ignore pruning failure
                }
            }
        }
        return deletedCount
    }

    data class SnapshotInfo(
        val count: Int,
        val totalBytes: Long,
        val directory: Path,
    )

    fun getSnapshotInfo(database: Path): SnapshotInfo {
        val directory = database.resolveSibling("migration-backups")
        if (!Files.exists(directory)) return SnapshotInfo(0, 0L, directory)
        var count = 0
        var totalBytes = 0L
        try {
            Files.list(directory).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".db") }.forEach { path ->
                    count++
                    totalBytes += Files.size(path)
                }
            }
        } catch (_: Throwable) {}
        return SnapshotInfo(count, totalBytes, directory)
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
