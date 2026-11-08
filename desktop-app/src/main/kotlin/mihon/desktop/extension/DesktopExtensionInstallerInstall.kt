package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.compat.replaceSourceIdentities
import mihon.extension.model.ExtensionManifest
import mihon.extension.validator.ExtensionPackageValidator
import mihon.extension.validator.ExtensionValidationException
import java.io.File
import java.security.MessageDigest

internal suspend fun DesktopExtensionInstaller.installFromLocalFileInternal(
    file: File,
    expectedSha256: String? = null,
    repoUrl: String = "",
    storeItem: ExtensionStoreItem? = null,
    trustOnInstall: Boolean = false,
    allowUntrusted: Boolean = false,
    explicitTrust: Boolean = false,
    trustUnknown: Boolean = false,
): InstalledExtension {
    val installer = this
    return withLifecycleLock {
        withContext(Dispatchers.IO) {
            installer.installUnlocked(
                file = file,
                expectedSha256 = expectedSha256,
                repoUrl = repoUrl,
                storeItem = storeItem,
                trustOnInstall = trustOnInstall,
                allowUntrusted = allowUntrusted,
                explicitTrust = explicitTrust,
                trustUnknown = trustUnknown,
            )
        }
    }
}

private suspend fun DesktopExtensionInstaller.installUnlocked(
    file: File,
    expectedSha256: String?,
    repoUrl: String,
    storeItem: ExtensionStoreItem?,
    trustOnInstall: Boolean,
    allowUntrusted: Boolean,
    explicitTrust: Boolean,
    trustUnknown: Boolean,
): InstalledExtension {
    if (!file.exists() || !file.isFile) {
        throw ExtensionValidationException("Extension package file does not exist: ${file.absolutePath}")
    }

    // A new install attempt supersedes any previous trust prompt.
    trustStore.clearPendingTrustRequest()

    verifyExpectedHashes(file, expectedSha256, storeItem)
    val signerFingerprints = verifyPackageSignature(file)
    val converted = convertOrValidatePackage(file, storeItem)
    return try {
        installConvertedPackage(
            originalFile = file,
            converted = converted,
            signerFingerprints = signerFingerprints,
            repoUrl = repoUrl,
            storeItem = storeItem,
            explicitTrust = trustOnInstall || allowUntrusted || explicitTrust || trustUnknown ||
                storeItem?.trustOnInstall == true,
        )
    } finally {
        converted.tempFile?.delete()
    }
}

private fun DesktopExtensionInstaller.verifyExpectedHashes(
    file: File,
    expectedSha256: String?,
    storeItem: ExtensionStoreItem?,
) {
    val expectedHashes = buildList {
        expectedSha256?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        storeItem?.sha256?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
    }.distinct()
    if (expectedHashes.isEmpty()) return
    val actualSha256 = computeSha256(file)
    expectedHashes.forEach { expected ->
        if (!actualSha256.equals(expected, ignoreCase = true)) {
            throw ExtensionValidationException(
                "SHA-256 mismatch: expected '$expected', but computed '$actualSha256'",
            )
        }
    }
}

private fun DesktopExtensionInstaller.verifyPackageSignature(file: File): List<String> {
    // Verify before conversion changes the package bytes.
    val verification = try {
        signatureVerifier.verify(file)
    } catch (e: ExtensionValidationException) {
        throw e
    } catch (e: Exception) {
        throw ExtensionSignatureException("Failed to verify extension signature: ${e.message}", e)
    }
    if (!verification.valid || verification.v2V3Only) {
        throw ExtensionSignatureException(
            verification.error ?: "APK Signature Scheme v2/v3-only packages are not supported",
        )
    }
    return verification.fingerprints
        .map { ExtensionTrustStore.normalizeFingerprint(it) }
        .filter { it.isNotEmpty() }
        .distinct()
}

private class ConvertedPackage(
    val packageFile: File,
    val manifest: ExtensionManifest,
    val tempFile: File?,
)

private fun convertOrValidatePackage(file: File, storeItem: ExtensionStoreItem?): ConvertedPackage {
    if (!mihon.desktop.extension.compat.TachiyomiExtensionConverter.isTachiyomiPackage(file)) {
        val manifest = ExtensionPackageValidator.validatePackage(file)
        return ConvertedPackage(packageFile = file, manifest = manifest, tempFile = null)
    }
    val convertedFile = File.createTempFile("mext_conv_", ".mext")
    return try {
        val manifest = mihon.desktop.extension.compat.TachiyomiExtensionConverter.convertToMext(
            file,
            convertedFile,
            storeItem,
        )
        ConvertedPackage(packageFile = convertedFile, manifest = manifest, tempFile = convertedFile)
    } catch (error: Throwable) {
        convertedFile.delete()
        throw error
    }
}

