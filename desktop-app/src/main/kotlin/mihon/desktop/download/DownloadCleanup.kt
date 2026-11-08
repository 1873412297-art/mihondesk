package mihon.desktop.download

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private const val DELETE_ATTEMPTS = 4
private const val DELETE_RETRY_DELAY_MILLIS = 25L

/** Deleting temporary and published chapters, with retries for Windows file locks. */
internal object DownloadCleanup {

    fun deleteTempChapter(
        layout: DownloadPathLayout,
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ) {
        deleteDirectory(layout, layout.getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId))
    }

    internal fun deleteDirectory(layout: DownloadPathLayout, dir: Path) {
        requireDeletable(layout, dir)
        deleteWithRetry(dir) {
            Files.walk(dir).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    /**
     * Windows refuses to delete a file another thread still holds open, and the startup recovery,
     * the reader or the extension host can be holding one at that moment. Losing that race is
     * retried briefly instead of being reported to the user as a failed deletion.
     */
    private fun deleteWithRetry(target: Path, delete: () -> Unit) {
        var attempt = 1
        while (true) {
            val failure = try {
                if (!Files.exists(target)) return
                delete()
                return
            } catch (error: Exception) {
                error
            }
            // A concurrent removal finished the job, or the previous attempt already lost the race.
            if (!Files.exists(target)) return
            if (attempt >= DELETE_ATTEMPTS) throw failure
            Thread.sleep(DELETE_RETRY_DELAY_MILLIS * attempt)
            attempt += 1
        }
    }

    private fun requireDeletable(layout: DownloadPathLayout, path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(
            normalized in layout.registeredDirectories || layout.downloadRoots.any { root ->
                normalized.startsWith(root) && root.relativize(normalized).nameCount >= 3
            },
        ) {
            "Refusing to delete outside a download chapter directory: $path"
        }
    }

    private fun deleteChapterEntry(layout: DownloadPathLayout, entry: ChapterEntry) {
        requireDeletable(layout, entry.path)
        if (entry.archive) {
            deleteWithRetry(entry.path) { Files.deleteIfExists(entry.path) }
        } else {
            deleteDirectory(layout, entry.path)
        }
    }

    fun deleteChapter(
        layout: DownloadPathLayout,
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean {
        val candidates = layout.chapterEntries(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        val published = candidates.filter { Files.exists(it.path) }
        // Staging archives never mark a chapter as downloaded; drop them along with the chapter.
        candidates.filter(ChapterEntry::archive).forEach { entry ->
            runCatching {
                deleteDirectory(layout, entry.path.resolveSibling("${entry.path.fileName}$ARCHIVE_STAGING_SUFFIX"))
            }
        }
        if (published.isEmpty()) return false
        return published.all { entry ->
            runCatching {
                deleteChapterEntry(layout, entry)
                val mangaDir = entry.path.parent
                if (Files.isDirectory(mangaDir) && Files.list(mangaDir).use { !it.findAny().isPresent }) {
                    Files.deleteIfExists(mangaDir)
                }
            }.isSuccess
        }
    }

    /**
     * Deletes one already-resolved published entry (directory or archive), including its staging
     * archive and an empty manga folder. Callers use this when the database registration must be
     * removed first but the registered path is only resolvable while that row still exists.
     */
    fun deleteResolvedChapter(layout: DownloadPathLayout, entry: ChapterEntry): Boolean = runCatching {
        if (entry.archive) {
            deleteDirectory(layout, entry.path.resolveSibling("${entry.path.fileName}$ARCHIVE_STAGING_SUFFIX"))
        }
        deleteChapterEntry(layout, entry)
        val mangaDir = entry.path.parent
        if (Files.isDirectory(mangaDir) && Files.list(mangaDir).use { !it.findAny().isPresent }) {
            Files.deleteIfExists(mangaDir)
        }
    }.isSuccess
}
