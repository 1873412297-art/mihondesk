package mihon.desktop.extension

import java.io.File

/**
 * Enforces the Android-style trust policy:
 *  - a package signed by a known repository key or a previously trusted fingerprint installs;
 *  - an unknown package requires explicit user trust (the Browse UI's "Trust & Install");
 *  - a package whose signer mismatches a known key/fingerprint is always rejected;
 *  - a revoked package must be explicitly trusted again, even when a repository key matches.
 */
internal fun DesktopExtensionInstaller.enforceTrustPolicy(
    file: File,
    pkg: String,
    signerFingerprints: List<String>,
    storeItem: ExtensionStoreItem?,
    installedFingerprints: Set<String> = emptySet(),
    explicitTrust: Boolean,
) {
    val storeSigningKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey)
    val userTrusted = trustStore.getTrustedFingerprints(pkg)
    val revoked = trustStore.isRevoked(pkg)
    if (signerFingerprints.isEmpty()) {
        enforceUnsignedTrustPolicy(
            file = file,
            pkg = pkg,
            storeSigningKey = storeSigningKey,
            userTrusted = userTrusted,
            revoked = revoked,
            installedFingerprints = installedFingerprints,
            storeItem = storeItem,
            explicitTrust = explicitTrust,
        )
        return
    }

    val storeMatches = storeSigningKey.isNotEmpty() && storeSigningKey in signerFingerprints
    val userMatches = signerFingerprints.any { it in userTrusted }
    val installedMatches = installedFingerprints.any { it in signerFingerprints }

    // A known repository signing key must match the package, even when the installed
    // extension fingerprint would otherwise allow the update.
    if (storeSigningKey.isNotEmpty() && !storeMatches) {
        throw ExtensionSignatureMismatchException(pkg, setOf(storeSigningKey), signerFingerprints)
    }

    // Updating an installed extension may not change its signer, even with explicit trust.
    if (installedFingerprints.isNotEmpty() && !installedMatches) {
        throw ExtensionSignatureMismatchException(
            pkg = pkg,
            expectedFingerprints = installedFingerprints,
            actualFingerprints = signerFingerprints,
        )
    }

    val officialMatches = signerFingerprints.any { it in officialSigningKeys }
    if (storeMatches || userMatches || installedMatches || officialMatches) {
        enforceKnownSignerTrust(file, pkg, signerFingerprints, storeItem, revoked, explicitTrust)
        return
    }

    val expectedKnown = storeSigningKey.isNotEmpty() || userTrusted.isNotEmpty()
    if (expectedKnown) {
        val expected = buildSet {
            addAll(userTrusted)
            if (storeSigningKey.isNotEmpty()) add(storeSigningKey)
        }
        throw ExtensionSignatureMismatchException(pkg, expected, signerFingerprints)
    }

    if (explicitTrust) {
        trustStore.trust(pkg, signerFingerprints)
        return
    }

    requestTrustAndThrow(
        file = file,
        pkg = pkg,
        signerFingerprints = signerFingerprints,
        storeItem = storeItem,
        reason = "Extension signer is unknown",
    )
}

private fun DesktopExtensionInstaller.enforceKnownSignerTrust(
    file: File,
    pkg: String,
    signerFingerprints: List<String>,
    storeItem: ExtensionStoreItem?,
    revoked: Boolean,
    explicitTrust: Boolean,
) {
    if (!revoked) return
    if (explicitTrust) {
        trustStore.trust(pkg, signerFingerprints)
        return
    }
    requestTrustAndThrow(
        file = file,
        pkg = pkg,
        signerFingerprints = signerFingerprints,
        storeItem = storeItem,
        reason = "Trust for this extension was revoked",
    )
}

private fun DesktopExtensionInstaller.enforceUnsignedTrustPolicy(
    file: File,
    pkg: String,
    storeSigningKey: String,
    userTrusted: Set<String>,
    revoked: Boolean,
    installedFingerprints: Set<String>,
    storeItem: ExtensionStoreItem?,
    explicitTrust: Boolean,
) {
    val unsignedTrusted = !revoked && "" in userTrusted
    when {
        // A previously signed extension must not silently downgrade to an unsigned package.
        installedFingerprints.isNotEmpty() -> throw ExtensionSignatureMismatchException(
            pkg = pkg,
            expectedFingerprints = installedFingerprints,
            actualFingerprints = emptyList(),
        )
        unsignedTrusted -> return
        storeSigningKey.isNotEmpty() -> throw ExtensionSignatureMismatchException(
            pkg = pkg,
            expectedFingerprints = setOf(storeSigningKey),
            actualFingerprints = emptyList(),
        )
        userTrusted.isNotEmpty() -> throw ExtensionSignatureMismatchException(
            pkg = pkg,
            expectedFingerprints = userTrusted,
            actualFingerprints = emptyList(),
        )
        explicitTrust -> {
            trustStore.trustUnsigned(pkg)
            return
        }
        else -> requestTrustAndThrow(
            file = file,
            pkg = pkg,
            signerFingerprints = emptyList(),
            storeItem = storeItem,
            reason = "Extension package is unsigned and its signer is unknown",
        )
    }
}

private fun DesktopExtensionInstaller.requestTrustAndThrow(
    file: File,
    pkg: String,
    signerFingerprints: List<String>,
    storeItem: ExtensionStoreItem?,
    reason: String,
): Nothing {
    trustStore.requestTrust(
        pkg = pkg,
        fingerprints = signerFingerprints,
        filePath = file.absolutePath,
        storeItem = storeItem,
        reason = reason,
    )
    throw ExtensionTrustRequiredException(pkg, signerFingerprints, reason)
}
