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
 * Extracts and verifies v1 JAR and v2/v3 APK signer certificate SHA-256 fingerprints.
 *
 * Verification is performed by [JarFile] with verification enabled: every non-directory entry is
 * read fully, which triggers the JDK's JAR signature validation. Certificates returned by
 * [java.util.jar.JarEntry.getCodeSigners] are then hashed with SHA-256.
 */
open class ExtensionSignatureVerifier : ExtensionVerifier {

    override fun verify(file: File): ExtensionSignatureVerification {
        if (!file.exists() || !file.isFile) {
            return invalid("Extension package file does not exist: ${file.absolutePath}")
        }

        val hasSigningBlock = try {
            hasApkSigningBlock(file)
        } catch (_: Exception) {
            false
        }

        val fingerprints = LinkedHashSet<String>()
        var hasV1SignatureFiles = false
        val unsignedEntries = mutableListOf<String>()
        var signedEntryFingerprints: Set<String>? = null

        try {
            JarFile(file, true).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (isV1SignatureFile(entry.name)) {
                        hasV1SignatureFiles = true
                    }
                    if (entry.isDirectory) continue

                    try {
                        jar.getInputStream(entry).use { input -> drain(input) }
                    } catch (e: SecurityException) {
                        return invalid(
                            "JAR signature verification failed for entry '${entry.name}': " +
                                (e.message ?: "signature digest mismatch"),
                            hasSigningBlock,
                            hasV1SignatureFiles,
                        )
                    }

                    val entryFingerprints = signerFingerprints(entry)
                    if (entryFingerprints.isEmpty()) {
                        if (!isJarMetadataFile(entry.name)) unsignedEntries += entry.name
                    } else {
                        fingerprints += entryFingerprints
                        val previous = signedEntryFingerprints
                        if (previous == null) {
                            signedEntryFingerprints = entryFingerprints
                        } else if (previous != entryFingerprints) {
                            return invalid(
                                "Package entries are signed by different signer certificates",
                                hasSigningBlock,
                                hasV1SignatureFiles,
                            )
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return invalid(
                "JAR signature verification failed: ${e.message ?: "invalid signature"}",
                hasSigningBlock,
                hasV1SignatureFiles,
            )
        } catch (e: Exception) {
            if (hasSigningBlock) {
                return invalid(
                    "APK carries an APK Signing Block (Signature Scheme v2/v3) but its ZIP/JAR structure " +
                        "could not be verified: ${e.message ?: e::class.simpleName}. The desktop installer can " +
                        "only verify v1 JAR signatures; this package was not trusted.",
                    hasSigningBlock = true,
                    hasV1SignatureFiles = hasV1SignatureFiles,
                )
            }
            return invalid(
                "Extension package is not a valid signed JAR/ZIP archive: ${e.message ?: e::class.simpleName}",
                hasSigningBlock,
                hasV1SignatureFiles,
            )
        }

        if (fingerprints.isEmpty()) {
            if (hasSigningBlock) {
                // Official repositories (e.g. keiyoushi) ship v2/v3-only APKs, so verify the APK
                // Signing Block itself instead of rejecting the package.
                return verifyApkSigningBlock(file, hasV1SignatureFiles)
            }
            if (hasV1SignatureFiles) {
                return invalid(
                    "Package contains JAR signature files but no valid signer certificates. " +
                        "The v1 signature could not be verified.",
                    hasSigningBlock = false,
                    hasV1SignatureFiles = true,
                )
            }
            return ExtensionSignatureVerification(
                fingerprints = emptyList(),
                hasV1Signature = false,
                hasApkSigningBlock = false,
                valid = true,
            )
        }

        if (unsignedEntries.isNotEmpty()) {
            return invalid(
                "Package contains unsigned entries (e.g. '${unsignedEntries.first()}'); " +
                    "partially signed packages are not trusted.",
                hasSigningBlock,
                hasV1SignatureFiles,
            )
        }

        return ExtensionSignatureVerification(
            fingerprints = fingerprints.toList(),
            hasV1Signature = hasV1SignatureFiles,
            hasApkSigningBlock = hasSigningBlock,
            valid = true,
        )
    }

    /** Returns signer fingerprints, throwing for invalid or v2/v3-only packages. */
    fun extractSignerFingerprints(file: File): List<String> = verifyOrThrow(file).fingerprints

    /**
     * Signer identity of one archive entry. Empty when the entry is not covered by a v1 signature;
     * a package may not mix signed and unsigned entries.
     */
    private fun signerFingerprints(entry: java.util.jar.JarEntry): Set<String> {
        val signers = entry.codeSigners ?: return emptySet()
        val result = LinkedHashSet<String>()
        for (signer in signers) {
            // The signer certificate is the first certificate in the path; the rest are the CA chain.
            val x509 = signer.signerCertPath.certificates.firstOrNull() as? X509Certificate
            if (x509 != null) result += sha256Hex(x509.encoded)
        }
        return result
    }

    private fun isJarMetadataFile(name: String): Boolean =
        name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ||
            name.startsWith("META-INF/SIG-", ignoreCase = true) ||
            isV1SignatureFile(name)

    fun verifyOrThrow(file: File): ExtensionSignatureVerification {
        val result = verify(file)
        if (!result.valid || result.v2V3Only) {
            throw ExtensionSignatureException(result.error ?: "Extension signature verification failed")
        }
        return result
    }

    /**
     * Verifies APK Signature Scheme v2/v3 with apksig and returns the signer certificate SHA-256s.
     * The platform range starts at API 24 so a missing v1 signature is not itself treated as an error.
     */
    private fun verifyApkSigningBlock(file: File, hasV1SignatureFiles: Boolean): ExtensionSignatureVerification {
        val result = try {
            ApkVerifier.Builder(file)
                .setMinCheckedPlatformVersion(MIN_CHECKED_SDK)
                .setMaxCheckedPlatformVersion(MAX_CHECKED_SDK)
                .build()
                .verify()
        } catch (e: Exception) {
            return invalid(
                "APK Signature Scheme v2/v3 block could not be verified: ${e.message ?: e::class.simpleName}",
                hasSigningBlock = true,
                hasV1SignatureFiles = hasV1SignatureFiles,
            )
        }
        val certificates = result.signerCertificates.orEmpty()
        if (!result.isVerified || certificates.isEmpty()) {
            val details = result.allErrors.joinToString("; ").ifBlank { "no verified signer" }
            return invalid(
                "APK Signature Scheme v2/v3 verification failed: $details",
                hasSigningBlock = true,
                hasV1SignatureFiles = hasV1SignatureFiles,
            )
        }
        return ExtensionSignatureVerification(
            fingerprints = certificates.map { sha256Hex(it.encoded) }.distinct(),
            hasV1Signature = hasV1SignatureFiles,
            hasApkSigningBlock = true,
            valid = true,
        )
    }

    private fun invalid(
        message: String,
        hasSigningBlock: Boolean = false,
        hasV1SignatureFiles: Boolean = false,
    ): ExtensionSignatureVerification = ExtensionSignatureVerification(
        fingerprints = emptyList(),
        hasV1Signature = hasV1SignatureFiles,
        hasApkSigningBlock = hasSigningBlock,
        valid = false,
        error = message,
    )

    companion object : ExtensionVerifier {
        private const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val EOCD_MIN_SIZE = 22
        private const val MAX_ZIP_COMMENT = 0xFFFF
        private const val MIN_CHECKED_SDK = 24
        private const val MAX_CHECKED_SDK = 35

        override fun verify(file: File): ExtensionSignatureVerification = ExtensionSignatureVerifier().verify(file)

        fun extractFingerprints(file: File): List<String> =
            ExtensionSignatureVerifier().extractSignerFingerprints(file)

        fun isV1SignatureFile(name: String): Boolean {
            if (!name.startsWith("META-INF/", ignoreCase = true)) return false
            val upper = name.uppercase()
            return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
                upper.endsWith(".EC")
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                builder.append(Character.forDigit((byte.toInt() shr 4) and 0x0F, 16))
                builder.append(Character.forDigit(byte.toInt() and 0x0F, 16))
            }
            return builder.toString()
        }

        private fun drain(input: InputStream) {
            val buffer = ByteArray(8192)
            while (input.read(buffer) != -1) {
                // Reading the entry is what triggers JAR signature verification.
            }
        }

        /**
         * Detects the APK Signing Block v2/v3 magic. The canonical location is the 16 bytes
         * immediately before the central directory; a tail scan is used as a fallback for ZIP64
         * archives where the EOCD central directory offset is not a direct byte offset.
         */
        private fun hasApkSigningBlock(file: File): Boolean {
            if (file.length() < (EOCD_MIN_SIZE + APK_SIG_BLOCK_MAGIC.length).toLong()) return false
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.US_ASCII)

            RandomAccessFile(file, "r").use { raf ->
                val tailLength = minOf(
                    file.length(),
                    (EOCD_MIN_SIZE + MAX_ZIP_COMMENT + 64).toLong(),
                ).toInt()
                val tail = ByteArray(tailLength)
                raf.seek(file.length() - tailLength)
                raf.readFully(tail)

                val eocdOffset = findEndOfCentralDirectory(tail)
                if (eocdOffset >= 0) {
                    val centralDirectoryOffset = findCentralDirectoryOffset(
                        raf = raf,
                        tail = tail,
                        eocdOffset = eocdOffset,
                        fileLength = file.length(),
                    )
                    if (centralDirectoryOffset != null &&
                        centralDirectoryOffset >= magicBytes.size.toLong() &&
                        centralDirectoryOffset <= file.length()
                    ) {
                        val magicOffset = centralDirectoryOffset - magicBytes.size
                        val magic = ByteArray(magicBytes.size)
                        raf.seek(magicOffset)
                        raf.readFully(magic)
                        if (magic.contentEquals(magicBytes)) return true
                    }
                }

                return indexOf(tail, magicBytes) >= 0
            }
        }

        /**
         * Resolves the central directory offset, including the ZIP64 EOCD path where the standard
         * EOCD stores 0xFFFFFFFF and a ZIP64 EOCD locator/record is present.
         */
        private fun findCentralDirectoryOffset(
            raf: RandomAccessFile,
            tail: ByteArray,
            eocdOffset: Int,
            fileLength: Long,
        ): Long? {
            val standardOffset = readIntLe(tail, eocdOffset + 16).toLong() and 0xFFFFFFFFL
            if (standardOffset != 0xFFFFFFFFL && standardOffset <= fileLength) {
                return standardOffset
            }

            // ZIP64 end of central directory locator sits 20 bytes before the standard EOCD.
            val locatorOffset = eocdOffset - 20
            if (locatorOffset >= 0 && readIntLe(tail, locatorOffset) == 0x07064b50) {
                val zip64EocdOffset = readLongLe(tail, locatorOffset + 8)
                if (zip64EocdOffset >= 0 && zip64EocdOffset + 56 <= fileLength) {
                    val zip64Record = ByteArray(56)
                    raf.seek(zip64EocdOffset)
                    raf.readFully(zip64Record)
                    if (readIntLe(zip64Record, 0) == 0x06064b50) {
                        return readLongLe(zip64Record, 48)
                    }
                }
            }
            return null
        }

        private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
            if (bytes.size < EOCD_MIN_SIZE) return -1
            for (index in bytes.size - EOCD_MIN_SIZE downTo 0) {
                val signature = (bytes[index].toInt() and 0xFF) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[index + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 3].toInt() and 0xFF) shl 24)
                if (signature == EOCD_SIGNATURE) return index
            }
            return -1
        }

        private fun readLongLe(bytes: ByteArray, offset: Int): Long {
            if (offset + 8 > bytes.size) return -1L
            var value = 0L
            for (index in 0 until 8) {
                value = value or ((bytes[offset + index].toLong() and 0xFFL) shl (index * 8))
            }
            return value
        }

        private fun readIntLe(bytes: ByteArray, offset: Int): Int {
            if (offset + 4 > bytes.size) return -1
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (index in 0..haystack.size - needle.size) {
                for (needleIndex in needle.indices) {
                    if (haystack[index + needleIndex] != needle[needleIndex]) continue@outer
                }
                return index
            }
            return -1
        }
    }
}
