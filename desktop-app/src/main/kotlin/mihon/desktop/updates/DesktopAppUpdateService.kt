package mihon.desktop.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.platform.DistributionMode
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable
data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0,
    val contentType: String = "",
)

@Serializable
data class AppReleaseInfo(
    val version: String,
    val tagName: String,
    val releaseNotes: String = "",
    val htmlUrl: String = "",
    val publishedAt: String = "",
    val assets: List<AppReleaseAsset> = emptyList(),
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val currentVersion: String,
        val matchedAsset: AppReleaseAsset?,
    ) : UpdateCheckResult

    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class CheckFailed(val message: String) : UpdateCheckResult
}

class DesktopAppUpdateService(
    val currentVersion: String = CURRENT_VERSION,
    val distributionMode: DistributionMode = DistributionMode.Installed,
    val repository: String = DEFAULT_REPO,
    private val fetchText: suspend (String) -> String = ::defaultFetchText,
    private val downloadStream: suspend (String) -> InputStream = ::defaultDownloadStream,
) {
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$repository/releases/latest"
            val jsonText = fetchText(url)
            val release = parseReleaseJson(jsonText)
            if (isNewerVersion(release.version, currentVersion)) {
                val matchedAsset =
                    findBestAsset(
                        release.assets.filter {
                            it.name.startsWith("mihondesk-${release.version}.") ||
                                it.name.startsWith("mihondesk-${release.version}-") ||
                                it.name.startsWith("MihonW-${release.version}.") ||
                                it.name.startsWith("MihonW-${release.version}-")
                        },
                        distributionMode,
                    )
                UpdateCheckResult.UpdateAvailable(release, currentVersion, matchedAsset)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.CheckFailed(e.message ?: "Failed to check for updates")
        }
    }

    suspend fun downloadAsset(
        asset: AppReleaseAsset,
        destination: Path,
        expectedSha256: String? = null,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
        onPublishing: suspend () -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        if (expectedSha256 == null || !SHA256.matches(expectedSha256)) return@withContext false
        var tempFile: Path? = null
        try {
            tempFile = Files.createTempFile(destination.toAbsolutePath().parent, ".mihon-update-", ".download")
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            downloadStream(asset.downloadUrl).use { input ->
                Files.newOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(asset.size <= 0 || received <= asset.size) { "Download size mismatch" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val now = System.nanoTime()
                        if (now - reported >= 100_000_000) {
                            onProgress(received, asset.size)
                            reported = now
                        }
                    }
                }
            }
            require(asset.size <= 0 || received == asset.size) { "Download size mismatch" }
            require(
                digest.digest().joinToString("") {
                    "%02x".format(it)
                }.equals(expectedSha256, true),
            ) { "Checksum mismatch" }
            onProgress(received, asset.size)
            currentCoroutineContext().ensureActive()
            onPublishing()
            currentCoroutineContext().ensureActive()
            // Windows can briefly deny replacement while another process inspects an EXE.
            // Keep the original intact; retries add at most one second of backoff.
            for (attempt in 0..4) {
                try {
                    Files.move(
                        tempFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                    break
                } catch (error: java.nio.file.FileSystemException) {
                    if (attempt == 4 || error is java.nio.file.AtomicMoveNotSupportedException ||
                        error is java.nio.file.NoSuchFileException
                    ) {
                        throw error
                    }
                    delay((attempt + 1) * 100L)
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            tempFile?.let { Files.deleteIfExists(it) }
        }
    }

    /** Require one unambiguous checksum belonging to the selected official release asset. */
    suspend fun releaseChecksum(release: AppReleaseInfo, asset: AppReleaseAsset): String = withContext(Dispatchers.IO) {
        require(asset in release.assets && trustedAsset(release, asset)) { "Unsupported release asset" }
        require(asset.size > 0) { "Release asset size is missing" }
        val manifest = release.assets.single { it.name == "SHA256SUMS.txt" }
        require(trustedAsset(release, manifest)) { "Unsupported checksum location" }
        val matches = fetchText(manifest.downloadUrl).lineSequence().mapNotNull { line ->
            val match = Regex("^([a-fA-F0-9]{64}) [ *](.+)$").matchEntire(line.trimEnd()) ?: return@mapNotNull null
            if (match.groupValues[2] == asset.name) match.groupValues[1] else null
        }.toList()
        require(matches.size == 1) { "Missing or ambiguous checksum" }
        matches.single()
    }

    private fun trustedAsset(release: AppReleaseInfo, asset: AppReleaseAsset): Boolean {
        if (!Regex("[a-zA-Z0-9._-]+").matches(asset.name)) return false
        if (!Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+").matches(release.tagName)) return false
        return asset.downloadUrl == "https://github.com/$repository/releases/download/${release.tagName}/${asset.name}"
    }

    companion object {
        val CURRENT_VERSION: String = requireNotNull(
            DesktopAppUpdateService::class.java.getResourceAsStream("/mihon-desktop-version.txt"),
        ) { "Desktop version resource is missing" }.bufferedReader().use { it.readText().trim() }
        const val DEFAULT_REPO = "1873412297-art/mihondesk"

        private val json = Json { ignoreUnknownKeys = true }
        private val SHA256 = Regex("[a-fA-F0-9]{64}")

        fun parseReleaseJson(jsonText: String): AppReleaseInfo {
            val element = json.parseToJsonElement(jsonText).jsonObject
            require(
                element["draft"]?.jsonPrimitive?.content != "true" &&
                    element["prerelease"]?.jsonPrimitive?.content != "true",
            )
            val tagName = element["tag_name"]?.jsonPrimitive?.content ?: ""
            val version = tagName.removePrefix("v").trim()
            parseVersionParts(tagName)
            val releaseNotes = element["body"]?.jsonPrimitive?.content ?: ""
            val htmlUrl = element["html_url"]?.jsonPrimitive?.content ?: ""
            val publishedAt = element["published_at"]?.jsonPrimitive?.content ?: ""

            val assets = element["assets"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                AppReleaseAsset(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    downloadUrl = obj["browser_download_url"]?.jsonPrimitive?.content ?: "",
                    size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    contentType = obj["content_type"]?.jsonPrimitive?.content ?: "",
                )
            } ?: emptyList()

            return AppReleaseInfo(
                version = version,
                tagName = tagName,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                assets = assets,
            )
        }

        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = parseVersionParts(remoteVersion)
            val currentParts = parseVersionParts(currentVersion)
            val maxLength = maxOf(remoteParts.size, currentParts.size)

            for (i in 0 until maxLength) {
                val remote = remoteParts.getOrElse(i) { 0 }
                val current = currentParts.getOrElse(i) { 0 }
                if (remote > current) return true
                if (remote < current) return false
            }
            return false
        }

        private fun parseVersionParts(v: String): List<Int> {
            require(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+").matches(v)) { "Unsupported release version" }
            return v.removePrefix("v").split('.').map { it.toInt() }
        }

        fun findBestAsset(assets: List<AppReleaseAsset>, mode: DistributionMode): AppReleaseAsset? = when (mode) {
            DistributionMode.Portable -> assets.singleOrNull {
                Regex("(?:mihondesk|MihonW)-[0-9]+\\.[0-9]+\\.[0-9]+-windows-x64-portable\\.zip").matches(it.name)
            }
            DistributionMode.Installed -> assets.singleOrNull {
                Regex("(?:mihondesk|MihonW)-[0-9]+\\.[0-9]+\\.[0-9]+\\.exe").matches(it.name)
            }
        }

        fun verifySha256(file: Path, expectedHash: String): Boolean {
            if (!Files.exists(file)) return false
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            return actualHash.equals(expectedHash.trim(), ignoreCase = true)
        }

        private fun defaultFetchText(url: String): String {
            val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "mihondesk/$CURRENT_VERSION")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            try {
                return connection.inputStream.use { input ->
                    val bytes = input.readNBytes(2 * 1024 * 1024 + 1)
                    require(bytes.size <= 2 * 1024 * 1024) { "Release metadata too large" }
                    bytes.toString(Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun defaultDownloadStream(url: String): InputStream {
            val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "mihondesk/$CURRENT_VERSION")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            return try {
                object : java.io.FilterInputStream(connection.inputStream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
    }
}
