package mihon.desktop.library.local

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class StagedLocalManga internal constructor(
    private val id: String,
    internal val stagingPath: Path,
    internal val finalPath: Path,
    internal val manifest: LocalImportManifest,
    private val stagingRoot: Path,
    private val mangaRoot: Path,
    private val claimPath: Path,
    private val claimsRoot: Path,
    private val fileFaults: LocalFileFaults,
    private val scanner: LocalImportScanner,
    private val createdIdentity: OwnedDirectoryIdentity,
) : AutoCloseable {
    private var promoted = false
    private var promotionAmbiguous = false
    private var committed = false
    private var closed = false

    fun promote(): Path {
        check(!closed) { "staged local manga is closed" }
        check(!promoted) { "staged local manga was already promoted" }
        validatePromotionNamespaces()
        requireOwnedDirectoryMatches(stagingPath, createdIdentity, claimPath)
        if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
            rejectStaging("promotion target already exists: $finalPath")
        }
        try {
            fileFaults.beforeAtomicMove(stagingPath, finalPath)
            if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
                rejectStaging("promotion target already exists: $finalPath")
            }
            validatePromotionNamespaces()
            requireOwnedDirectoryMatches(stagingPath, createdIdentity, claimPath)
            promotionAmbiguous = true
            Files.move(stagingPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
            fileFaults.afterAtomicMove(stagingPath, finalPath)
            validatePromotionNamespaces()
            requireOwnedDirectoryMatches(finalPath, createdIdentity, claimPath)
            if (Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS)) {
                rejectStaging("atomic promotion identity validation failed: $finalPath")
            }
            promoted = true
            promotionAmbiguous = false
            reverifyPromoted()
            return finalPath
        } catch (error: LocalImportRejected) {
            throw error
        } catch (error: AtomicMoveNotSupportedException) {
            rejectStaging("atomic local manga promotion is not supported", error)
        } catch (error: FileAlreadyExistsException) {
            rejectStaging("promotion target already exists: $finalPath", error)
        } catch (error: IOException) {
            rejectStaging("atomic local manga promotion failed", error)
        } finally {
            if (promotionAmbiguous) reconcileObservablePromotionState()
        }
    }

    fun markCommitted() {
        check(promoted) { "cannot commit local manga before promotion" }
        check(!closed) { "staged local manga is closed" }
        committed = true
    }

    internal fun reverifyPromoted() {
        check(promoted) { "cannot verify local manga before promotion" }
        validatePromotionNamespaces()
        requireOwnedDirectoryMatches(finalPath, createdIdentity, claimPath)
        validatePrivateTree(finalPath)
        val promotedManifest = scanner.scanOwnedImportDirectory(
            finalPath,
            setOf(ownershipMarkerPath(finalPath, createdIdentity.nonce)),
        )
        if (promotedManifest.chapters != manifest.chapters || promotedManifest.sha256 != manifest.sha256) {
            rejectStaging("promoted manifest verification failed")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (committed) return
        compensateObservablePromotionState(reconcileObservablePromotionState())
    }

    private fun validatePromotionNamespaces() {
        validatePrivateNamespace(stagingRoot, "staging namespace")
        validatePrivateNamespace(claimsRoot, "claim namespace")
        validatePrivateNamespace(mangaRoot, "manga namespace")
        validateNoReparseAncestors(claimPath, "local import claim")
    }

    private fun reconcileObservablePromotionState(): ObservablePromotionState {
        if (runCatching { validatePromotionNamespaces() }.isFailure) {
            return ObservablePromotionState.unknown()
        }
        val observable = ObservablePromotionState(
            staging = observeOwnedPath(stagingPath),
            final = observeOwnedPath(finalPath),
        )
        when {
            observable.final.state == OwnedPathState.OWNED &&
                observable.staging.state == OwnedPathState.ABSENT -> {
                promoted = true
                promotionAmbiguous = false
            }
            observable.staging.state == OwnedPathState.OWNED &&
                observable.final.state == OwnedPathState.ABSENT -> {
                promoted = false
                promotionAmbiguous = false
            }
            observable.staging.state == OwnedPathState.ABSENT &&
                observable.final.state == OwnedPathState.ABSENT -> {
                promoted = false
                promotionAmbiguous = false
            }
        }
        return observable
    }

    private fun observeOwnedPath(path: Path): OwnedPathObservation = when (observePathPresence(path)) {
        PathPresence.ABSENT -> OwnedPathObservation(OwnedPathState.ABSENT)
        PathPresence.UNKNOWN -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true)
        PathPresence.PRESENT -> when (observePathPresence(ownershipMarkerPath(path, createdIdentity.nonce))) {
            PathPresence.ABSENT -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN)
            PathPresence.UNKNOWN -> OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true)
            PathPresence.PRESENT -> {
                val matches = runCatching {
                    requireOwnedDirectoryMatches(path, createdIdentity, claimPath)
                }.isSuccess
                OwnedPathObservation(
                    state = if (matches) OwnedPathState.OWNED else OwnedPathState.OTHER_OR_UNKNOWN,
                    needsProvenance = !matches,
                )
            }
        }
    }

    private fun compensateObservablePromotionState(observable: ObservablePromotionState) {
        var allOwnedPathsRemoved = true
        if (observable.staging.state == OwnedPathState.OWNED) {
            allOwnedPathsRemoved = deleteOwnedDirectory(
                stagingRoot,
                stagingPath,
                createdIdentity,
                claimPath,
            ) && allOwnedPathsRemoved
        }
        if (observable.final.state == OwnedPathState.OWNED) {
            allOwnedPathsRemoved = deleteOwnedDirectory(
                mangaRoot,
                finalPath,
                createdIdentity,
                claimPath,
            ) && allOwnedPathsRemoved
        }

        val afterCleanup = reconcileObservablePromotionState()
        val ownedPathRemains = afterCleanup.staging.state == OwnedPathState.OWNED ||
            afterCleanup.final.state == OwnedPathState.OWNED
        if (
            allOwnedPathsRemoved &&
            !ownedPathRemains &&
            !observable.needsProvenance &&
            !afterCleanup.needsProvenance
        ) {
            deleteValidClaim(claimsRoot, claimPath, id, createdIdentity.nonce)
        }
    }
}
