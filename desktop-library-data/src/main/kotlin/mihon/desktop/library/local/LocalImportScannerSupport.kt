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

internal val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("cbz", "zip", "rar", "cbr", "7z", "cb7", "tar", "cbt", "epub")

internal fun isSupportedArchive(name: String): Boolean =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT) in SUPPORTED_ARCHIVE_EXTENSIONS

internal enum class LocalScanMode {
    SOURCE,
    OWNED_IMPORT,
}

internal fun isInternalOwnershipMetadata(path: Path): Boolean {
    val name = path.fileName?.toString() ?: return false
    if (!name.startsWith(LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX)) return false
    val suffix = name.removePrefix(LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX)
    if (suffix.matches(OWNERSHIP_NONCE_PATTERN)) return true
    if (!suffix.endsWith(".tmp")) return false
    val temporaryParts = suffix.removeSuffix(".tmp").split('.', limit = 2)
    if (temporaryParts.size != 2 || !temporaryParts[0].matches(OWNERSHIP_NONCE_PATTERN)) return false
    return runCatching { java.util.UUID.fromString(temporaryParts[1]).toString() == temporaryParts[1] }
        .getOrDefault(false)
}

internal val OWNERSHIP_NONCE_PATTERN = Regex("[0-9a-f]{32}")

internal fun readAttributes(path: Path, label: String): BasicFileAttributes = try {
    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
} catch (error: IOException) {
    reject("cannot read attributes for $label: $path", error)
} catch (error: SecurityException) {
    reject("cannot read attributes for $label: $path", error)
}

internal fun <T> withDirectoryIterationRejection(message: String, block: () -> T): T = try {
    block()
} catch (error: DirectoryIteratorException) {
    throw LocalImportRejected(message, error.cause ?: error)
}

internal fun validateNoReparseAncestors(
    path: Path,
    label: String,
    dosReparseReader: LocalDosReparseReader = LocalDosReparseReader.SYSTEM,
) {
    // Pure-Java NOFOLLOW checks reject persistent or observed component replacements. They are deliberately repeated
    // before copy operations, but are not a native handle-relative guarantee: a privileged actor that swaps and
    // restores a component entirely between observations is outside the Plan 2 local-library threat model.
    val absolute = path.toAbsolutePath().normalize()
    var current = absolute.root ?: reject("$label has no filesystem root: $absolute")
    validateSafePathComponent(current, label, dosReparseReader)
    for (index in 0 until absolute.nameCount) {
        current = current.resolve(absolute.getName(index))
        validateSafePathComponent(current, label, dosReparseReader)
    }
}

internal fun validateSafePathComponent(path: Path, label: String, dosReparseReader: LocalDosReparseReader) {
    val attributes = readAttributes(path, "$label ancestor")
    if (isLinkOrReparsePoint(path, attributes, dosReparseReader)) {
        reject("$label ancestor is a link or reparse point: $path")
    }
}

internal fun isLinkOrReparsePoint(
    path: Path,
    attributes: BasicFileAttributes,
    dosReparseReader: LocalDosReparseReader = LocalDosReparseReader.SYSTEM,
): Boolean {
    if (Files.isSymbolicLink(path) || attributes.isSymbolicLink || attributes.isOther) return true
    if (attributes is DosFileAttributes && attributes.isOther) return true
    return try {
        dosReparseReader.read(path) == true
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        reject("cannot read DOS reparse attribute: $path", error)
    } catch (error: SecurityException) {
        reject("cannot read DOS reparse attribute: $path", error)
    } catch (error: UnsupportedOperationException) {
        reject("cannot read DOS reparse attribute: $path", error)
    } catch (error: IllegalArgumentException) {
        reject("cannot read DOS reparse attribute: $path", error)
    }
}

internal fun portablePath(path: Path): String =
    (0 until path.nameCount).joinToString("/") { path.getName(it).toString() }

internal val LOCAL_CHAPTER_PATH_COMPARATOR = SyncMergePolicy.UNICODE_CODE_POINT_COMPARATOR

internal fun manifestSha256(chapters: List<LocalChapterCandidate>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    chapters.forEach { chapter ->
        digest.updateLengthPrefixed(portablePath(chapter.relativePath))
        digest.updateLengthPrefixed(chapter.kind.name)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(chapter.sizeBytes).array())
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(chapter.modifiedAt).array())
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun MessageDigest.updateLengthPrefixed(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    update(bytes)
}

internal fun checkedAdd(left: Long, right: Long, label: String): Long = try {
    Math.addExact(left, right)
} catch (error: ArithmeticException) {
    reject("$label overflow", error)
}

internal fun reject(message: String, cause: Throwable? = null): Nothing = throw LocalImportRejected(message, cause)
