package mihon.desktop.library.local

import mihon.sync.core.merge.SyncMergePolicy
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryIteratorException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.DosFileAttributes
import java.security.MessageDigest
import java.util.Locale

enum class LocalChapterKind {
    DIRECTORY,
    ARCHIVE,
}

data class LocalChapterCandidate(
    val name: String,
    val relativePath: Path,
    val kind: LocalChapterKind,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

data class LocalImportManifest(
    val title: String,
    val sourceRoot: Path,
    val chapters: List<LocalChapterCandidate>,
    val sha256: String,
)

data class LocalImportLimits(
    val maxChapters: Int = 100_000,
    val maxEntries: Long = 2_000_000,
    val maxBytes: Long = 1_073_741_824,
) {
    init {
        require(maxChapters > 0)
        require(maxEntries > 0)
        require(maxBytes >= 0)
    }
}

class LocalImportRejected(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

internal enum class LocalEntryFault {
    UNREADABLE,
    NONREGULAR,
}

internal fun interface LocalScannerEntryFaults {
    fun fault(path: Path): LocalEntryFault?

    companion object {
        val NONE = LocalScannerEntryFaults { null }
    }
}

internal fun interface LocalDosReparseReader {
    fun read(path: Path): Boolean?

    companion object {
        val SYSTEM = LocalDosReparseReader { path ->
            val view = Files.getFileAttributeView(
                path,
                DosFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            ) ?: return@LocalDosReparseReader null
            view.readAttributes().isOther
        }
    }
}

class LocalImportScanner private constructor(
    private val limits: LocalImportLimits,
    private val entryFaults: LocalScannerEntryFaults,
    private val dosReparseReader: LocalDosReparseReader,
) {
    constructor(limits: LocalImportLimits = LocalImportLimits()) : this(
        limits,
        LocalScannerEntryFaults.NONE,
        LocalDosReparseReader.SYSTEM,
    )

    internal constructor(
        entryFaults: LocalScannerEntryFaults,
        limits: LocalImportLimits = LocalImportLimits(),
    ) : this(limits, entryFaults, LocalDosReparseReader.SYSTEM)

    internal constructor(
        dosReparseReader: LocalDosReparseReader,
        entryFaults: LocalScannerEntryFaults = LocalScannerEntryFaults.NONE,
        limits: LocalImportLimits = LocalImportLimits(),
    ) : this(limits, entryFaults, dosReparseReader)

    fun scan(sourceDirectory: Path): LocalImportManifest = scan(sourceDirectory, LocalScanMode.SOURCE, emptySet())

    internal fun scanOwnedImportDirectory(directory: Path, verifiedMetadata: Set<Path>): LocalImportManifest =
        scan(directory, LocalScanMode.OWNED_IMPORT, verifiedMetadata)

    private fun scan(sourceDirectory: Path, mode: LocalScanMode, verifiedMetadata: Set<Path>): LocalImportManifest {
        val root = sourceDirectory.toAbsolutePath().normalize()
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            reject("source root is not a readable directory: $root")
        }
        validateNoReparseAncestors(root, "source root", dosReparseReader)
        val rootAttributes = readAttributes(root, "source root")
        if (
            isLinkOrReparsePoint(root, rootAttributes, dosReparseReader) ||
            !rootAttributes.isDirectory ||
            !Files.isReadable(root)
        ) {
            reject("source root is not a readable directory: $root")
        }
        val title = root.fileName?.toString()?.takeIf(String::isNotEmpty)
            ?: reject("source root must have a directory name: $root")
        val exclusions = verifiedMetadata.mapTo(mutableSetOf()) { metadata ->
            val normalized = metadata.toAbsolutePath().normalize()
            if (mode != LocalScanMode.OWNED_IMPORT || normalized.parent != root ||
                !isInternalOwnershipMetadata(normalized)
            ) {
                reject("invalid internal ownership metadata exclusion: $metadata")
            }
            normalized
        }
        val state = ScanState(root, limits, dosReparseReader)
        val chapters = try {
            withDirectoryIterationRejection("cannot enumerate source root: $root") {
                Files.newDirectoryStream(root).use { children ->
                    children.mapNotNull { child ->
                        if (child.toAbsolutePath().normalize() in exclusions) {
                            null
                        } else {
                            scanTopLevel(child, state)
                        }
                    }
                }
            }
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            reject("cannot enumerate source root: $root", error)
        }
        if (chapters.isEmpty()) reject("local manga contains no chapters: $root")
        val sorted = chapters.sortedWith(compareBy(LOCAL_CHAPTER_PATH_COMPARATOR) { portablePath(it.relativePath) })
        validateNoReparseAncestors(root, "source root", dosReparseReader)
        return LocalImportManifest(title, root, sorted, manifestSha256(sorted))
    }

    internal fun validateCandidatePaths(root: Path, candidates: List<Path>) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val seen = mutableSetOf<String>()
        candidates.forEach { relative ->
            if (relative.isAbsolute) reject("candidate path must not be absolute: $relative")
            val resolved = normalizedRoot.resolve(relative).normalize()
            if (!resolved.startsWith(normalizedRoot)) reject("candidate path escapes source root: $relative")
            val normalizedRelative = normalizedRoot.relativize(resolved)
            if (normalizedRelative.nameCount == 0) reject("candidate path resolves to source root: $relative")
            val key = portablePath(normalizedRelative).lowercase(Locale.ROOT)
            if (!seen.add(key)) reject("case-folded duplicate path: $relative")
        }
    }

    private fun scanTopLevel(child: Path, state: ScanState): LocalChapterCandidate? {
        when (entryFaults.fault(child)) {
            LocalEntryFault.UNREADABLE -> reject("entry is unreadable: ${child.fileName}")
            LocalEntryFault.NONREGULAR -> reject(
                "top-level entry is not a regular file or directory: ${child.fileName}",
            )
            null -> Unit
        }
        val preliminaryAttributes = readAttributes(child, "top-level entry")
        if (isLinkOrReparsePoint(child, preliminaryAttributes, dosReparseReader)) {
            reject("link or reparse point is not allowed: ${child.fileName}")
        }
        if (!Files.isReadable(child)) reject("entry is unreadable: ${child.fileName}")
        val entry = state.inspect(child)
        val relative = entry.relativePath
        return when {
            entry.attributes.isDirectory -> {
                val directorySize = scanChapterDirectory(child, state)
                LocalChapterCandidate(
                    name = child.fileName.toString(),
                    relativePath = relative,
                    kind = LocalChapterKind.DIRECTORY,
                    sizeBytes = directorySize,
                    modifiedAt = entry.attributes.lastModifiedTime().toMillis(),
                )
            }
            entry.attributes.isRegularFile && isSupportedArchive(child.fileName.toString()) -> {
                state.addBytes(entry.attributes.size(), child)
                LocalChapterCandidate(
                    name = child.fileName.toString(),
                    relativePath = relative,
                    kind = LocalChapterKind.ARCHIVE,
                    sizeBytes = entry.attributes.size(),
                    modifiedAt = entry.attributes.lastModifiedTime().toMillis(),
                )
            }
            entry.attributes.isRegularFile -> reject("unsupported top-level file: $relative")
            else -> reject("top-level entry is not a regular file or directory: $relative")
        }
    }

    private fun scanChapterDirectory(directory: Path, state: ScanState): Long {
        var size = 0L
        try {
            Files.walkFileTree(
                directory,
                setOf(),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (dir != directory) state.inspect(dir)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        val inspected = state.inspect(file)
                        if (!inspected.attributes.isRegularFile) {
                            reject("chapter asset is not a regular file: ${inspected.relativePath}")
                        }
                        size = checkedAdd(size, inspected.attributes.size(), "chapter byte size")
                        state.addBytes(inspected.attributes.size(), file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, error: IOException): FileVisitResult =
                        reject("chapter asset is unreadable: $file", error)
                },
            )
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            reject("cannot scan chapter directory: $directory", error)
        }
        return size
    }
}

