package mihon.desktop.extension
import com.android.apksig.ApkVerifier
import mihon.extension.validator.ExtensionValidationException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/**
 * Thrown when an extension package has a malformed, invalid, or unsupported signature.
 *
 * APK packages using only APK Signature Scheme v2/v3 are verified with apksig; a malformed or
 * tampered APK Signing Block is reported with this exception.
 */
open class ExtensionSignatureException(message: String, cause: Throwable? = null) :
    ExtensionValidationException(message, cause)

/** Thrown when a package signature is valid but the signer fingerprint is not trusted yet. */
open class ExtensionTrustRequiredException(
    val pkg: String,
    val fingerprints: List<String>,
    val reason: String = "Extension signature is not trusted",
) : ExtensionValidationException(
    "$reason: $pkg (signer fingerprints: ${fingerprints.ifEmpty { listOf("UNSIGNED") }.joinToString()}). " +
        "Explicitly trust this package to install it.",
)

/** Thrown when a package signer does not match a known key or a previously trusted fingerprint. */
open class ExtensionSignatureMismatchException(
    val pkg: String,
    val expectedFingerprints: Set<String>,
    val actualFingerprints: List<String>,
) : ExtensionValidationException(
    "Extension signature mismatch for '$pkg': expected one of " +
        "${expectedFingerprints.ifEmpty { setOf("<unknown>") }.joinToString()}, but package was signed by " +
        actualFingerprints.ifEmpty { listOf("<unsigned>") }.joinToString(),
)

/** Alias used by callers that model a validly signed but not-yet-trusted package. */
typealias ExtensionUntrustedException = ExtensionSignatureMismatchException

/** Broad alias for any trust-related install rejection. */
typealias ExtensionTrustException = ExtensionValidationException

/** Alias for callers that name the malformed-signature failure after verification. */
typealias ExtensionSignatureVerificationException = ExtensionSignatureException

/** Alias for the signature verification result. */
typealias SignatureVerificationResult = ExtensionSignatureVerification

/** Alias for the pluggable signature verifier contract. */
typealias SignatureVerifier = ExtensionVerifier

/**
 * Result of inspecting an extension package's v1 JAR/APK signature.
 *
 * [valid] describes whether the package could be parsed/verified. An unsigned package is valid
 * (it simply has no signer identity), whereas a package with broken signatures is invalid.
 */
data class ExtensionSignatureVerification(
    val fingerprints: List<String> = emptyList(),
    val hasV1Signature: Boolean = false,
    val hasApkSigningBlock: Boolean = false,
    val valid: Boolean = true,
    val error: String? = null,
) {
    val signed: Boolean get() = fingerprints.isNotEmpty()
    val isSigned: Boolean get() = signed
    val isValid: Boolean get() = valid
    val signerFingerprints: List<String> get() = fingerprints
    val signatureFingerprints: List<String> get() = fingerprints
    val sha256Fingerprints: List<String> get() = fingerprints
    val certificateFingerprints: List<String> get() = fingerprints
    val primaryFingerprint: String? get() = fingerprints.lastOrNull()
    val sha256: String? get() = primaryFingerprint
    val errorMessage: String? get() = error
    val failureReason: String? get() = error

    /** True when the APK carries an APK Signing Block but no verifiable v1 JAR signature. */
    val v2V3Only: Boolean get() = hasApkSigningBlock && fingerprints.isEmpty() && !hasV1Signature
    val isV2V3Only: Boolean get() = v2V3Only

    fun requireValid() {
        if (!valid || v2V3Only) {
            throw ExtensionSignatureException(error ?: "Extension signature verification failed")
        }
    }
}

/** Pluggable verifier contract. Tests may inject a fake verifier. */
fun interface ExtensionVerifier {
    fun verify(file: File): ExtensionSignatureVerification
}
