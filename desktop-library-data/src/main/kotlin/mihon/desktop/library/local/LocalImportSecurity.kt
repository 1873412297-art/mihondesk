package mihon.desktop.library.local

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.util.Locale

internal fun validateCommittedLocalMangaSecurity(localLibraryRoot: Path, finalPath: Path): Path {
    val root = localLibraryRoot.toAbsolutePath().normalize()
    val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
    val mangaRoot = root.resolve(MANGA_DIRECTORY)
    val normalizedFinal = finalPath.toAbsolutePath().normalize()
    requireImmediateChild(mangaRoot, normalizedFinal, "committed local manga path")
    validatePrivateNamespace(claimsRoot, "claim namespace")
    validatePrivateNamespace(mangaRoot, "manga namespace")
    val id = normalizedFinal.fileName?.toString() ?: rejectStaging("committed local manga has no identifier")
    if (!isValidImportId(id)) rejectStaging("committed local manga identifier is invalid")
    val claimPath = claimsRoot.resolve("$id$CLAIM_SUFFIX")
    validateNoReparseAncestors(claimPath, "local import claim")
    val claim = readValidClaim(claimPath) ?: rejectStaging("committed local manga claim is invalid")
    requireOwnedImportDirectory(normalizedFinal, id, claim.nonce, claimPath)
    validatePrivateTree(normalizedFinal)
    return ownershipMarkerPath(normalizedFinal, claim.nonce)
}

internal fun safeEnsureDirectory(directory: Path) {
    val absolute = directory.toAbsolutePath().normalize()
    val parent = absolute.parent
    if (Files.notExists(absolute, LinkOption.NOFOLLOW_LINKS)) {
        if (parent != null) safeEnsureDirectory(parent)
        try {
            Files.createDirectory(absolute)
        } catch (error: FileAlreadyExistsException) {
            // A competing creator still has to pass the no-follow validation below.
        } catch (error: IOException) {
            rejectStaging("cannot create local import directory: $absolute", error)
        }
    }
    val attributes = readAttributes(absolute, "local import directory")
    if (!attributes.isDirectory || isLinkOrReparsePoint(absolute, attributes)) {
        rejectStaging("local import directory is not a safe directory: $absolute")
    }
    validateNoReparseAncestors(absolute, "local import directory")
}

internal fun safeEnsurePrivateDirectory(directory: Path) {
    safeEnsureDirectory(directory)
    restrictPrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
}

internal fun validatePrivateNamespace(directory: Path, label: String) {
    validateNoReparseAncestors(directory, label)
    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
}

internal fun validateClaimAndNamespaces(
    stagingRoot: Path,
    claimsRoot: Path,
    mangaRoot: Path,
    claimPath: Path,
    id: String,
    nonce: String,
) {
    validatePrivateNamespace(stagingRoot, "staging namespace")
    validatePrivateNamespace(claimsRoot, "claim namespace")
    validatePrivateNamespace(mangaRoot, "manga namespace")
    requireImmediateChild(claimsRoot, claimPath, "local import claim")
    validateNoReparseAncestors(claimPath, "local import claim")
    val claim = readValidClaim(claimPath)
    if (claim?.id != id || claim.nonce != nonce) {
        rejectStaging("local import claim validation failed: $claimPath")
    }
}

internal fun validatePrivateTree(root: Path) {
    try {
        Files.walkFileTree(
            root,
            setOf(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    validatePrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    validatePrivatePath(file, FILE_OWNER_PERMISSIONS, isDirectory = false)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw error
                }
            },
        )
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private local import tree could not be validated: $root", error)
    }
}

internal fun createPrivateDirectory(directory: Path) {
    val parent = checkNotNull(directory.parent)
    val posixView = Files.getFileAttributeView(parent, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    var created = false
    try {
        if (posixView == null) {
            Files.createDirectory(directory)
        } else {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_OWNER_PERMISSIONS))
        }
        created = true
        restrictPrivatePath(directory, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        validateNoReparseAncestors(directory, "private staging directory")
    } catch (error: Throwable) {
        if (created) runCatching { Files.deleteIfExists(directory) }.onFailure(error::addSuppressed)
        throw error
    }
}

