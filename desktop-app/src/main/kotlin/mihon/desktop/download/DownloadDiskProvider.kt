package mihon.desktop.download

import kotlinx.serialization.Serializable
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
internal data class DownloadedPageMetadata(
    val index: Int,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    /** On-disk parts of this page; 1 unless the page was split while downloading. */
    val partCount: Int = 1,
)

internal data class DownloadChapterInspection(
    val expectedPages: Int,
    val validPages: Map<Int, DownloadedPageMetadata>,
) {
    val isComplete: Boolean
        get() = expectedPages > 0 && validPages.size == expectedPages

    val totalBytes: Long
        get() = validPages.values.sumOf(DownloadedPageMetadata::sizeBytes)
}

/** A published chapter is either a page directory or a single CBZ archive. */
data class ChapterEntry(val path: Path, val archive: Boolean) {
    val assetKind: String
        get() = if (archive) ARCHIVE_ASSET_KIND else DIRECTORY_ASSET_KIND
}

private const val DIRECTORY_ASSET_KIND = "DIRECTORY"
private const val ARCHIVE_ASSET_KIND = "ARCHIVE"
internal const val ARCHIVE_SUFFIX = ".cbz"
internal const val ARCHIVE_STAGING_SUFFIX = ".cbz_tmp"

class DownloadDiskProvider(
    downloadsDir: Path,
    legacyDownloadsDirs: List<Path> = emptyList(),
    private val minDiskSpaceBytes: Long = 50L * 1024 * 1024, // 50 MB safety margin
    private val registeredChapterDirectory: (Long, Long, Long) -> Path? = { _, _, _ -> null },
    private val saveAsCbz: () -> Boolean = { false },
) {
    private val layout = DownloadPathLayout(downloadsDir, legacyDownloadsDirs, registeredChapterDirectory, saveAsCbz)
    val downloadsDir: Path get() = layout.downloadsDir
    val downloadRoots: List<Path> get() = layout.downloadRoots

    init {
        if (!Files.exists(downloadsDir)) {
            Files.createDirectories(downloadsDir)
        }
        if (!Files.isDirectory(downloadsDir)) {
            throw IOException("Download path is not a directory: $downloadsDir")
        }
    }

    /** The Windows-safe name for a new title-based directory; see [DownloadFileNames]. */
    fun sanitizeFileName(name: String): String = layout.sanitizeFileName(name)

    fun getMangaDir(sourceId: Long, mangaTitle: String, mangaId: Long? = null): Path =
        layout.getMangaDir(sourceId, mangaTitle, mangaId)

    fun getChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path = layout.getChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)

    fun findChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path? = layout.findChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)

    fun findChapterEntry(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): ChapterEntry? = layout.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)

    fun getTempChapterDir(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Path = layout.getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)

    internal fun adoptLegacyDownloads(downloads: List<DesktopDownload>) = layout.adoptLegacyDownloads(downloads)

    internal fun restoreLegacyTemporaryPages(download: DesktopDownload, target: Path) =
        layout.restoreLegacyTemporaryPages(download, target)

    fun checkDiskSpace(requiredBytes: Long = minDiskSpaceBytes): Boolean {
        return try {
            val store = Files.getFileStore(downloadsDir)
            store.usableSpace >= requiredBytes
        } catch (e: Exception) {
            false // An unavailable download volume must not be treated as writable.
        }
    }

    fun getPageFile(tempDir: Path, pageIndex: Int, extension: String = "jpg"): Path =
        DownloadPageFiles.getPageFile(tempDir, pageIndex, extension)

    internal fun getSplitPartFile(tempDir: Path, pageIndex: Int, partIndex: Int): Path =
        DownloadPageFiles.getSplitPartFile(tempDir, pageIndex, partIndex)

    internal fun pageFiles(chapterDir: Path, pageIndex: Int, expectedPartCount: Int? = null): List<Path> =
        DownloadPageFiles.pageFiles(chapterDir, pageIndex, expectedPartCount)

    internal fun pageMetadata(
        chapterDir: Path,
        pageIndex: Int,
        expectedPartCount: Int? = null,
    ): DownloadedPageMetadata? = DownloadPageFiles.pageMetadata(chapterDir, pageIndex, expectedPartCount)

    internal fun validPageMetadata(
        chapterDir: Path,
        pageIndex: Int,
        expectedPartCount: Int? = null,
    ): DownloadedPageMetadata? = DownloadPageFiles.validPageMetadata(chapterDir, pageIndex, expectedPartCount)

    fun savePage(tempDir: Path, pageIndex: Int, bytes: ByteArray, extension: String = "jpg"): Path =
        DownloadPageFiles.savePage(tempDir, pageIndex, bytes, extension)

    fun isValidPage(file: Path): Boolean = DownloadPageFiles.isValidPage(file)

    fun cleanPartialPages(tempDir: Path) = DownloadPageFiles.cleanPartialPages(tempDir)

    /**
     * Verifies a published chapter against its cheap completion manifest. Legacy or changed files
     * are decoded once and receive a fresh manifest so later startup checks remain inexpensive.
     */
    internal fun inspectChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        expectedPageIndexes: List<Int>,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): DownloadChapterInspection = ChapterInspection.inspect(
        layout,
        sourceId,
        mangaTitle,
        chapterName,
        expectedPageIndexes,
        mangaId,
        chapterId,
    )

    fun isChapterDownloaded(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean = ChapterInspection.isDownloaded(layout, sourceId, mangaTitle, chapterName, mangaId, chapterId)

    fun deleteTempChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ) = DownloadCleanup.deleteTempChapter(layout, sourceId, mangaTitle, chapterName, mangaId, chapterId)

    fun deleteChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean = DownloadCleanup.deleteChapter(layout, sourceId, mangaTitle, chapterName, mangaId, chapterId)

    internal fun deleteChapterEntry(entry: ChapterEntry): Boolean = DownloadCleanup.deleteResolvedChapter(layout, entry)

    fun finalizeChapter(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalPages: Int,
        mutationPort: LibraryMutationPort? = null,
        pagePartCounts: Map<Int, Int> = emptyMap(),
    ): Path {
        val tempDir = getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        if (!Files.exists(tempDir) || !Files.isDirectory(tempDir)) {
            throw IOException("Temporary download directory $tempDir does not exist")
        }

        // Every page must remain decodable before publishing an offline chapter. Pages split while
        // downloading also have to contain every recorded part, or a truncated page is published.
        // Drop staging and orphaned split parts first so validation sees the final file set.
        cleanPartialPages(tempDir)
        DownloadPageFiles.removeOrphanedSplitParts(tempDir)
        val metadata = mutableListOf<DownloadedPageMetadata>()
        for (i in 0 until totalPages) {
            val expectedParts = pagePartCounts[i]?.takeIf { it > 1 }
            val page = validPageMetadata(tempDir, i, expectedParts)
                ?: throw IOException("Page $i (${getPageFile(tempDir, i)}) is missing, corrupt or incomplete")
            metadata += page
        }
        val totalBytes = metadata.sumOf(DownloadedPageMetadata::sizeBytes)

        DownloadArchiveCodec.writeManifest(tempDir, metadata)

        val target = layout.expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        Files.createDirectories(target.path.parent)
        val register = {
            registerCompletedChapter(
                sourceId = sourceId,
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = mangaTitle,
                chapterName = chapterName,
                totalBytes = totalBytes,
                mutationPort = mutationPort,
            )
        }
        return if (target.archive) {
            publishArchive(tempDir, target.path, register)
        } else {
            publishDirectory(tempDir, target.path, register)
        }
    }

    private fun publishDirectory(tempDir: Path, targetDir: Path, register: () -> Unit): Path {
        val previousDir = targetDir.resolveSibling("${targetDir.fileName}.previous")
        // Keep the previous complete chapter until replacement succeeds.
        if (Files.exists(targetDir)) {
            DownloadCleanup.deleteDirectory(layout, previousDir)
            Files.move(targetDir, previousDir)
        }
        var published = false
        try {
            try {
                Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempDir, targetDir)
            }
            published = true
            // Commit both records together. A registration failure must leave the downloaded
            // pages retryable and retain any previously registered offline chapter.
            register()
        } catch (error: Exception) {
            runCatching {
                if (published) Files.move(targetDir, tempDir)
                if (!Files.exists(targetDir) && Files.exists(previousDir)) {
                    Files.move(previousDir, targetDir)
                }
            }.onFailure(error::addSuppressed)
            throw error
        }
        DownloadCleanup.deleteDirectory(layout, previousDir)

        return targetDir
    }

    /**
     * Publishes a chapter as a single archive. The archive is staged beside its target so a failure
     * leaves the downloaded pages retryable and any previously published chapter untouched.
     */
    private fun publishArchive(tempDir: Path, targetFile: Path, register: () -> Unit): Path {
        val staging = targetFile.resolveSibling("${targetFile.fileName}$ARCHIVE_STAGING_SUFFIX")
        DownloadArchiveCodec.writeArchive(tempDir, staging)
        // A chapter downloaded before the setting was enabled is still published as a directory.
        val replaced = listOf(
            targetFile.resolveSibling(targetFile.fileName.toString().removeSuffix(ARCHIVE_SUFFIX)),
            targetFile,
        )
        val displaced = mutableListOf<Pair<Path, Path>>()
        var published = false
        try {
            replaced.forEach { existing ->
                if (Files.exists(existing)) {
                    val previous = existing.resolveSibling("${existing.fileName}.previous")
                    DownloadCleanup.deleteDirectory(layout, previous)
                    Files.move(existing, previous)
                    displaced += previous to existing
                }
            }
            try {
                Files.move(staging, targetFile, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, targetFile)
            }
            published = true
            register()
        } catch (error: Exception) {
            runCatching {
                if (published) Files.move(targetFile, staging)
                displaced.asReversed().forEach { (previous, original) ->
                    if (!Files.exists(original) && Files.exists(previous)) Files.move(previous, original)
                }
                // Retryability comes from the untouched page directory, not from a staging archive.
                Files.deleteIfExists(staging)
            }.onFailure(error::addSuppressed)
            throw error
        }
        displaced.forEach { DownloadCleanup.deleteDirectory(layout, it.first) }
        DownloadCleanup.deleteDirectory(layout, tempDir)

        return targetFile
    }

    internal fun registerCompletedChapter(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalBytes: Long,
        mutationPort: LibraryMutationPort?,
    ) {
        mutationPort?.transaction {
            val entry = layout.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
                ?: layout.expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            val mangaDir = entry.path.parent
            insertLocalManga(
                LocalMangaRecord(
                    mangaId = mangaId,
                    storagePath = mangaDir.toAbsolutePath().toString(),
                    manifestSha256 = "",
                    importedAt = System.currentTimeMillis(),
                ),
            )
            insertLocalChapter(
                LocalChapterRecord(
                    chapterId = chapterId,
                    relativePath = entry.path.fileName.toString(),
                    assetKind = entry.assetKind,
                    sizeBytes = totalBytes,
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    internal fun isChapterRegistrationCurrent(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalBytes: Long,
        mutationPort: LibraryMutationPort,
    ): Boolean {
        val entry = layout.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            ?: layout.expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        return mutationPort.isLocalChapterAssetRegistered(
            mangaId = mangaId,
            storagePath = entry.path.parent.toAbsolutePath().toString(),
            chapterId = chapterId,
            relativePath = entry.path.fileName.toString(),
            sizeBytes = totalBytes,
            assetKind = entry.assetKind,
        )
    }
}
