package mihon.desktop.library.local

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal fun createClaim(path: Path, id: String, nonce: String, createdAt: Long) {
    if (createdAt < 0) rejectStaging("claim timestamp must not be negative")
    if (!isValidOwnershipNonce(nonce)) rejectStaging("ownership nonce is invalid")
    var created = false
    try {
        // CREATE_NEW + SYNC establishes claim ordering for process-crash recovery on a running filesystem. Pure Java
        // has no portable parent-directory flush, so this is deliberately not a sudden-power-loss durability claim.
        Files.writeString(
            path,
            claimContent(id, nonce, createdAt),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        created = true
        restrictPrivatePath(path, FILE_OWNER_PERMISSIONS, isDirectory = false)
    } catch (error: FileAlreadyExistsException) {
        rejectStaging("local import claim already exists: $path", error)
    } catch (error: Throwable) {
        if (created) {
            runCatching { deleteValidClaim(checkNotNull(path.parent), path, id, nonce) }
                .onFailure(error::addSuppressed)
        }
        if (error is LocalImportRejected) throw error
        if (error is IOException) rejectStaging("cannot create local import claim: $path", error)
        throw error
    }
    val claim = readValidClaim(path)
    if (claim?.id != id || claim.nonce != nonce) {
        runCatching { deleteValidClaim(checkNotNull(path.parent), path, id, nonce) }
        rejectStaging("local import claim validation failed: $path")
    }
}

internal fun createOwnershipMarkerAtomically(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
    stagingRoot: Path,
    claimsRoot: Path,
    mangaRoot: Path,
    fileFaults: LocalFileFaults,
) {
    val id = identity.id
    val nonce = identity.nonce
    val marker = ownershipMarkerPath(directory, nonce)
    val temporary = directory.resolve("${marker.fileName}.${UUID.randomUUID()}.tmp")
    var simulatedCrash = false
    var temporaryIdentity: OwnedMarkerIdentity? = null
    try {
        Files.writeString(
            temporary,
            ownershipMarkerContent(id, nonce),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            StandardOpenOption.SYNC,
        )
        restrictPrivatePath(temporary, FILE_OWNER_PERMISSIONS, isDirectory = false)
        temporaryIdentity = retainOwnedMarkerIdentity(temporary, id, nonce)
        try {
            fileFaults.duringMarkerPublication(temporary, marker)
        } catch (crash: LocalImportCrashSimulation) {
            simulatedCrash = true
            throw crash
        }
        validateClaimAndNamespaces(stagingRoot, claimsRoot, mangaRoot, claimPath, id, nonce)
        requireCreatedDirectoryMatches(directory, identity, claimPath, requireEmpty = false)
        requireImmediateChild(directory, temporary, "temporary ownership marker")
        validateNoReparseAncestors(temporary, "temporary ownership marker")
        if (!hasValidOwnershipMarkerFile(temporary, id, nonce)) {
            rejectStaging("temporary local import ownership marker validation failed")
        }
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("local import ownership marker destination already exists")
        }
        Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: AtomicMoveNotSupportedException) {
        rejectStaging("atomic ownership marker creation is not supported", error)
    } catch (error: IOException) {
        rejectStaging("cannot create local import ownership marker", error)
    } finally {
        if (!simulatedCrash) {
            temporaryIdentity?.let { identity ->
                runCatching { deleteOwnedMarkerFile(temporary, identity) }
            }
        }
    }
    validatePrivatePath(marker, FILE_OWNER_PERMISSIONS, isDirectory = false)
    if (!hasValidOwnershipMarker(directory, id, nonce)) {
        rejectStaging("local import ownership marker validation failed")
    }
}

internal fun retainOwnedMarkerIdentity(marker: Path, id: String, nonce: String): OwnedMarkerIdentity {
    if (!hasValidOwnershipMarkerFile(marker, id, nonce)) {
        rejectStaging("temporary local import ownership marker validation failed")
    }
    val attributes = readAttributes(marker, "temporary ownership marker")
    return OwnedMarkerIdentity(id, nonce, attributes.fileKey())
}

internal fun deleteOwnedMarkerFile(marker: Path, identity: OwnedMarkerIdentity): Boolean {
    if (Files.notExists(marker, LinkOption.NOFOLLOW_LINKS)) return true
    if (!hasValidOwnershipMarkerFile(marker, identity.id, identity.nonce)) return false
    val attributes = readAttributes(marker, "temporary ownership marker cleanup")
    if (identity.fileKey != null && attributes.fileKey() != identity.fileKey) return false
    return try {
        Files.delete(marker)
        true
    } catch (_: IOException) {
        false
    }
}

