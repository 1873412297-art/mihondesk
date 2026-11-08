package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun DesktopExtensionInstaller.backfillMissingIconsInternal(
    fetchRemote: suspend (InstalledExtension) -> ByteArray?,
): Int {
    val missing = withContext(Dispatchers.IO) {
        getInstalledExtensions().filter { ext -> ext.iconPath.isNullOrBlank() || !File(ext.iconPath).isFile }
    }
    if (missing.isEmpty()) return 0
    val resolved = mutableMapOf<String, String>()
    for (ext in missing) {
        val localIcon = withContext(Dispatchers.IO) { iconFromPackage(File(ext.packageFile)) }
        val downloadedIcon = try {
            fetchRemote(ext)?.let { mihon.desktop.extension.compat.ApkIconExtractor.toPng(it) ?: it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.warn(
                "ExtensionInstaller",
                "Cannot fetch the icon for ${ext.pkg}",
                error,
            )
            null
        }
        val bytes = localIcon ?: downloadedIcon ?: continue
        val target = File(ext.installDir, "icon.png")
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(ext.installDir, "icon.png.tmp")
                tmp.writeBytes(bytes)
                java.nio.file.Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }.isSuccess
        }
        if (ok) resolved[ext.pkg] = target.absolutePath
    }
    if (resolved.isEmpty()) return 0
    withLifecycleLock {
        withContext(Dispatchers.IO) {
            saveInstalledExtensions(
                getInstalledExtensions().map { ext -> resolved[ext.pkg]?.let { ext.copy(iconPath = it) } ?: ext },
            )
        }
    }
    return resolved.size
}

private fun iconFromPackage(packageFile: File): ByteArray? = runCatching {
    if (!packageFile.isFile) return null
    java.util.zip.ZipFile(packageFile).use { zip ->
        val entry = zip.getEntry("icon.png") ?: zip.getEntry("icon.jpg") ?: zip.getEntry("assets/icon.png")
        entry?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
            ?: if (zip.getEntry("AndroidManifest.xml") != null) {
                mihon.desktop.extension.compat.ApkIconExtractor.extract(zip)
            } else {
                null
            }
    }
}.getOrNull()?.takeIf { it.isNotEmpty() }
