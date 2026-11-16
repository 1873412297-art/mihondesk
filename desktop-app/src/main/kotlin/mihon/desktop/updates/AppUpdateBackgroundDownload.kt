package mihon.desktop.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.logging.DesktopLogger
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Where background downloads go: the per-user temp folder, not the profile, because the portable
 * updater copies the whole `data/` folder into the new version and would carry the archive along.
 */
internal fun appUpdateDownloadDirectory(): Path =
    Path.of(System.getProperty("java.io.tmpdir"), "mihondesk-updates").toAbsolutePath().normalize()

private val SAFE_ASSET_NAME = Regex("[A-Za-z0-9._-]+")

/**
 * Downloads and verifies [update]'s package into [directory] and returns it, or null when the
 * download failed (it resumes next time). Packages of older releases there are removed first.
 */
internal suspend fun downloadAppUpdateInBackground(
    service: DesktopAppUpdateService,
    update: UpdateCheckResult.UpdateAvailable,
    directory: Path,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): DownloadedAppUpdate? {
    val asset = update.matchedAsset ?: return null
    require(SAFE_ASSET_NAME.matches(asset.name)) { "Unsafe release asset name: ${asset.name}" }
    val checksum = service.releaseChecksum(update.release, asset)
    val destination = withContext(ioDispatcher) {
        Files.createDirectories(directory)
        pruneOtherPackages(directory, asset.name)
        directory.resolve(asset.name)
    }
    // A package downloaded by an earlier check is reused once it verifies again.
    val reusable = withContext(ioDispatcher) { hasVerifiedFile(destination, asset.size, checksum) }
    if (!reusable && !service.downloadAsset(asset, destination, checksum)) return null
    DesktopLogger.info("AppUpdate", "Downloaded ${asset.name} in the background")
    return DownloadedAppUpdate(destination, checksum)
}

internal fun hasVerifiedFile(file: Path, size: Long, sha256: String): Boolean =
    Files.isRegularFile(file, NOFOLLOW_LINKS) &&
        (size <= 0 || Files.size(file) == size) &&
        fileSha256(file).equals(sha256, ignoreCase = true)

internal fun fileSha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Keeps [keep] and its partial download; anything else in the folder is an older package. */
private fun pruneOtherPackages(directory: Path, keep: String) {
    Files.list(directory).use { entries ->
        entries.filter { entry ->
            val name = entry.fileName.toString()
            name != keep && !name.startsWith(".$keep.") && Files.isRegularFile(entry, NOFOLLOW_LINKS)
        }.forEach { stale ->
            runCatching { Files.deleteIfExists(stale) }
                .onFailure { DesktopLogger.warn("AppUpdate", "Could not remove old update package $stale", it) }
        }
    }
}