internal fun retainCreatedDirectoryIdentity(
    directory: Path,
    id: String,
    nonce: String,
    claimPath: Path,
): OwnedDirectoryIdentity {
    if (directory.fileName?.toString() != id) {
        rejectStaging("created local import directory identifier is invalid: $directory")
    }
    validateNoReparseAncestors(directory, "created import directory")
    val attributes = readAttributes(directory, "created import directory")
    if (!attributes.isDirectory || isLinkOrReparsePoint(directory, attributes)) {
        rejectStaging("created local import directory is not safe: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    val claim = readValidClaim(claimPath)
    if (claim?.id != id || claim.nonce != nonce) {
        rejectStaging("created local import directory claim is invalid: $directory")
    }
    return OwnedDirectoryIdentity(id, nonce, attributes.fileKey())
}

internal fun requireCreatedDirectoryMatches(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
    requireEmpty: Boolean,
): BasicFileAttributes {
    if (directory.fileName?.toString() != identity.id) {
        rejectStaging("created local import directory identifier changed: $directory")
    }
    validateNoReparseAncestors(directory, "created import directory")
    val attributes = readAttributes(directory, "created import directory")
    val claim = readValidClaim(claimPath)
    if (
        !attributes.isDirectory ||
        isLinkOrReparsePoint(directory, attributes) ||
        claim?.id != identity.id ||
        claim.nonce != identity.nonce ||
        (identity.fileKey != null && attributes.fileKey() != identity.fileKey)
    ) {
        rejectStaging("created local import directory identity changed: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    if (requireEmpty && !isEmptyClaimedDirectory(directory)) {
        rejectStaging("created local import directory is not empty: $directory")
    }
    return attributes
}

internal fun requireOwnedDirectoryMatches(
    directory: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): BasicFileAttributes {
    val attributes = requireOwnedImportDirectory(directory, identity.id, identity.nonce, claimPath)
    if (identity.fileKey != null && attributes.fileKey() != identity.fileKey) {
        rejectStaging("local import directory identity changed: $directory")
    }
    return attributes
}

internal fun requireOwnedImportDirectory(
    directory: Path,
    id: String,
    nonce: String,
    claimPath: Path,
): BasicFileAttributes {
    validateNoReparseAncestors(directory, "owned import directory")
    val attributes = readAttributes(directory, "owned import directory")
    if (
        !attributes.isDirectory ||
        isLinkOrReparsePoint(directory, attributes) ||
        !hasValidOwnershipMarker(directory, id, nonce) ||
        readValidClaim(claimPath)?.let { it.id == id && it.nonce == nonce } != true
    ) {
        rejectStaging("local import ownership validation failed: $directory")
    }
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
    return attributes
}

internal fun deleteOwnedDirectory(
    parent: Path,
    target: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): Boolean {
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(parent, normalizedTarget, "owned cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return true
    val matches = runCatching {
        requireOwnedDirectoryMatches(normalizedTarget, identity, claimPath)
        true
    }.getOrDefault(false)
    if (!matches) return false
    deleteValidated(parent, normalizedTarget)
    return true
}

internal fun deleteEmptyClaimedDirectory(
    parent: Path,
    target: Path,
    identity: OwnedDirectoryIdentity,
    claimPath: Path,
): Boolean {
    val normalizedTarget = target.toAbsolutePath().normalize()
    requireImmediateChild(parent, normalizedTarget, "empty claimed cleanup target")
    if (Files.notExists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) return true
    val matches = runCatching {
        requireCreatedDirectoryMatches(normalizedTarget, identity, claimPath, requireEmpty = true)
        true
    }.getOrDefault(false)
    if (!matches) return false
    return try {
        Files.delete(normalizedTarget)
        true
    } catch (_: IOException) {
        false
    }
}

internal fun hasValidOwnershipMarker(directory: Path, id: String, nonce: String): Boolean {
    if (!isValidImportId(id) || directory.fileName?.toString() != id) return false
    val marker = ownershipMarkerPath(directory, nonce)
    return hasValidOwnershipMarkerFile(marker, id, nonce)
}

internal fun hasValidOwnershipMarkerFile(marker: Path, id: String, nonce: String): Boolean {
    if (!isValidImportId(id) || !isValidOwnershipNonce(nonce)) return false
    val markerAttributes = runCatching { readAttributes(marker, "ownership marker") }.getOrNull() ?: return false
    if (
        !markerAttributes.isRegularFile ||
        isLinkOrReparsePoint(marker, markerAttributes) ||
        markerAttributes.size() > MAX_OWNERSHIP_MARKER_BYTES
    ) {
        return false
    }
    if (runCatching { validatePrivatePath(marker, FILE_OWNER_PERMISSIONS, isDirectory = false) }.isFailure) {
        return false
    }
    return runCatching { Files.readString(marker, StandardCharsets.UTF_8) == ownershipMarkerContent(id, nonce) }
        .getOrDefault(false)
}
