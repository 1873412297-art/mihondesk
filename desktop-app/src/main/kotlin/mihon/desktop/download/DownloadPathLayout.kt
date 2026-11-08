package mihon.desktop.download

import mihon.desktop.download.DownloadFileNames
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Directory layout of downloaded chapters, including legacy locations adopted from older versions. */
internal class DownloadPathLayout(
    rawDownloadsDir: Path,
    legacyDownloadsDirs: List<Path>,
    private val registeredChapterDirectory: (Long, Long, Long) -> Path?,
    private val saveAsCbz: () -> Boolean,
) {
    private data class ChapterKey(val sourceId: Long, val mangaId: Long, val chapterId: Long)
    private val legacyChapters = ConcurrentHashMap<ChapterKey, Path>()
    private val legacyTemporaryChapters = ConcurrentHashMap<ChapterKey, Path>()
    val registeredDirectories: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    val downloadsDir: Path = rawDownloadsDir.toAbsolutePath().normalize()
    val downloadRoots: List<Path> = buildList {
        add(this@DownloadPathLayout.downloadsDir)
        legacyDownloadsDirs.forEach { add(it.toAbsolutePath().normalize()) }
    }.distinct()

    fun sanitizeFileName(name: String): String = DownloadFileNames.current(name)

    fun getMangaDir(sourceId: Long, mangaTitle: String, mangaId: Long? = null): Path {
        return if (mangaId == null) {
            getMangaDir(downloadsDir, sourceId, mangaTitle)
        } else {
            downloadsDir.resolve(sourceId.toString()).resolve("manga-$mangaId")
        }
    }

    fun getChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path {
        require((mangaId == null) == (chapterId == null))
        return getMangaDir(sourceId, mangaTitle, mangaId)
            .resolve(chapterId?.let { "chapter-$it" } ?: sanitizeFileName(chapterName))
    }

    fun findChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path? = chapterDirectories(sourceId, mangaTitle, chapterName, mangaId, chapterId).firstOrNull(Files::isDirectory)

    /** Resolves the published chapter as a directory or, for CBZ downloads, as a single archive. */
    fun findChapterEntry(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): ChapterEntry? = chapterEntries(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        .firstOrNull { entry ->
            if (entry.archive) Files.isRegularFile(entry.path) else Files.isDirectory(entry.path)
        }

    internal fun chapterEntries(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long?,
        chapterId: Long?,
    ): List<ChapterEntry> {
        val directories = chapterDirectories(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            .map { ChapterEntry(it, archive = false) }
        if (mangaId == null || chapterId == null) return directories
        return directories + chapterArchives(sourceId, mangaId, chapterId).map { ChapterEntry(it, archive = true) }
    }

    private fun chapterDirectories(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long?,
        chapterId: Long?,
    ): List<Path> {
        require((mangaId == null) == (chapterId == null))
        if (mangaId == null || chapterId == null) {
            // Older releases sanitized names differently; try both so their downloads stay visible.
            return downloadRoots.flatMap { root ->
                DownloadFileNames.candidates(mangaTitle).flatMap { manga ->
                    DownloadFileNames.candidates(chapterName).map {
                        root.resolve("$sourceId").resolve(manga).resolve(it)
                    }
                }
            }.distinct()
        }
        val key = ChapterKey(sourceId, mangaId, chapterId)
        return buildList {
            downloadRoots.forEach { root ->
                add(root.resolve(sourceId.toString()).resolve("manga-$mangaId").resolve("chapter-$chapterId"))
            }
            registeredChapterPath(sourceId, mangaId, chapterId)?.let(::add)
            legacyChapters[key]?.let(::add)
        }.distinct()
    }

    private fun chapterArchives(sourceId: Long, mangaId: Long, chapterId: Long): List<Path> = buildList {
        downloadRoots.forEach { root ->
            add(
                root.resolve(sourceId.toString()).resolve("manga-$mangaId")
                    .resolve("chapter-$chapterId$ARCHIVE_SUFFIX"),
            )
        }
        registeredChapterPath(sourceId, mangaId, chapterId)
            ?.takeIf { it.fileName?.toString()?.endsWith(ARCHIVE_SUFFIX) == true }
            ?.let(::add)
    }.distinct()

    /** The published path of the last registration; either a directory or an archive. */
    private fun registeredChapterPath(sourceId: Long, mangaId: Long, chapterId: Long): Path? =
        registeredChapterDirectory(sourceId, mangaId, chapterId)?.toAbsolutePath()?.normalize()?.also {
            registeredDirectories.add(it)
        }

    /** Where a finished download is published, before anything exists on disk. */
    internal fun expectedChapterEntry(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long?,
        chapterId: Long?,
    ): ChapterEntry {
        val directory = getChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        return if (saveAsCbz() && mangaId != null && chapterId != null) {
            ChapterEntry(directory.resolveSibling("${directory.fileName}$ARCHIVE_SUFFIX"), archive = true)
        } else {
            ChapterEntry(directory, archive = false)
        }
    }

    fun getTempChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path {
        val chapter = getChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        return chapter.resolveSibling("${chapter.fileName}_tmp")
    }

    /** Only an unambiguous pre-ID queue entry may claim a legacy name-based directory. */
    internal fun adoptLegacyDownloads(downloads: List<DesktopDownload>) {
        val legacy = downloads.filter { it.storageLayoutVersion == 0 }
        val mangaOwners = legacy.groupBy {
            it.sourceId to
                sanitizeFileName(it.mangaTitle).lowercase(java.util.Locale.ROOT)
        }
        legacy.groupBy {
            Triple(
                it.sourceId,
                sanitizeFileName(it.mangaTitle).lowercase(java.util.Locale.ROOT),
                sanitizeFileName(it.chapterName).lowercase(java.util.Locale.ROOT),
            )
        }.values.filter { group ->
            group.map { it.chapterId }.distinct().size == 1 &&
                mangaOwners.getValue(
                    group.first().sourceId to
                        sanitizeFileName(group.first().mangaTitle).lowercase(java.util.Locale.ROOT),
                )
                    .map { it.mangaId }.distinct().size == 1
        }.forEach { group ->
            val item = group.first()
            val key = ChapterKey(item.sourceId, item.mangaId, item.chapterId)
            findChapterDir(item.sourceId, item.mangaTitle, item.chapterName)?.let { legacyChapters[key] = it }
            val temporary = getTempChapterDir(item.sourceId, item.mangaTitle, item.chapterName)
            if (Files.isDirectory(temporary)) legacyTemporaryChapters[key] = temporary
        }
    }

    internal fun restoreLegacyTemporaryPages(download: DesktopDownload, target: Path) {
        val old = legacyTemporaryChapters[ChapterKey(download.sourceId, download.mangaId, download.chapterId)] ?: return
        download.pages.forEach { page ->
            val previous = DownloadPageFiles.getPageFile(old, page.index)
            val current = DownloadPageFiles.getPageFile(target, page.index)
            if (!Files.exists(current) && DownloadPageFiles.isValidPage(previous)) Files.copy(previous, current)
        }
    }

    private fun getMangaDir(root: Path, sourceId: Long, mangaTitle: String): Path =
        root.resolve(sourceId.toString()).resolve(sanitizeFileName(mangaTitle))
}
