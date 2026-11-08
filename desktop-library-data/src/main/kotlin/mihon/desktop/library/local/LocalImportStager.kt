package mihon.desktop.library.local

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal class LocalImportStager private constructor(
    private val scanner: LocalImportScanner,
    private val idFactory: () -> String,
    private val checkpoint: LocalStagingCheckpoint,
    private val fileFaults: LocalFileFaults,
    private val clock: () -> Long,
) {
    internal constructor(
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, LocalFileFaults.NONE, System::currentTimeMillis)

    internal constructor(
        fileFaults: LocalFileFaults,
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, fileFaults, System::currentTimeMillis)

    internal constructor(
        clock: () -> Long,
        fileFaults: LocalFileFaults = LocalFileFaults.NONE,
        scanner: LocalImportScanner = LocalImportScanner(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
        checkpoint: LocalStagingCheckpoint = LocalStagingCheckpoint.NONE,
    ) : this(scanner, idFactory, checkpoint, fileFaults, clock)

    fun stage(manifest: LocalImportManifest, localLibraryRoot: Path): StagedLocalManga {
        validateManifestShape(manifest)
        validateNoReparseAncestors(manifest.sourceRoot, "source root")
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed after scan")
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsurePrivateDirectory(stagingRoot)
        safeEnsurePrivateDirectory(claimsRoot)
        safeEnsurePrivateDirectory(mangaRoot)
        val id = idFactory()
        if (!isValidImportId(id)) rejectStaging("invalid staging identifier")
        val ownershipNonce = newOwnershipNonce()
        val claimPath = claimsRoot.resolve("$id$CLAIM_SUFFIX").normalize()
        val stagingPath = stagingRoot.resolve(id).normalize()
        val finalPath = mangaRoot.resolve(id).normalize()
        requireImmediateChild(claimsRoot, claimPath, "claim path")
        requireImmediateChild(stagingRoot, stagingPath, "staging path")
        requireImmediateChild(mangaRoot, finalPath, "promotion path")
        var claimCreated = false
        var stagingCreated = false
        var createdIdentity: OwnedDirectoryIdentity? = null
        try {
            createClaim(claimPath, id, ownershipNonce, clock())
            claimCreated = true
            fileFaults.afterClaimCreated(claimPath)
            validateClaimAndNamespaces(
                stagingRoot,
                claimsRoot,
                mangaRoot,
                claimPath,
                id,
                ownershipNonce,
            )
            try {
                createPrivateDirectory(stagingPath)
                stagingCreated = true
            } catch (error: FileAlreadyExistsException) {
                rejectStaging("staging target already exists: $stagingPath", error)
            }
            val identity = retainCreatedDirectoryIdentity(stagingPath, id, ownershipNonce, claimPath)
            createdIdentity = identity
            fileFaults.afterStagingDirectoryCreated(stagingPath)
            validateClaimAndNamespaces(
                stagingRoot,
                claimsRoot,
                mangaRoot,
                claimPath,
                id,
                ownershipNonce,
            )
            requireCreatedDirectoryMatches(stagingPath, identity, claimPath, requireEmpty = true)
            createOwnershipMarkerAtomically(
                stagingPath,
                identity,
                claimPath,
                stagingRoot,
                claimsRoot,
                mangaRoot,
                fileFaults,
            )
            requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
            val copyContext = StagingCopyContext(
                stagingPath,
                identity,
                claimPath,
                stagingRoot,
                claimsRoot,
                mangaRoot,
            )
            manifest.chapters.forEach { chapter ->
                val source = resolveManifestEntry(manifest.sourceRoot, chapter.relativePath)
                val target = resolveManifestEntry(stagingPath, chapter.relativePath)
                copyEntryNoFollow(source, target, copyContext)
            }
            verifyStagedState(manifest, stagingPath, identity, claimPath, stagingRoot, claimsRoot, mangaRoot)
            checkpoint.afterStagingCopy(stagingPath)
            verifyStagedState(manifest, stagingPath, identity, claimPath, stagingRoot, claimsRoot, mangaRoot)
            return StagedLocalManga(
                id = id,
                stagingPath = stagingPath,
                finalPath = finalPath,
                manifest = manifest,
                stagingRoot = stagingRoot,
                mangaRoot = mangaRoot,
                claimPath = claimPath,
                claimsRoot = claimsRoot,
                fileFaults = fileFaults,
                scanner = scanner,
                createdIdentity = identity,
            )
        } catch (crash: LocalImportCrashSimulation) {
            throw crash
        } catch (error: Throwable) {
            var stagingRemoved = Files.notExists(stagingPath, LinkOption.NOFOLLOW_LINKS)
            if (stagingCreated || !stagingRemoved) {
                runCatching {
                    createdIdentity?.let { identity ->
                        deleteOwnedDirectory(stagingRoot, stagingPath, identity, claimPath) ||
                            deleteEmptyClaimedDirectory(stagingRoot, stagingPath, identity, claimPath)
                    } ?: false
                }.onSuccess { removed -> stagingRemoved = removed }
                    .onFailure(error::addSuppressed)
            }
            if (claimCreated && stagingRemoved) {
                runCatching { deleteValidClaim(claimsRoot, claimPath, id, ownershipNonce) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    fun cleanupOrphans(localLibraryRoot: Path, retainedFinalPaths: Set<Path>) {
        val root = localLibraryRoot.toAbsolutePath().normalize()
        val stagingRoot = root.resolve(STAGING_DIRECTORY)
        val claimsRoot = root.resolve(CLAIMS_DIRECTORY)
        val mangaRoot = root.resolve(MANGA_DIRECTORY)
        safeEnsureDirectory(root)
        safeEnsurePrivateDirectory(stagingRoot)
        safeEnsurePrivateDirectory(claimsRoot)
        safeEnsurePrivateDirectory(mangaRoot)
        val retained = retainedFinalPaths.mapTo(mutableSetOf()) { retainedPath ->
            val normalized = retainedPath.toAbsolutePath().normalize()
            requireImmediateChild(mangaRoot, normalized, "database local manga path")
            normalized
        }
        readValidClaims(claimsRoot).values.forEach { claim ->
            val stagingPath = stagingRoot.resolve(claim.id)
            val finalPath = mangaRoot.resolve(claim.id)
            var deletedDirectory = false
            if (isSafeImportDirectory(stagingPath, claim, allowTemporaryMarker = true)) {
                deleteValidated(stagingRoot, stagingPath)
                deletedDirectory = true
            }
            if (finalPath !in retained && isSafeImportDirectory(finalPath, claim, allowTemporaryMarker = false)) {
                deleteValidated(mangaRoot, finalPath)
                deletedDirectory = true
            }
            val pathRemains = Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)
            if (!pathRemains && (deletedDirectory || isStaleClaim(claim.createdAt, clock()))) {
                deleteValidClaim(claimsRoot, claim.path, claim.id, claim.nonce)
            }
        }
    }

    private fun validateManifestShape(manifest: LocalImportManifest) {
        if (manifest.chapters.isEmpty()) rejectStaging("manifest contains no chapters")
        scanner.validateCandidatePaths(manifest.sourceRoot, manifest.chapters.map(LocalChapterCandidate::relativePath))
        manifest.chapters.forEach { chapter ->
            if (chapter.name != chapter.relativePath.fileName?.toString()) {
                rejectStaging("manifest chapter name does not match its path: ${chapter.relativePath}")
            }
            if (chapter.relativePath.nameCount != 1) {
                rejectStaging("manifest chapter must be an immediate child: ${chapter.relativePath}")
            }
            if (chapter.sizeBytes < 0) rejectStaging("manifest chapter has a negative size: ${chapter.relativePath}")
        }
    }

    private fun verifyStagedState(
        manifest: LocalImportManifest,
        stagingPath: Path,
        identity: OwnedDirectoryIdentity,
        claimPath: Path,
        stagingRoot: Path,
        claimsRoot: Path,
        mangaRoot: Path,
    ) {
        validatePrivateNamespace(stagingRoot, "staging namespace")
        validatePrivateNamespace(claimsRoot, "claim namespace")
        validatePrivateNamespace(mangaRoot, "manga namespace")
        requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
        requireUnchangedManifest(manifest, scanner.scan(manifest.sourceRoot), "source changed during staging")
        validatePrivateTree(stagingPath)
        val stagedManifest = scanner.scanOwnedImportDirectory(
            stagingPath,
            setOf(ownershipMarkerPath(stagingPath, identity.nonce)),
        )
        if (stagedManifest.chapters != manifest.chapters || stagedManifest.sha256 != manifest.sha256) {
            rejectStaging("staged manifest verification failed")
        }
    }

    private fun copyEntryNoFollow(source: Path, target: Path, context: StagingCopyContext) {
        val before = safeSourceAttributes(source)
        when {
            before.isDirectory -> copyDirectoryNoFollow(source, target, before, context)
            before.isRegularFile -> copyFileNoFollow(source, target, before, context)
            else -> rejectStaging("source entry is not a regular file or directory: $source")
        }
    }

    private fun copyDirectoryNoFollow(
        source: Path,
        target: Path,
        before: BasicFileAttributes,
        context: StagingCopyContext,
    ) {
        try {
            Files.createDirectory(target)
            restrictPrivatePath(target, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
            withDirectoryIterationRejection("failed to enumerate source directory: $source") {
                Files.newDirectoryStream(source).use { children ->
                    children.forEach { child ->
                        copyEntryNoFollow(child, target.resolve(child.fileName.toString()), context)
                    }
                }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
            validatePrivatePath(target, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source directory without following links: $source", error)
        }
    }

    private fun copyFileNoFollow(
        source: Path,
        target: Path,
        before: BasicFileAttributes,
        context: StagingCopyContext,
    ) {
        try {
            val parent = checkNotNull(target.parent)
            val parentIdentity = readAttributes(parent, "staged target parent")
            FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    restrictPrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
                    val targetIdentity = readAttributes(target, "staged target file")
                    val buffer = ByteBuffer.allocateDirect(COPY_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (input.read(buffer) != -1) {
                        buffer.flip()
                        while (buffer.hasRemaining()) copiedBytes += output.write(buffer)
                        fileFaults.afterCopyChunk(source, target, copiedBytes)
                        verifyAfterCopyCallback(
                            source,
                            before,
                            target,
                            targetIdentity,
                            parent,
                            parentIdentity,
                            copiedBytes,
                            output,
                            context,
                        )
                        buffer.clear()
                    }
                }
            }
            verifySourceUnchanged(source, before)
            Files.setLastModifiedTime(target, before.lastModifiedTime())
            restrictPrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: IOException) {
            rejectStaging("failed to copy source file without following links: $source", error)
        }
    }

    private fun verifyAfterCopyCallback(
        source: Path,
        sourceIdentity: BasicFileAttributes,
        target: Path,
        targetIdentity: BasicFileAttributes,
        parent: Path,
        parentIdentity: BasicFileAttributes,
        copiedBytes: Long,
        output: FileChannel,
        context: StagingCopyContext,
    ) {
        verifySourceUnchanged(source, sourceIdentity)
        context.validate()
        validateNoReparseAncestors(parent, "staged target parent")
        validatePrivatePath(parent, DIRECTORY_OWNER_PERMISSIONS, isDirectory = true)
        val currentParent = readAttributes(parent, "staged target parent")
        if (!samePathIdentity(parentIdentity, currentParent)) {
            rejectStaging("staged target parent identity changed: $parent")
        }
        validateNoReparseAncestors(target, "staged target file")
        validatePrivatePath(target, FILE_OWNER_PERMISSIONS, isDirectory = false)
        val currentTarget = readAttributes(target, "staged target file")
        if (
            !samePathIdentity(targetIdentity, currentTarget) ||
            currentTarget.size() != copiedBytes ||
            output.size() != copiedBytes
        ) {
            rejectStaging("staged target file identity changed: $target")
        }
    }

    private fun safeSourceAttributes(source: Path): BasicFileAttributes {
        validateNoReparseAncestors(source, "staged source")
        val attributes = readAttributes(source, "staged source entry")
        if (isLinkOrReparsePoint(source, attributes)) rejectStaging("link or reparse point is not allowed: $source")
        if (!Files.isReadable(source)) rejectStaging("source entry is unreadable: $source")
        return attributes
    }

    private fun verifySourceUnchanged(source: Path, before: BasicFileAttributes) {
        val after = safeSourceAttributes(source)
        if (!sameIdentityAndMetadata(before, after)) rejectStaging("source entry changed while copying: $source")
    }
}
