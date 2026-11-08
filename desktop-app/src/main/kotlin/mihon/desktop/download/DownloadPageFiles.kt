package mihon.desktop.download

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val SPLIT_PART_SUFFIX = ".jpg"
private val SPLIT_PART_NAME = Regex("^(\\d{3,})__(\\d{3,})\\.jpg$")

/** Page files of a chapter: naming, split parts, validation and partial-page cleanup. */
internal object DownloadPageFiles {

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
        // A whole page wins over split parts: removeOrphanedSplitParts drops such parts before
        // validation, so treating them as authoritative here would publish missing files.
        val whole = getPageFile(chapterDir, pageIndex)
        if (Files.isRegularFile(whole)) return listOf(whole)
        if (expectedPartCount != null && expectedPartCount > 1) {
            val parts = (0 until expectedPartCount).map { getSplitPartFile(chapterDir, pageIndex, it) }
            return if (parts.all(Files::isRegularFile)) parts else emptyList()
        }
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
                mihon.desktop.platform.moveReplacing(partial, file)
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
    internal fun removeOrphanedSplitParts(chapterDir: Path) {
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

    internal fun splitPartFileName(pageIndex: Int, partIndex: Int): String =
        String.format("%03d__%03d.jpg", pageIndex + 1, partIndex + 1)

    internal fun inspectChapterAt(
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
        val manifest = DownloadArchiveCodec.readManifest(chapterDir)?.takeIf { saved ->
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
            if (manifest?.pages != current) runCatching { DownloadArchiveCodec.writeManifest(chapterDir, current) }
        }
        return DownloadChapterInspection(expected.size, valid)
    }
}