private suspend fun DesktopExtensionInstaller.installConvertedPackage(
    originalFile: File,
    converted: ConvertedPackage,
    signerFingerprints: List<String>,
    repoUrl: String,
    storeItem: ExtensionStoreItem?,
    explicitTrust: Boolean,
): InstalledExtension {
    var manifest = converted.manifest
    val installedExtension = getInstalledExtensions().find { it.pkg == manifest.id }
    val installedFingerprints = buildSet {
        installedExtension?.signatureFingerprints.orEmpty().forEach {
            add(ExtensionTrustStore.normalizeFingerprint(it))
        }
        installedExtension?.signatureFingerprint?.let { add(ExtensionTrustStore.normalizeFingerprint(it)) }
        installedExtension?.signingKey?.let { add(ExtensionTrustStore.normalizeFingerprint(it)) }
    }.filter { it.isNotEmpty() }.toSet()
    enforceTrustPolicy(
        file = originalFile,
        pkg = manifest.id,
        signerFingerprints = signerFingerprints,
        storeItem = storeItem,
        installedFingerprints = installedFingerprints,
        explicitTrust = explicitTrust,
    )

    // Persist a matching repository signing key so trust survives restarts and later
    // repository fetches can compare against a local, user-visible trust entry.
    val storeSigningKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey)
    if (storeSigningKey.isNotEmpty() && signerFingerprints.contains(storeSigningKey)) {
        trustStore.trust(manifest.id, storeSigningKey)
    }
    signerFingerprints.filter { it in officialSigningKeys }.forEach { trustStore.trust(manifest.id, it) }

    if (converted.tempFile != null && storeItem?.sources.isNullOrEmpty()) {
        val runtimeSources = DesktopSourceManager.probeInstalledPackage(
            this,
            converted.packageFile,
            manifest.id,
        )
        manifest = mihon.desktop.extension.compat.TachiyomiExtensionConverter.replaceSourceIdentities(
            converted.packageFile,
            manifest,
            runtimeSources,
        )
    }

    return publishInstall(
        effectivePackageFile = converted.packageFile,
        manifest = manifest,
        signerFingerprints = signerFingerprints,
        repoUrl = repoUrl,
        storeItem = storeItem,
        installedExtension = installedExtension,
    )
}

private suspend fun DesktopExtensionInstaller.publishInstall(
    effectivePackageFile: File,
    manifest: ExtensionManifest,
    signerFingerprints: List<String>,
    repoUrl: String,
    storeItem: ExtensionStoreItem?,
    installedExtension: InstalledExtension?,
): InstalledExtension {
    val targetDir = File(installRoot, manifest.id)
    val stagedDir = java.nio.file.Files.createTempDirectory(
        installRoot.toPath(),
        ".stage-${manifest.id}-",
    ).toFile()
    val backupDir = File(installRoot, ".backup-${manifest.id}-${java.util.UUID.randomUUID()}")
    var oldMoved = false
    var published = false
    var committed = false
    try {
        val hasIcon = stagePackage(stagedDir, effectivePackageFile, manifest.id)
        DesktopSourceManager.releaseInstalledPackage(this, manifest.id)
        if (targetDir.exists()) {
            java.nio.file.Files.move(targetDir.toPath(), backupDir.toPath())
            oldMoved = true
        }
        java.nio.file.Files.move(stagedDir.toPath(), targetDir.toPath())
        published = true
        val installed = buildInstalledExtension(
            targetDir = targetDir,
            manifest = manifest,
            signerFingerprints = signerFingerprints,
            repoUrl = repoUrl,
            storeItem = storeItem,
            installedExtension = installedExtension,
            hasIcon = hasIcon,
        )
        saveInstalledExtensions(getInstalledExtensions().filter { it.pkg != manifest.id } + installed)
        committed = true
        return installed
    } catch (error: Throwable) {
        rollbackInstall(error, targetDir, backupDir, published, oldMoved)
        throw error
    } finally {
        cleanupOrRemember(stagedDir)
        if (committed) cleanupOrRemember(backupDir)
    }
}

private fun stagePackage(stagedDir: File, effectivePackageFile: File, pkg: String): Boolean {
    val stagedPackage = File(stagedDir, "$pkg.mext")
    effectivePackageFile.copyTo(stagedPackage)
    ExtensionPackageValidator.validatePackage(stagedPackage)
    var hasIcon = false
    java.util.zip.ZipFile(stagedPackage).use { zip ->
        val entry = zip.getEntry("icon.png") ?: zip.getEntry("icon.jpg") ?: zip.getEntry("assets/icon.png")
        if (entry != null) {
            zip.getInputStream(entry).use { input ->
                File(stagedDir, "icon.png").outputStream().use { input.copyTo(it) }
            }
            hasIcon = true
        }
    }
    return hasIcon
}

private fun buildInstalledExtension(
    targetDir: File,
    manifest: ExtensionManifest,
    signerFingerprints: List<String>,
    repoUrl: String,
    storeItem: ExtensionStoreItem?,
    installedExtension: InstalledExtension?,
    hasIcon: Boolean,
): InstalledExtension = InstalledExtension(
    pkg = manifest.id,
    manifest = manifest,
    installDir = targetDir.absolutePath,
    packageFile = File(targetDir, "${manifest.id}.mext").absolutePath,
    installedAt = System.currentTimeMillis(),
    repoUrl = repoUrl,
    iconPath = if (hasIcon) File(targetDir, "icon.png").absolutePath else null,
    isEnabled = installedExtension?.isEnabled ?: true,
    signatureFingerprint = signerFingerprints.lastOrNull().orEmpty(),
    signatureFingerprints = signerFingerprints,
    signingKey = ExtensionTrustStore.normalizeFingerprint(storeItem?.signingKey),
    trustStatus = ExtensionTrustStatus.TRUSTED,
)

private fun rollbackInstall(
    error: Throwable,
    targetDir: File,
    backupDir: File,
    published: Boolean,
    oldMoved: Boolean,
) {
    if (published && targetDir.exists() && !targetDir.deleteRecursively()) {
        error.addSuppressed(
            java.io.IOException(
                "Cannot remove failed replacement ${targetDir.absolutePath}; " +
                    "old package preserved at ${backupDir.absolutePath}",
            ),
        )
    }
    if (oldMoved && !targetDir.exists()) {
        try {
            java.nio.file.Files.move(backupDir.toPath(), targetDir.toPath())
        } catch (restoreError: Throwable) {
            error.addSuppressed(restoreError)
        }
    }
}

private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
