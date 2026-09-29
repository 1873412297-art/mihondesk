package mihon.desktop.extension

import mihon.extension.validator.ExtensionValidationException

/**
 * Thrown when the listing being installed is no longer offered by the repositories that are
 * configured, so the install/update cannot be fulfilled and no download is attempted.
 */
class ExtensionStoreUnavailableException(
    val pkg: String,
    val version: String,
    message: String = "No configured store provides $pkg $version anymore",
) : ExtensionValidationException(message)

/**
 * Whether [item] can still be fulfilled from the stores that are configured right now.
 *
 * A store candidate is installable while its own repository is still configured and some configured
 * store still lists its package. Items without a repository URL are not store candidates (local
 * packages, missing-source installs) and are never blocked here.
 */
internal fun isStoreCandidateInstallable(
    item: ExtensionStoreItem,
    repositories: List<String>,
    available: List<ExtensionStoreItem>,
): Boolean {
    if (item.downloadUrl.isBlank()) return false
    val repoUrl = item.repoUrl.trim().trimEnd('/')
    if (repoUrl.isEmpty()) return true
    if (repositories.none { it.trim().trimEnd('/') == repoUrl }) return false
    // [available] already holds one selected candidate per package, so only the package can be
    // compared here; the candidate's own store was checked above.
    return available.any { it.pkg == item.pkg }
}
