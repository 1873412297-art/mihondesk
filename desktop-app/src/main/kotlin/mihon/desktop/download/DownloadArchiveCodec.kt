package mihon.desktop.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.reader.source.ImageEntryPolicy
import java.io.BufferedOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal const val MANIFEST_VERSION = 1
internal const val MANIFEST_FILE = ".mihon-download.json"

/** CBZ archive writing/reading and the chapter manifest that records page metadata. */
internal object DownloadArchiveCodec {
    private val manifestJson = Json { ignoreUnknownKeys = true }

    @Serializable
    internal data class DownloadChapterManifest(
        val version: Int = 1,
        val totalPages: Int,
        val pages: List<DownloadedPageMetadata>,
    )

    fun manifestPath(chapterDir: Path): Path = chapterDir.resolve(MANIFEST_FILE)

    fun readManifest(chapterDir: Path): DownloadChapterManifest? = runCatching {
        val path = manifestPath(chapterDir)
        if (!Files.isRegularFile(path)) return@runCatching null
        manifestJson.decodeFromString<DownloadChapterManifest>(Files.readString(path))
    }.getOrNull()

    fun writeManifest(chapterDir: Path, pages: List<DownloadedPageMetadata>) {
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
                mihon.desktop.platform.moveReplacing(partial, path)
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
    fun writeArchive(tempDir: Path, staging: Path) {
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
            // Best-effort cleanup; a failure here is not actionable.
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

    fun isChapterComplete(file: Path): Boolean {
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
            inspectPayload(payload, manifestIndexes).isComplete
        } else {
            payload.images.any { it.sizeBytes > 0L }
        }
    }

    fun inspectAt(file: Path, expectedPageIndexes: List<Int>): DownloadChapterInspection {
        if (!Files.isRegularFile(file) || expectedPageIndexes.isEmpty()) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val expected = expectedPageIndexes.distinct().sorted()
        if (expected.size != expectedPageIndexes.size) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val payload = readArchive(file) ?: return DownloadChapterInspection(expected.size, emptyMap())
        return inspectPayload(payload, expected)
    }

    /**
     * Pages of an archive are positional, matching the reader's natural entry ordering. Split
     * pages occupy as many consecutive entries as their recorded part count, and a page whose
     * parts are missing stops the inspection so it can never be reported as complete.
     */
    private fun inspectPayload(
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
}