private data class InspectedEntry(
    val relativePath: Path,
    val attributes: BasicFileAttributes,
)

private class ScanState(
    private val root: Path,
    private val limits: LocalImportLimits,
    private val dosReparseReader: LocalDosReparseReader,
) {
    private val caseFoldedPaths = mutableSetOf<String>()
    private var entries = 0L
    private var bytes = 0L
    private var chapters = 0

    fun inspect(candidate: Path): InspectedEntry {
        val absolute = candidate.toAbsolutePath().normalize()
        if (!absolute.startsWith(root)) reject("candidate path escapes source root: $candidate")
        val relative = root.relativize(absolute)
        if (relative.nameCount == 0) reject("candidate path resolves to source root: $candidate")
        val key = portablePath(relative).lowercase(Locale.ROOT)
        if (!caseFoldedPaths.add(key)) reject("case-folded duplicate path: $relative")
        entries++
        if (entries > limits.maxEntries) reject("local import entry limit exceeded: ${limits.maxEntries}")
        val attributes = readAttributes(absolute, relative.toString())
        if (isLinkOrReparsePoint(absolute, attributes, dosReparseReader)) {
            reject("link or reparse point is not allowed: $relative")
        }
        if (!Files.isReadable(absolute)) reject("entry is unreadable: $relative")
        if (relative.nameCount == 1) {
            chapters++
            if (chapters > limits.maxChapters) reject("local import chapter limit exceeded: ${limits.maxChapters}")
        }
        return InspectedEntry(relative, attributes)
    }

    fun addBytes(amount: Long, path: Path) {
        if (amount < 0) reject("negative entry size: $path")
        bytes = checkedAdd(bytes, amount, "local import byte size")
        if (bytes > limits.maxBytes) reject("local import byte limit exceeded: ${limits.maxBytes}")
    }
}
