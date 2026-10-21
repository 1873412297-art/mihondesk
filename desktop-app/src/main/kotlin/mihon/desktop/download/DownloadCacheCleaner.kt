package mihon.desktop.download

import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryRepository
import java.nio.file.Files
import java.nio.file.Path

data class CleanReport(
    val deletedChaptersCount: Int,
    val freedBytes: Long,
)

class DownloadCacheCleaner(
    private val repository: LibraryRepository,
    private val diskProvider: DownloadDiskProvider,
    /** Manga that must never be cleaned as orphans (e.g. ones with queued downloads). */
    private val activeMangaIds: () -> Set<Long> = { emptySet() },
    private val coordinatedDelete: ((MangaRecord, LibraryChapter) -> Boolean)? = null,
) {
    fun calculateDownloadSize(): Long = diskProvider.downloadRoots.sumOf(Companion::calculateDownloadSize)

    fun deleteReadChapters(): CleanReport =
        Companion.deleteReadChapters(repository, diskProvider, coordinatedDelete)

    fun clearImageDiskCache(cacheDir: Path): Long = Companion.clearImageDiskCache(cacheDir)

    /** Deletes downloaded chapters of manga that are no longer in the library (not favourited or gone). */
    fun deleteDownloadsNotInLibrary(protectedMangaIds: Set<Long> = emptySet(), dryRun: Boolean = false): CleanReport =
        Companion.deleteDownloadsNotInLibrary(
            repository.allMangaSnapshot(),
            diskProvider,
            protectedMangaIds + activeMangaIds(),
            dryRun,
        )

    companion object {
        private val MANGA_ID_DIR = Regex("manga-(\\d+)")

        /**
         * Removes per-manga download folders whose manga is not a library favourite.
         * Folders are `<root>/<sourceId>/manga-<id>` (current layout) or `<root>/<sourceId>/<title>`
         * (legacy). [protectedMangaIds] (e.g. manga with queued downloads) are always kept.
         * Returns the number of chapters (entries inside the removed folders) and bytes freed.
         */
        fun deleteDownloadsNotInLibrary(
            manga: List<MangaRecord>,
            diskProvider: DownloadDiskProvider,
            protectedMangaIds: Set<Long> = emptySet(),
            dryRun: Boolean = false,
        ): CleanReport {
            val favourites = manga.filter { it.favorite }
            val keptIds = favourites.map { it.id }.toSet() + protectedMangaIds
            val keptLegacy = (favourites + manga.filter { it.id in protectedMangaIds })
                .map { it.sourceId.toString() to diskProvider.sanitizeFileName(it.title) }
                .toSet()
            var chapters = 0
            var freed = 0L
            for (root in diskProvider.downloadRoots) {
                val sources = runCatching { Files.list(root).use { it.toList() } }.getOrDefault(emptyList())
                for (sourceDir in sources) {
                    val sourceId = sourceDir.fileName.toString()
                    if (!Files.isDirectory(sourceDir) || sourceId.toLongOrNull() == null) continue
                    val mangaDirs = runCatching { Files.list(sourceDir).use { it.toList() } }.getOrDefault(emptyList())
                    for (mangaDir in mangaDirs) {
                        if (!Files.isDirectory(mangaDir)) continue
                        val name = mangaDir.fileName.toString()
                        val id = MANGA_ID_DIR.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()
                        val keep = if (id != null) id in keptIds else (sourceId to name) in keptLegacy
                        if (keep) continue
                        val entries = runCatching { Files.list(mangaDir).use { it.count().toInt() } }.getOrDefault(0)
                        val size = getDirectorySize(mangaDir)
                        if (!dryRun && !deleteRecursively(mangaDir)) continue
                        chapters += entries
                        freed += size
                    }
                }
            }
            return CleanReport(deletedChaptersCount = chapters, freedBytes = freed)
        }

        private fun deleteRecursively(dir: Path): Boolean = runCatching {
            Files.walk(dir).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            true
        }.getOrDefault(false)

        fun calculateDownloadSize(downloadsDir: Path): Long {
            if (!Files.exists(downloadsDir)) return 0L
            return try {
                Files.walk(downloadsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .mapToLong {
                            try {
                                Files.size(it)
                            } catch (_: Exception) {
                                0L
                            }
                        }
                        .sum()
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun getDirectorySize(dir: Path): Long {
            if (!Files.exists(dir)) return 0L
            return try {
                Files.walk(dir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .mapToLong {
                            try {
                                Files.size(it)
                            } catch (_: Exception) {
                                0L
                            }
                        }
                        .sum()
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun deleteReadChapters(
            repository: LibraryRepository,
            diskProvider: DownloadDiskProvider,
            coordinatedDelete: ((MangaRecord, LibraryChapter) -> Boolean)? = null,
        ): CleanReport {
            val allManga = repository.allMangaSnapshot()
            var deletedCount = 0
            var totalFreed = 0L

            for (manga in allManga) {
                val chapters = repository.chapterSnapshot(manga.id)
                for (ch in chapters) {
                    if (ch.read) {
                        val chapterEntry = diskProvider.findChapterEntry(
                            manga.sourceId,
                            manga.title,
                            ch.name,
                            manga.id,
                            ch.id,
                        )
                        if (chapterEntry != null) {
                            val size = getDirectorySize(chapterEntry.path)
                            val success = coordinatedDelete?.invoke(manga, ch)
                                ?: diskProvider.deleteChapter(manga.sourceId, manga.title, ch.name, manga.id, ch.id)
                            if (success) {
                                deletedCount++
                                totalFreed += size
                            }
                        }
                    }
                }
            }

            return CleanReport(
                deletedChaptersCount = deletedCount,
                freedBytes = totalFreed,
            )
        }

        fun clearImageDiskCache(cacheDir: Path): Long {
            if (!Files.exists(cacheDir)) return 0L
            var freed = 0L
            try {
                Files.walk(cacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { path ->
                        if (path != cacheDir) {
                            try {
                                if (Files.isRegularFile(path)) {
                                    freed += Files.size(path)
                                }
                                Files.deleteIfExists(path)
                            } catch (e: Exception) {
                                mihon.desktop.logging.DesktopLogger.warn(
                                    "DownloadCacheCleaner",
                                    "Failed to delete cache file $path",
                                    e,
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                mihon.desktop.logging.DesktopLogger.warn(
                    "DownloadCacheCleaner",
                    "Failed to clean image cache in $cacheDir",
                    e,
                )
            }
            return freed
        }
    }
}
