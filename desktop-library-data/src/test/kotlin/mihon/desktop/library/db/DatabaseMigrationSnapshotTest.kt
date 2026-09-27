package mihon.desktop.library.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class DatabaseMigrationSnapshotTest {

    @Test
    fun `prune retains at most 5 snapshots and deletes oldest`(@TempDir tempDir: Path) {
        val backupsDir = tempDir.resolve("migration-backups")
        Files.createDirectories(backupsDir)

        val now = System.currentTimeMillis()
        for (i in 1..8) {
            val file = backupsDir.resolve("db.v1-to-v2-$i.db")
            Files.writeString(file, "snapshot $i")
            Files.setLastModifiedTime(file, FileTime.fromMillis(now + i * 1000L))
        }

        val deleted = DatabaseMigrationSnapshot.prune(backupsDir, maxSnapshots = 5, now = now + 10_000L)
        assertEquals(3, deleted)

        val remaining = Files.list(backupsDir).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(5, remaining.size)
        // Kept 4, 5, 6, 7, 8; deleted 1, 2, 3
        assertTrue(remaining.contains("db.v1-to-v2-8.db"))
        assertTrue(remaining.contains("db.v1-to-v2-4.db"))
        assertTrue(!remaining.contains("db.v1-to-v2-1.db"))
        assertTrue(!remaining.contains("db.v1-to-v2-2.db"))
        assertTrue(!remaining.contains("db.v1-to-v2-3.db"))
    }

    @Test
    fun `prune removes snapshots older than 30 days`(@TempDir tempDir: Path) {
        val backupsDir = tempDir.resolve("migration-backups")
        Files.createDirectories(backupsDir)

        val now = System.currentTimeMillis()
        val oldTime = now - 35L * 24 * 60 * 60 * 1000L // 35 days ago

        val freshFile = backupsDir.resolve("fresh.db")
        Files.writeString(freshFile, "fresh")
        Files.setLastModifiedTime(freshFile, FileTime.fromMillis(now))

        val oldFile1 = backupsDir.resolve("old1.db")
        Files.writeString(oldFile1, "old 1")
        Files.setLastModifiedTime(oldFile1, FileTime.fromMillis(oldTime))

        val oldFile2 = backupsDir.resolve("old2.db")
        Files.writeString(oldFile2, "old 2")
        Files.setLastModifiedTime(oldFile2, FileTime.fromMillis(oldTime - 1000L))

        val deleted = DatabaseMigrationSnapshot.prune(backupsDir, maxSnapshots = 5, now = now)
        assertEquals(2, deleted)

        val remaining = Files.list(backupsDir).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(listOf("fresh.db"), remaining)
    }

    @Test
    fun `getSnapshotInfo reports accurate metrics`(@TempDir tempDir: Path) {
        val dbPath = tempDir.resolve("library.db")
        val backupsDir = tempDir.resolve("migration-backups")
        Files.createDirectories(backupsDir)

        val infoEmpty = DatabaseMigrationSnapshot.getSnapshotInfo(dbPath)
        assertEquals(0, infoEmpty.count)
        assertEquals(0L, infoEmpty.totalBytes)

        val file1 = backupsDir.resolve("snap1.db")
        Files.writeString(file1, "12345")
        val file2 = backupsDir.resolve("snap2.db")
        Files.writeString(file2, "67890")

        val info = DatabaseMigrationSnapshot.getSnapshotInfo(dbPath)
        assertEquals(2, info.count)
        assertEquals(10L, info.totalBytes)
        assertEquals(backupsDir, info.directory)
    }
}
