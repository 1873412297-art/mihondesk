package mihon.desktop.library.local

import java.nio.file.Path

internal data class OwnedDirectoryIdentity(val id: String, val nonce: String, val fileKey: Any?)

internal data class OwnedMarkerIdentity(val id: String, val nonce: String, val fileKey: Any?)

internal enum class PathPresence {
    ABSENT,
    PRESENT,
    UNKNOWN,
}

internal enum class OwnedPathState {
    ABSENT,
    OWNED,
    OTHER_OR_UNKNOWN,
}

internal data class OwnedPathObservation(
    val state: OwnedPathState,
    val needsProvenance: Boolean = false,
)

internal data class ObservablePromotionState(
    val staging: OwnedPathObservation,
    val final: OwnedPathObservation,
) {
    val needsProvenance: Boolean = staging.needsProvenance || final.needsProvenance

    companion object {
        fun unknown(): ObservablePromotionState = ObservablePromotionState(
            staging = OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true),
            final = OwnedPathObservation(OwnedPathState.OTHER_OR_UNKNOWN, needsProvenance = true),
        )
    }
}

internal data class StagingCopyContext(
    val stagingPath: Path,
    val identity: OwnedDirectoryIdentity,
    val claimPath: Path,
    val stagingRoot: Path,
    val claimsRoot: Path,
    val mangaRoot: Path,
) {
    fun validate() {
        validateClaimAndNamespaces(
            stagingRoot,
            claimsRoot,
            mangaRoot,
            claimPath,
            identity.id,
            identity.nonce,
        )
        requireOwnedDirectoryMatches(stagingPath, identity, claimPath)
    }
}

internal data class ValidClaim(val id: String, val nonce: String, val createdAt: Long, val path: Path)
