package mihon.desktop.extension

import kotlinx.serialization.Serializable
import mihon.extension.model.ExtensionManifest

@Serializable
data class InstalledExtension(
    val pkg: String,
    val manifest: ExtensionManifest,
    val installDir: String,
    val packageFile: String,
    val installedAt: Long,
    val repoUrl: String = "",
    val iconPath: String? = null,
    val isEnabled: Boolean = true,
    /** Last (primary) verified signer certificate SHA-256 fingerprint, or "" when unsigned. */
    val signatureFingerprint: String = "",
    /** All verified signer certificate SHA-256 fingerprints, or empty when unsigned. */
    val signatureFingerprints: List<String> = emptyList(),
    /** Repository signing key that was used to trust this package, if any. */
    val signingKey: String = "",
    /** Trust state recorded at install time. Prefer the live [ExtensionTrustStore] when available. */
    val trustStatus: ExtensionTrustStatus = ExtensionTrustStatus.UNKNOWN,
) {
    val signerFingerprint: String get() = signatureFingerprint
    val signerFingerprints: List<String> get() = signatureFingerprints
    val signatureHash: String get() = signatureFingerprint
    val fingerprint: String get() = signatureFingerprint
    val trustedFingerprints: List<String> get() = signatureFingerprints
    val trustState: ExtensionTrustStatus get() = trustStatus
    val isTrusted: Boolean get() = trustStatus == ExtensionTrustStatus.TRUSTED
    val trusted: Boolean get() = isTrusted
    val isUntrusted: Boolean get() = trustStatus == ExtensionTrustStatus.UNTRUSTED
}
