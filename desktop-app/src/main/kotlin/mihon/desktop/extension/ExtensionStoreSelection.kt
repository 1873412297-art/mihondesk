package mihon.desktop.extension

/** Selects updates only from stores declaring the already installed signer. */
internal fun selectAvailableStoreItems(
    installed: List<InstalledExtension>,
    available: List<ExtensionStoreItem>,
): List<ExtensionStoreItem> {
    val installedByPackage = installed.associateBy { it.pkg }
    return available.groupBy { it.pkg }.mapNotNull { (pkg, candidates) ->
        val existing = installedByPackage[pkg]
        val verifiedSigners = existing?.let { extension ->
            ExtensionTrustStore.normalizeFingerprints(
                extension.signatureFingerprints + extension.signatureFingerprint + extension.signingKey,
            ).filter(String::isNotEmpty).toSet()
        }.orEmpty()
        val eligible = if (verifiedSigners.isEmpty()) {
            candidates
        } else {
            candidates.filter { candidate ->
                ExtensionTrustStore.normalizeFingerprint(candidate.signingKey) in verifiedSigners
            }
        }
        eligible.maxByOrNull { it.versionCode }
    }.sortedBy { it.name }
}
