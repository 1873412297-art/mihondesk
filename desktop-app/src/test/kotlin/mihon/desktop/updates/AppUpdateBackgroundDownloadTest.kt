package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class AppUpdateBackgroundDownloadTest {
    @TempDir
    lateinit var root: Path

    private val data = "package bytes".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private val name = "mihondesk-9.9.0.exe"
    private val base = "https://github.com/${DesktopAppUpdateService.DEFAULT_REPO}/releases/download/v9.9.0/"
    private val json = """{"tag_name":"v9.9.0","assets":[
        {"name":"$name","browser_download_url":"$base$name","size":${data.size}},
        {"name":"SHA256SUMS.txt","browser_download_url":"${base}SHA256SUMS.txt"}]}"""

    private fun service(onDownload: () -> Unit = {}) = DesktopAppUpdateService(
        fetchText = { if (it.endsWith("latest")) json else "$hash  $name" },
        downloadStream = {
            onDownload()
            ByteArrayInputStream(data)
        },
    )

    @Test
    fun `downloads and verifies the package, reuses it next time and removes older packages`(): Unit = runBlocking {
        var downloads = 0
        val service = service { downloads++ }
        val update = service.checkForUpdates() as UpdateCheckResult.UpdateAvailable
        val directory = root.resolve("updates")
        Files.createDirectories(directory)
        val stale = Files.writeString(directory.resolve("mihondesk-9.8.0.exe"), "old")

        val first = downloadAppUpdateInBackground(service, update, directory)
        first shouldBe DownloadedAppUpdate(directory.resolve(name), hash)
        Files.readAllBytes(directory.resolve(name)) shouldBe data
        Files.exists(stale) shouldBe false

        downloadAppUpdateInBackground(service, update, directory) shouldBe first
        downloads shouldBe 1
    }

    @Test
    fun `a tampered package is downloaded again`(): Unit = runBlocking {
        var downloads = 0
        val service = service { downloads++ }
        val update = service.checkForUpdates() as UpdateCheckResult.UpdateAvailable
        Files.write(root.resolve(name), "evil bytes!!!".toByteArray())

        downloadAppUpdateInBackground(service, update, root) shouldNotBe null
        downloads shouldBe 1
        Files.readAllBytes(root.resolve(name)) shouldBe data
    }

    @Test
    fun `a failed download returns null`(): Unit = runBlocking {
        val service = DesktopAppUpdateService(
            fetchText = { if (it.endsWith("latest")) json else "$hash  $name" },
            downloadStream = { throw java.io.IOException("offline") },
        )
        val update = service.checkForUpdates() as UpdateCheckResult.UpdateAvailable
        downloadAppUpdateInBackground(service, update, root) shouldBe null
        Files.exists(root.resolve(name)) shouldBe false
    }
}
