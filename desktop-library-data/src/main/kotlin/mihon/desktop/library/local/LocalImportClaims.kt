package mihon.desktop.library.local

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.UUID

internal fun readValidClaims(claimsRoot: Path): Map<String, ValidClaim> = try {
    withDirectoryIterationRejection("failed to read local import claims: $claimsRoot") {
        Files.newDirectoryStream(claimsRoot).use { children ->
            children.mapNotNull(::readValidClaim).associateBy(ValidClaim::id)
        }
    }
} catch (error: IOException) {
    rejectStaging("failed to read local import claims: $claimsRoot", error)
}

internal fun readValidClaim(path: Path): ValidClaim? {
    val fileName = path.fileName?.toString() ?: return null
    if (!fileName.endsWith(CLAIM_SUFFIX)) return null
    val id = fileName.removeSuffix(CLAIM_SUFFIX)
    if (!isValidImportId(id)) return null
    val attributes = runCatching { readAttributes(path, "local import claim") }.getOrNull() ?: return null
    if (
        !attributes.isRegularFile ||
        isLinkOrReparsePoint(path, attributes) ||
        attributes.size() > MAX_CLAIM_BYTES
    ) {
        return null
    }
    if (runCatching { validatePrivatePath(path, FILE_OWNER_PERMISSIONS, isDirectory = false) }.isFailure) return null
    val content = runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull() ?: return null
    val lines = content.split('\n')
    if (lines.size != 5 || lines[0] != CLAIM_FORMAT || lines[4].isNotEmpty()) return null
    if (lines[1] != "id=$id") return null
    val nonce = lines[2].removePrefix("nonce=")
    if (lines[2] != "nonce=$nonce" || !isValidOwnershipNonce(nonce)) return null
    val timestampText = lines[3].removePrefix("createdAt=")
    if (lines[3] != "createdAt=$timestampText") return null
    val createdAt = timestampText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return ValidClaim(id, nonce, createdAt, path.toAbsolutePath().normalize())
}

internal fun deleteValidClaim(claimsRoot: Path, claimPath: Path, id: String, nonce: String) {
    val normalized = claimPath.toAbsolutePath().normalize()
    requireImmediateChild(claimsRoot, normalized, "claim cleanup target")
    if (Files.notExists(normalized, LinkOption.NOFOLLOW_LINKS)) return
    val valid = readValidClaim(normalized) ?: return
    if (valid.id != id || valid.nonce != nonce) return
    try {
        Files.delete(normalized)
    } catch (error: IOException) {
        rejectStaging("failed to clean local import claim: $normalized", error)
    }
}

internal fun isStaleClaim(createdAt: Long, now: Long): Boolean =
    now >= createdAt && now - createdAt >= CLAIM_STALE_MILLIS

internal fun isSafeImportDirectory(
    directory: Path,
    claim: ValidClaim,
    allowTemporaryMarker: Boolean,
): Boolean {
    if (directory.fileName?.toString() != claim.id) return false
    val attributes = runCatching { readAttributes(directory, "claimed import directory") }.getOrNull() ?: return false
    if (!attributes.isDirectory || isLinkOrReparsePoint(directory, attributes)) return false
    if (runCatching { validateNoReparseAncestors(directory, "claimed import directory") }.isFailure) return false
    if (runCatching { validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true) }.isFailure) {
        return false
    }
    if (hasValidOwnershipMarker(directory, claim.id, claim.nonce)) return true
    if (!allowTemporaryMarker) return false
    if (hasValidTemporaryOwnershipMarker(directory, claim)) return true
    return isEmptyClaimedDirectory(directory)
}

internal fun isEmptyClaimedDirectory(directory: Path): Boolean = try {
    withDirectoryIterationRejection("failed to enumerate claimed import directory: $directory") {
        Files.newDirectoryStream(directory).use { entries -> !entries.iterator().hasNext() }
    }
} catch (_: IOException) {
    false
}

