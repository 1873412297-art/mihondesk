package mihon.desktop.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.reader.source.ImageEntryPolicy
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
private data class DownloadChapterManifest(
    val version: Int = 1,
    val totalPages: Int,
    val pages: List<DownloadedPageMetadata>,
)

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
private const val ARCHIVE_SUFFIX = ".cbz"
private const val ARCHIVE_STAGING_SUFFIX = ".cbz_tmp"
private const val SPLIT_PART_SUFFIX = ".jpg"

/** Only leaves whose whole stem is a page/part number are split-page leftovers. */
private val SPLIT_PART_NAME = Regex("^(\\d{3,})__(\\d{3,})\\.jpg$")

/** `%03d__%03d.jpg` — the name of one part of a split page, mirroring Android's `splitImageName`. */
internal fun splitPartFileName(pageIndex: Int, partIndex: Int): String =
    String.format("%03d__%03d.jpg", pageIndex + 1, partIndex + 1)

class DownloadDiskProvider(
    downloadsDir: Path,
    legacyDownloadsDirs: List<Path> = emptyList(),
    private val minDiskSpaceBytes: Long = 50L * 1024 * 1024, // 50 MB safety margin
    private val registeredChapterDirectory: (Long, Long, Long) -> Path? = { _, _, _ -> null },
    private val saveAsCbz: () -> Boolean = { false },
) {
    private data class ChapterKey(val sourceId: Long, val mangaId: Long, val chapterId: Long)
    private val legacyChapters = java.util.concurrent.ConcurrentHashMap<ChapterKey, Path>()
    private val legacyTemporaryChapters = java.util.concurrent.ConcurrentHashMap<ChapterKey, Path>()
    private val registeredDirectories = java.util.concurrent.ConcurrentHashMap.newKeySet<Path>()
    private val manifestJson = Json { ignoreUnknownKeys = true }
    val downloadsDir: Path = downloadsDir.toAbsolutePath().normalize()
    val downloadRoots: List<Path> = buildList {
        add(this@DownloadDiskProvider.downloadsDir)
        legacyDownloadsDirs.forEach { add(it.toAbsolutePath().normalize()) }
    }.distinct()

    init {
        if (!Files.exists(downloadsDir)) {
            Files.createDirectories(downloadsDir)
        }
        if (!Files.isDirectory(downloadsDir)) {
            throw IOException("Download path is not a directory: $downloadsDir")
        }
    }

    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\x00-\\x1f\\\\/:*?\"<>|]"), "_")
            .trim().trimEnd('.', ' ').ifEmpty { "unnamed" }
        val deviceName = sanitized.substringBefore('.').trimEnd()
        return if (deviceName.matches(Regex("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))) {
            "_$sanitized"
        } else {
            sanitized
        }
    }

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

    private fun chapterEntries(
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
            return downloadRoots.map { root ->
                getMangaDir(root, sourceId, mangaTitle).resolve(sanitizeFileName(chapterName))
            }
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
    private fun expectedChapterEntry(
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
            val previous = getPageFile(old, page.index)
            val current = getPageFile(target, page.index)
            if (!Files.exists(current) && isValidPage(previous)) Files.copy(previous, current)
        }
    }

    fun checkDiskSpace(requiredBytes: Long = minDiskSpaceBytes): Boolean {
        return try {
            val store = Files.getFileStore(downloadsDir)
            store.usableSpace >= requiredBytes
        } catch (e: Exception) {
            false // An unavailable download volume must not be treated as writable.
        }
    }

    fun getPageFile(tempDir: Path, pageIndex: Int, extension: String = "jpg"): Path {
        val ext = if (extension.startsWith(".")) extension.substring(1) else extension
        val fileName = String.format("%03d.%s", pageIndex + 1, ext)
        return tempDir.resolve(fileName)
    }

    /**
     * Parts of a split page are named `<page>__<part>`; the natural page order then keeps every
     * part directly after the page it belongs to, so the reader sees them as consecutive pages.
     * [partIndex] is zero-based.
     */
    internal fun getSplitPartFile(tempDir: Path, pageIndex: Int, partIndex: Int): Path {
        require(partIndex >= 0) { "partIndex must not be negative" }
        return tempDir.resolve(splitPartFileName(pageIndex, partIndex))
    }

    /**
     * Files backing one stored page: the single `%03d.jpg`, or the `%03d__NNN.jpg` parts of a page
     * split while downloading. A page whose parts are missing is not downloaded, so an expected
     * part count above one only resolves when every part is present.
     */
    internal fun pageFiles(chapterDir: Path, pageIndex: Int, expectedPartCount: Int? = null): List<Path> {
        if (expectedPartCount != null && expectedPartCount > 1) {
            val parts = (0 until expectedPartCount).map { getSplitPartFile(chapterDir, pageIndex, it) }
            return if (parts.all(Files::isRegularFile)) parts else emptyList()
        }
        val whole = getPageFile(chapterDir, pageIndex)
        if (Files.isRegularFile(whole)) return listOf(whole)
        return contiguousSplitParts(chapterDir, pageIndex)
    }

    /** Only a gap-free run starting at part 1 is a complete split; anything past a gap is leftover. */
    private fun contiguousSplitParts(chapterDir: Path, pageIndex: Int): List<Path> {
        if (!Files.isDirectory(chapterDir)) return emptyList()
        val prefix = String.format("%03d__", pageIndex + 1)
        val numbered = runCatching {
            Files.list(chapterDir).use { paths ->
                paths.filter { Files.isRegularFile(it) }
                    .map { it to it.fileName.toString() }
                    .filter { (_, name) -> name.startsWith(prefix) && name.endsWith(SPLIT_PART_SUFFIX) }
                    .map { (path, name) ->
                        name.removePrefix(prefix).removeSuffix(SPLIT_PART_SUFFIX).toIntOrNull() to path
                    }
                    .filter { (partIndex, _) -> partIndex != null && partIndex > 0 }
                    .toList()
            }
        }.getOrDefault(emptyList())
        val parts = mutableListOf<Path>()
        for ((position, entry) in numbered.sortedBy { it.first }.withIndex()) {
            if (entry.first != position + 1) break
            parts.add(entry.second)
        }
        return parts
    }

    /** Aggregate metadata of a page without decoding it, or null when none of its files exist. */
    internal fun pageMetadata(
        chapterDir: Path,
        pageIndex: Int,
        expectedPartCount: Int? = null,
    ): DownloadedPageMetadata? {
        val files = pageFiles(chapterDir, pageIndex, expectedPartCount)
        return if (files.isEmpty()) null else metadataFor(pageIndex, files)
    }

    /** Aggregate metadata of a page that still decodes, or null when it is missing or corrupt. */
    internal fun validPageMetadata(
        chapterDir: Path,
        pageIndex: Int,
        expectedPartCount: Int? = null,
    ): DownloadedPageMetadata? {
        val files = pageFiles(chapterDir, pageIndex, expectedPartCount)
        if (files.isEmpty() || files.any { !isValidPage(it) }) return null
        return metadataFor(pageIndex, files)
    }

    private fun metadataFor(index: Int, files: List<Path>): DownloadedPageMetadata? = runCatching {
        DownloadedPageMetadata(
            index = index,
            sizeBytes = files.sumOf { Files.size(it) },
            modifiedAtMillis = files.maxOf { Files.getLastModifiedTime(it).toMillis() },
            partCount = files.size,
        )
    }.getOrNull()

    fun savePage(tempDir: Path, pageIndex: Int, bytes: ByteArray, extension: String = "jpg"): Path {
        Files.createDirectories(tempDir)
        val file = getPageFile(tempDir, pageIndex, extension)
        val partial = file.resolveSibling("${file.fileName}.part")
        try {
            Files.write(partial, bytes)
            if (!isValidPage(partial)) throw IOException("Page ${pageIndex + 1} is corrupt or incomplete")
            try {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING)
            }
            return file
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /** Decode a bounded sample instead of trusting length or a format signature. */
    fun isValidPage(file: Path): Boolean = runCatching {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return@runCatching false
        if (!hasCompleteContainer(file)) return@runCatching false
        javax.imageio.ImageIO.createImageInputStream(file.toFile()).use { input ->
            if (input == null) return@runCatching false
            val readers = javax.imageio.ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                // The same native codec family used by the desktop reader covers WebP/AVIF.
                org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(file)).use { image ->
                    return@runCatching image.width > 0 && image.height > 0
                }
            }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0) return@runCatching false
                val params = reader.defaultReadParam
                val sample = ((maxOf(width, height) + 1023L) / 1024L).toInt().coerceAtLeast(1)
                params.setSourceSubsampling(sample, sample, 0, 0)
                val decoded = reader.read(0, params) ?: return@runCatching false
                decoded.flush()
                true
            } finally {
                reader.dispose()
            }
        }
    }.getOrDefault(false)

    private fun hasCompleteContainer(file: Path): Boolean = java.io.RandomAccessFile(file.toFile(), "r").use { input ->
        if (input.length() < 12) return@use false
        val header = ByteArray(12).also(input::readFully)
        when {
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() -> {
                input.seek(input.length() - 2)
                input.readUnsignedShort() == 0xffd9
            }
            header[0] == 0x89.toByte() && header.copyOfRange(1, 4).toString(Charsets.US_ASCII) == "PNG" -> {
                input.seek(input.length() - 12)
                val end = ByteArray(12).also(input::readFully)
                end.toList() == listOf<Byte>(0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)
            }
            header.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "GIF" -> {
                input.seek(input.length() - 1)
                input.read() == 0x3b
            }
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" -> {
                val size = (4..7).fold(0L) { value, index ->
                    value or
                        ((header[index].toLong() and 255) shl ((index - 4) * 8))
                }
                size + 8 == input.length()
            }
            else -> true
        }
    }
    fun cleanPartialPages(tempDir: Path) {
        if (!Files.isDirectory(tempDir)) return
        Files.list(tempDir).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".part") }.forEach { Files.deleteIfExists(it) }
        }
    }

    /**
     * A split that failed - or a crash between writing the first part and deleting the original -
     * can leave parts beside the whole page. The reader would show that page twice, so the parts
     * of a page that still exists whole are dropped before the chapter is published.
     */
    private fun removeOrphanedSplitParts(chapterDir: Path) {
        val orphans = runCatching {
            Files.list(chapterDir).use { paths ->
                paths.map { it.fileName.toString() }
                    .filter { SPLIT_PART_NAME.matches(it) }
                    .toList()
            }
        }.getOrDefault(emptyList())
        orphans.forEach { name ->
            val pageIndex = SPLIT_PART_NAME.matchEntire(name)
                ?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
                ?: return@forEach
            if (Files.isRegularFile(getPageFile(chapterDir, pageIndex))) {
                Files.deleteIfExists(chapterDir.resolve(name))
            }
        }
    }

    fun deleteTempChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ) {
        deleteDirectory(getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId))
    }

    private fun deleteDirectory(dir: Path) {
        requireDeletable(dir)
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

    private fun requireDeletable(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(
            normalized in registeredDirectories || downloadRoots.any { root ->
                normalized.startsWith(root) && root.relativize(normalized).nameCount >= 3
            },
        ) {
            "Refusing to delete outside a download chapter directory: $path"
        }
    }

    private fun deleteChapterEntry(entry: ChapterEntry) {
        requireDeletable(entry.path)
        if (entry.archive) {
            deleteWithRetry(entry.path) { Files.deleteIfExists(entry.path) }
        } else {
            deleteDirectory(entry.path)
        }
    }

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
    ): DownloadChapterInspection {
        val entry = findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            ?: return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        return if (entry.archive) {
            inspectArchiveAt(entry.path, expectedPageIndexes)
        } else {
            inspectChapterAt(entry.path, expectedPageIndexes)
        }
    }

    fun isChapterDownloaded(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean {
        val entry = findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId) ?: return false
        if (entry.archive) return isArchiveChapterComplete(entry.path)
        val chapterDir = entry.path
        val manifestFile = manifestPath(chapterDir)
        val manifest = readManifest(chapterDir)
        if (Files.exists(manifestFile) && manifest == null) return false
        if (manifest != null && manifest.totalPages > 0) {
            return inspectChapterAt(chapterDir, manifest.pages.map(DownloadedPageMetadata::index)).isComplete
        }
        // Older downloads may predate manifests. The saved queue migrates them during startup.
        return runCatching {
            Files.list(chapterDir).use { paths ->
                paths.anyMatch { path ->
                    Files.isRegularFile(path) &&
                        path.fileName.toString() != MANIFEST_FILE &&
                        !path.fileName.toString().endsWith(".part") &&
                        runCatching { Files.size(path) > 0L }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
    }

    fun deleteChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean {
        val candidates = chapterEntries(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        val published = candidates.filter { Files.exists(it.path) }
        // Staging archives never mark a chapter as downloaded; drop them along with the chapter.
        candidates.filter(ChapterEntry::archive).forEach { entry ->
            runCatching { deleteDirectory(entry.path.resolveSibling("${entry.path.fileName}$ARCHIVE_STAGING_SUFFIX")) }
        }
        if (published.isEmpty()) return false
        return published.all { entry ->
            runCatching {
                deleteChapterEntry(entry)
                val mangaDir = entry.path.parent
                if (Files.isDirectory(mangaDir) && Files.list(mangaDir).use { !it.findAny().isPresent }) {
                    Files.deleteIfExists(mangaDir)
                }
            }.isSuccess
        }
    }

    fun finalizeChapter(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalPages: Int,
        mutationPort: LibraryMutationPort? = null,
    ): Path {
        val tempDir = getTempChapterDir(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        if (!Files.exists(tempDir) || !Files.isDirectory(tempDir)) {
            throw IOException("Temporary download directory $tempDir does not exist")
        }

        // Every page must remain decodable before publishing an offline chapter.
        val metadata = mutableListOf<DownloadedPageMetadata>()
        for (i in 0 until totalPages) {
            val page = validPageMetadata(tempDir, i)
                ?: throw IOException("Page $i (${getPageFile(tempDir, i)}) is missing, corrupt or incomplete")
            metadata += page
        }
        val totalBytes = metadata.sumOf(DownloadedPageMetadata::sizeBytes)

        cleanPartialPages(tempDir)
        removeOrphanedSplitParts(tempDir)
        writeManifest(tempDir, metadata)

        val target = expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
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
            deleteDirectory(previousDir)
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
        deleteDirectory(previousDir)

        return targetDir
    }

    /**
     * Publishes a chapter as a single archive. The archive is staged beside its target so a failure
     * leaves the downloaded pages retryable and any previously published chapter untouched.
     */
    private fun publishArchive(tempDir: Path, targetFile: Path, register: () -> Unit): Path {
        val staging = targetFile.resolveSibling("${targetFile.fileName}$ARCHIVE_STAGING_SUFFIX")
        writeArchive(tempDir, staging)
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
                    deleteDirectory(previous)
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
        displaced.forEach { deleteDirectory(it.first) }
        deleteDirectory(tempDir)

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
            val entry = findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
                ?: expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
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
        val entry = findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            ?: expectedChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
        return mutationPort.isLocalChapterAssetRegistered(
            mangaId = mangaId,
            storagePath = entry.path.parent.toAbsolutePath().toString(),
            chapterId = chapterId,
            relativePath = entry.path.fileName.toString(),
            sizeBytes = totalBytes,
            assetKind = entry.assetKind,
        )
    }

    private fun getMangaDir(root: Path, sourceId: Long, mangaTitle: String): Path =
        root.resolve(sourceId.toString()).resolve(sanitizeFileName(mangaTitle))

    private fun inspectChapterAt(
        chapterDir: Path,
        expectedPageIndexes: List<Int>,
    ): DownloadChapterInspection {
        if (!Files.isDirectory(chapterDir) || expectedPageIndexes.isEmpty()) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val expected = expectedPageIndexes.distinct().sorted()
        if (expected.size != expectedPageIndexes.size) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val manifest = readManifest(chapterDir)?.takeIf { saved ->
            saved.version == MANIFEST_VERSION &&
                saved.totalPages == expected.size &&
                saved.pages.map(DownloadedPageMetadata::index).sorted() == expected
        }
        val manifestPages = manifest?.pages?.associateBy(DownloadedPageMetadata::index).orEmpty()
        val valid = buildMap {
            expected.forEach { index ->
                val saved = manifestPages[index]
                val current = pageMetadata(chapterDir, index, saved?.partCount)
                val unchanged = saved != null && current != null &&
                    current.sizeBytes == saved.sizeBytes &&
                    current.modifiedAtMillis == saved.modifiedAtMillis &&
                    current.partCount == saved.partCount
                if (unchanged) {
                    put(index, current)
                } else {
                    validPageMetadata(chapterDir, index, saved?.partCount)?.let { put(index, it) }
                }
            }
        }
        if (valid.size == expected.size) {
            val current = valid.values.sortedBy(DownloadedPageMetadata::index)
            if (manifest?.pages != current) runCatching { writeManifest(chapterDir, current) }
        }
        return DownloadChapterInspection(expected.size, valid)
    }

    private fun manifestPath(chapterDir: Path): Path = chapterDir.resolve(MANIFEST_FILE)

    private fun readManifest(chapterDir: Path): DownloadChapterManifest? = runCatching {
        val path = manifestPath(chapterDir)
        if (!Files.isRegularFile(path)) return@runCatching null
        manifestJson.decodeFromString<DownloadChapterManifest>(Files.readString(path))
    }.getOrNull()

    private fun writeManifest(chapterDir: Path, pages: List<DownloadedPageMetadata>) {
        val sorted = pages.sortedBy(DownloadedPageMetadata::index)
        val path = manifestPath(chapterDir)
        val partial = path.resolveSibling("${path.fileName}.part")
        Files.createDirectories(chapterDir)
        try {
            Files.writeString(
                partial,
                manifestJson.encodeToString(
                    DownloadChapterManifest(
                        version = MANIFEST_VERSION,
                        totalPages = sorted.size,
                        pages = sorted,
                    ),
                ),
            )
            try {
                Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /**
     * Writes one stored entry per file of the staging directory. Page names stay flat so the
     * archive reads like any other CBZ; a failure never leaves a half written archive behind.
     */
    private fun writeArchive(tempDir: Path, staging: Path) {
        try {
            val files = Files.list(tempDir).use { paths ->
                paths.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".part") }
                    .toList()
                    .sortedBy { it.fileName.toString() }
            }
            Files.newOutputStream(
                staging,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).let(::BufferedOutputStream).let { stream ->
                ZipOutputStream(stream, Charsets.UTF_8).use { zip ->
                    files.forEach { file ->
                        val size = Files.size(file)
                        val entry = ZipEntry(file.fileName.toString()).apply {
                            method = ZipEntry.STORED
                            this.size = size
                            compressedSize = size
                            crc = crc32(file)
                        }
                        zip.putNextEntry(entry)
                        Files.copy(file, zip)
                        zip.closeEntry()
                    }
                }
            }
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(staging) }
            throw error
        }
    }

    private fun crc32(file: Path): Long {
        val crc = CRC32()
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private fun readArchive(file: Path): ArchivePayload? = runCatching {
        ZipFile(file.toFile()).use { zip ->
            val images = mutableListOf<ArchiveImage>()
            var manifest: DownloadChapterManifest? = null
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                // Only flat image entries are pages; anything nested belongs to the source.
                if (entry.isDirectory || name.contains('/') || name.contains('\\')) continue
                when {
                    name == MANIFEST_FILE -> manifest = runCatching {
                        zip.getInputStream(entry).use { input ->
                            manifestJson.decodeFromString<DownloadChapterManifest>(
                                input.readBytes().toString(Charsets.UTF_8),
                            )
                        }
                    }.getOrNull()
                    ImageEntryPolicy.isSupportedImage(name) ->
                        images += ArchiveImage(name, entry.size, entry.time)
                }
            }
            ArchivePayload(images.sortedWith(ArchiveImage.ORDER), manifest)
        }
    }.getOrNull()

    private fun isArchiveChapterComplete(file: Path): Boolean {
        val payload = readArchive(file) ?: return false
        val manifestIndexes = payload.manifest
            ?.takeIf { it.totalPages > 0 }
            ?.pages
            ?.map(DownloadedPageMetadata::index)
            ?.filter { it >= 0 }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
        // Archives without a usable manifest still count as downloaded when they hold a page.
        return if (manifestIndexes != null) {
            inspectArchivePayload(payload, manifestIndexes).isComplete
        } else {
            payload.images.any { it.sizeBytes > 0L }
        }
    }

    private fun inspectArchiveAt(file: Path, expectedPageIndexes: List<Int>): DownloadChapterInspection {
        if (!Files.isRegularFile(file) || expectedPageIndexes.isEmpty()) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val expected = expectedPageIndexes.distinct().sorted()
        if (expected.size != expectedPageIndexes.size) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val payload = readArchive(file) ?: return DownloadChapterInspection(expected.size, emptyMap())
        return inspectArchivePayload(payload, expected)
    }

    /**
     * Pages of an archive are positional, matching the reader's natural entry ordering. Split
     * pages occupy as many consecutive entries as their recorded part count, and a page whose
     * parts are missing stops the inspection so it can never be reported as complete.
     */
    private fun inspectArchivePayload(
        payload: ArchivePayload,
        expectedPageIndexes: List<Int>,
    ): DownloadChapterInspection {
        val manifestPages = payload.manifest?.pages?.associateBy(DownloadedPageMetadata::index).orEmpty()
        val valid = mutableMapOf<Int, DownloadedPageMetadata>()
        var position = 0
        for (index in expectedPageIndexes) {
            val partCount = (manifestPages[index]?.partCount ?: 1).coerceAtLeast(1)
            if (position + partCount > payload.images.size) break
            val parts = payload.images.subList(position, position + partCount)
            if (parts.any { it.sizeBytes <= 0L }) break
            valid[index] = DownloadedPageMetadata(
                index = index,
                sizeBytes = parts.sumOf(ArchiveImage::sizeBytes),
                modifiedAtMillis = parts.maxOf(ArchiveImage::modifiedAtMillis),
                partCount = parts.size,
            )
            position += partCount
        }
        return DownloadChapterInspection(expectedPageIndexes.size, valid)
    }

    /**
     * Page order of a published archive. Downloaded pages are named `%03d` and their split parts
     * `%03d__%03d`, so a numeric stem keeps the position aligned with the chapter page index and
     * with natural reader ordering. Names that are not page numbers keep sorting last by name.
     */
    private data class ArchiveImage(val name: String, val sizeBytes: Long, val modifiedAtMillis: Long) {
        private val stem = name.substringBeforeLast('.')
        private val match = PAGE_STEM.matchEntire(stem)
        private val pageIndex: Int = match?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        private val partIndex: Int = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

        companion object {
            val ORDER: Comparator<ArchiveImage> = compareBy(
                ArchiveImage::pageIndex,
                ArchiveImage::partIndex,
                { it.name.lowercase(java.util.Locale.ROOT) },
            )

            private val PAGE_STEM = Regex("^(\\d+)(?:__(\\d+))?$")
        }
    }

    private data class ArchivePayload(
        val images: List<ArchiveImage>,
        val manifest: DownloadChapterManifest?,
    )

    private companion object {
        const val MANIFEST_VERSION = 1
        const val MANIFEST_FILE = ".mihon-download.json"
        const val DELETE_ATTEMPTS = 4
        const val DELETE_RETRY_DELAY_MILLIS = 25L
    }
}
