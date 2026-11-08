package mihon.extension.host

import mihon.extension.validator.ExtensionValidationException
import java.io.File

/**
 * Resolves a package entry against the extension working directory.
 *
 * [ExtensionPackageValidator] inspects entry names from local ZIP headers while [java.util.zip.ZipFile]
 * reports the central-directory name. A crafted archive can make the two disagree, so extraction
 * must prove the resolved destination stays inside the working directory.
 */
internal fun extensionEntryFile(workingDir: File, entryName: String): File {
    if (entryName.isEmpty() || entryName.contains('\u0000')) {
        throw ExtensionValidationException("Unsafe extension entry path: '$entryName'")
    }
    val root = workingDir.canonicalFile
    val destination = File(root, entryName).canonicalFile
    if (!destination.toPath().startsWith(root.toPath())) {
        throw ExtensionValidationException("Unsafe extension entry path: '$entryName'")
    }
    return destination
}