internal fun hasValidTemporaryOwnershipMarker(directory: Path, claim: ValidClaim): Boolean = try {
    val markerName = ownershipMarkerPath(directory, claim.nonce).fileName.toString()
    withDirectoryIterationRejection("failed to enumerate temporary ownership markers: $directory") {
        Files.newDirectoryStream(directory, "$markerName.*.tmp").use { candidates ->
            val matching = candidates.filter { marker ->
                hasValidOwnershipMarkerFile(marker, claim.id, claim.nonce)
            }
            matching.size == 1
        }
    }
} catch (_: IOException) {
    false
}

internal fun ownershipMarkerContent(id: String, nonce: String): String =
    "$OWNERSHIP_MARKER_FORMAT\nid=$id\nnonce=$nonce\n"

internal fun claimContent(id: String, nonce: String, createdAt: Long): String =
    "$CLAIM_FORMAT\nid=$id\nnonce=$nonce\ncreatedAt=$createdAt\n"

internal fun ownershipMarkerPath(directory: Path, nonce: String): Path {
    if (!isValidOwnershipNonce(nonce)) rejectStaging("ownership marker nonce is invalid")
    return directory.resolve("$LOCAL_IMPORT_OWNERSHIP_MARKER_PREFIX$nonce")
}

internal fun newOwnershipNonce(): String {
    val bytes = ByteArray(OWNERSHIP_NONCE_BYTES)
    OWNERSHIP_RANDOM.nextBytes(bytes)
    return bytes.joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
}

internal fun isValidOwnershipNonce(nonce: String): Boolean =
    nonce.length == OWNERSHIP_NONCE_BYTES * 2 && nonce.all { it in '0'..'9' || it in 'a'..'f' }

internal fun isValidImportId(id: String): Boolean = runCatching {
    UUID.fromString(id).toString() == id
}.getOrDefault(false)

internal fun resolveManifestEntry(root: Path, relative: Path): Path {
    if (relative.isAbsolute) rejectStaging("manifest path must not be absolute: $relative")
    val resolved = root.resolve(relative).normalize()
    if (!resolved.startsWith(root.normalize())) rejectStaging("manifest path escapes root: $relative")
    return resolved
}

internal fun requireUnchangedManifest(expected: LocalImportManifest, actual: LocalImportManifest, message: String) {
    if (
        expected.title != actual.title ||
        expected.sourceRoot != actual.sourceRoot ||
        expected.chapters != actual.chapters ||
        expected.sha256 != actual.sha256
    ) {
        rejectStaging(message)
    }
}

internal fun sameIdentityAndMetadata(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.isDirectory == after.isDirectory &&
        before.isRegularFile == after.isRegularFile &&
        before.isSymbolicLink == after.isSymbolicLink &&
        before.isOther == after.isOther &&
        before.size() == after.size() &&
        before.lastModifiedTime() == after.lastModifiedTime() &&
        (before.fileKey() == null || after.fileKey() == null || before.fileKey() == after.fileKey())

internal fun samePathIdentity(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.isDirectory == after.isDirectory &&
        before.isRegularFile == after.isRegularFile &&
        before.isSymbolicLink == after.isSymbolicLink &&
        before.isOther == after.isOther &&
        (before.fileKey() == null || before.fileKey() == after.fileKey())

internal fun observePathPresence(path: Path): PathPresence = try {
    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    PathPresence.PRESENT
} catch (_: NoSuchFileException) {
    PathPresence.ABSENT
} catch (_: IOException) {
    PathPresence.UNKNOWN
} catch (_: SecurityException) {
    PathPresence.UNKNOWN
}

internal fun requireImmediateChild(parent: Path, child: Path, label: String) {
    val normalizedParent = parent.toAbsolutePath().normalize()
    val normalizedChild = child.toAbsolutePath().normalize()
    if (normalizedChild.parent != normalizedParent) {
        rejectStaging("$label is not directly below $normalizedParent: $child")
    }
}

internal fun deleteValidated(parent: Path, target: Path) {
    val normalizedParent = parent.toAbsolutePath().normalize()
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(normalizedParent, normalizedTarget, "cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return
    try {
        Files.walkFileTree(
            normalizedTarget,
            setOf(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw error
                }
            },
        )
    } catch (error: IOException) {
        rejectStaging("failed to clean local import path: $normalizedTarget", error)
    }
}

internal fun rejectStaging(message: String, cause: Throwable? = null): Nothing = throw LocalImportRejected(
    message,
    cause,
)
