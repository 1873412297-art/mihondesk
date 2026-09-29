package mihon.desktop.storage

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class StorageUsage(
    val downloads: Long,
    val imageCache: Long,
    val database: Long,
    val extensions: Long,
    val covers: Long,
    val logs: Long,
    val backups: Long,
)

/** Measures profile-owned files without following links into other directories. Call on an IO dispatcher. */
class StorageUsageCalculator {
    fun calculate(profileRoot: Path, downloadsDir: Path, backupDir: Path): StorageUsage = StorageUsage(
        downloads = sizeOf(downloadsDir),
        imageCache = sizeOf(profileRoot.resolve("cache")),
        database = sizeOf(profileRoot.resolve("database")),
        extensions = sizeOf(profileRoot.resolve("extensions")),
        covers = sizeOf(profileRoot.resolve("covers")),
        logs = sizeOf(profileRoot.resolve("logs")),
        backups = sizeOf(backupDir),
    )

    private fun sizeOf(root: Path): Long {
        if (!Files.exists(root)) return 0
        var bytes = 0L
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) bytes += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException?): FileVisitResult =
                    FileVisitResult.CONTINUE
            },
        )
        return bytes
    }
}