internal fun restrictPrivatePath(path: Path, permissions: Set<PosixFilePermission>, isDirectory: Boolean) {
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
        try {
            posix.setPermissions(permissions)
        } catch (error: IOException) {
            rejectStaging("private local import permissions could not be applied: $path", error)
        }
        validatePrivatePath(path, permissions, isDirectory)
        return
    }
    if (!isWindowsHost()) return
    val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        ?: rejectStaging("Windows ACL view is unavailable for private local import path: $path")
    try {
        val owner = acl.owner
        val system = lookupWindowsPrincipal(path, "S-1-5-18", "NT AUTHORITY\\SYSTEM", "SYSTEM")
        val flags = if (isDirectory) {
            setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
        } else {
            emptySet()
        }
        val allPermissions = AclEntryPermission.entries.toSet()
        acl.acl = listOf(
            allowAcl(owner, allPermissions, flags),
            allowAcl(system, allPermissions, flags),
        )
        validateRestrictedWindowsAcl(path, acl, owner, system, isDirectory)
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    } catch (error: SecurityException) {
        rejectStaging("private Windows ACL could not be applied: $path", error)
    }
}

internal fun validatePrivatePath(path: Path, permissions: Set<PosixFilePermission>, isDirectory: Boolean) {
    val attributes = readAttributes(path, "private local import path")
    if (
        isLinkOrReparsePoint(path, attributes) ||
        (isDirectory && !attributes.isDirectory) ||
        (!isDirectory && !attributes.isRegularFile)
    ) {
        rejectStaging("private local import path is not a safe ${if (isDirectory) "directory" else "file"}: $path")
    }
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
        try {
            if (posix.readAttributes().permissions() != permissions) {
                rejectStaging("private local import permissions could not be validated: $path")
            }
        } catch (error: IOException) {
            rejectStaging("private local import permissions could not be validated: $path", error)
        }
        return
    }
    if (!isWindowsHost()) return
    val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        ?: rejectStaging("Windows ACL view is unavailable for private local import path: $path")
    try {
        val owner = acl.owner
        val system = lookupWindowsPrincipal(path, "S-1-5-18", "NT AUTHORITY\\SYSTEM", "SYSTEM")
        validateRestrictedWindowsAcl(path, acl, owner, system, isDirectory)
    } catch (error: LocalImportRejected) {
        throw error
    } catch (error: IOException) {
        rejectStaging("private Windows ACL could not be validated: $path", error)
    } catch (error: SecurityException) {
        rejectStaging("private Windows ACL could not be validated: $path", error)
    }
}

internal fun validateRestrictedWindowsAcl(
    path: Path,
    view: AclFileAttributeView,
    owner: UserPrincipal,
    system: UserPrincipal,
    isDirectory: Boolean,
) {
    val ownerName = owner.name.lowercase(Locale.ROOT)
    val systemName = system.name.lowercase(Locale.ROOT)
    val entries = view.acl
    val expectedFlags = if (isDirectory) {
        setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
    } else {
        emptySet()
    }
    val expectedPermissions = AclEntryPermission.entries.toSet()
    if (
        entries.size != 2 ||
        entries.count { it.principal().name.lowercase(Locale.ROOT) == ownerName } != 1 ||
        entries.count { it.principal().name.lowercase(Locale.ROOT) == systemName } != 1 ||
        entries.any {
            it.type() != AclEntryType.ALLOW ||
                it.permissions() != expectedPermissions ||
                it.flags() != expectedFlags
        }
    ) {
        rejectStaging("private Windows ACL is not the exact owner and SYSTEM allow-list: $path")
    }
}

internal fun allowAcl(
    principal: UserPrincipal,
    permissions: Set<AclEntryPermission>,
    flags: Set<AclEntryFlag>,
): AclEntry = AclEntry.newBuilder()
    .setType(AclEntryType.ALLOW)
    .setPrincipal(principal)
    .setPermissions(permissions)
    .setFlags(flags)
    .build()

internal fun lookupWindowsPrincipal(path: Path, vararg names: String): UserPrincipal =
    names.firstNotNullOfOrNull { name ->
        runCatching { path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(name) }.getOrNull()
    } ?: rejectStaging("required Windows SYSTEM principal is unavailable")

internal fun isWindowsHost(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
