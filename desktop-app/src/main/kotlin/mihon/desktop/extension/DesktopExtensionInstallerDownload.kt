package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mihon.desktop.updates.DesktopAppUpdateService
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal suspend fun DesktopExtensionInstaller.downloadAndInstallInternal(
    downloadUrl: String,
    expectedSha256: String?,
    repoUrl: String = "",
    storeItem: ExtensionStoreItem? = null,
    trustOnInstall: Boolean = false,
    allowUntrusted: Boolean = false,
    explicitTrust: Boolean = false,
    trustUnknown: Boolean = false,
    onDownloadComplete: () -> Unit = {},
): InstalledExtension {
    val installer = this
    return withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "MihonW/${DesktopAppUpdateService.CURRENT_VERSION}")
            .build()
        val tempFile = File.createTempFile("mext_dl_", ".mext")
        try {
            downloadToFile(request, tempFile)

            currentCoroutineContext().ensureActive()
            onDownloadComplete()
            installer.installFromLocalFileInternal(
                file = tempFile,
                expectedSha256 = expectedSha256,
                repoUrl = repoUrl,
                storeItem = storeItem,
                trustOnInstall = trustOnInstall,
                allowUntrusted = allowUntrusted,
                explicitTrust = explicitTrust,
                trustUnknown = trustUnknown,
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

private suspend fun DesktopExtensionInstaller.downloadToFile(request: Request, tempFile: File) {
    coroutineScope {
        val call = httpClient.newCall(request)
        val download = async(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ExtensionDownloadException(
                            "Failed to download extension package: HTTP ${response.code}",
                        )
                    }
                    FileOutputStream(tempFile).use { out ->
                        val input = response.body.byteStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                    }
                }
            } catch (failure: IOException) {
                // Closing a cancelled socket must remain cancellation, not an install error.
                currentCoroutineContext().ensureActive()
                throw failure
            }
        }
        try {
            download.await()
        } catch (cancelled: CancellationException) {
            // Covers both header and body waits. The scope joins the writer before
            // the outer finally deletes its temporary file (also on Windows).
            call.cancel()
            throw cancelled
        }
    }
}
