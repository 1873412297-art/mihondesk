package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mihon.desktop.platform.DistributionMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class AppUpdateDownloadTest {
    private val base = "https://github.com/${DesktopAppUpdateService.DEFAULT_REPO}/releases/download/v0.3.0/"
    private val asset = AppReleaseAsset("mihondesk-0.3.0.exe", base + "mihondesk-0.3.0.exe", 16)
    private val manifest = AppReleaseAsset("SHA256SUMS.txt", base + "SHA256SUMS.txt")
    private val release = AppReleaseInfo("0.3.0", "v0.3.0", assets = listOf(asset, manifest))

    @TempDir lateinit var root: Path
    private val bytes = "verified package".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `checksum requires exact official asset and unique manifest entry`(): Unit = runBlocking {
        DesktopAppUpdateService(fetchText = { "$hash *${asset.name}\r\n" }).releaseChecksum(release, asset) shouldBe
            hash
        for (text in listOf("", "$hash  different.exe", "$hash  ${asset.name}\n$hash  ${asset.name}")) {
            var rejected = false
            try {
                DesktopAppUpdateService(fetchText = { text }).releaseChecksum(release, asset)
            } catch (
                _: IllegalArgumentException,
            ) {
                rejected = true
            }
            rejected shouldBe true
        }
        for (bad in listOf(
            asset.copy(downloadUrl = "https://example.com/file.exe"),
            asset.copy(name = "../file.exe"),
        )) {
            var fetched = false
            var rejected = false
            try {
                DesktopAppUpdateService(fetchText = {
                    fetched = true
                    ""
                }).releaseChecksum(release.copy(assets = listOf(bad, manifest)), bad)
            } catch (
                _: IllegalArgumentException,
            ) {
                rejected = true
            }
            rejected shouldBe true
            fetched shouldBe false
        }
    }

    @Test
    fun `cancellation closes download and preserves old destination`(): Unit = runBlocking {
        val target = root.resolve("update.exe")
        Files.writeString(target, "existing")
        var closed = false
        val stream = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val service = DesktopAppUpdateService(downloadStream = { stream })
        var cancelled = false
        try {
            service.downloadAsset(asset.copy(size = bytes.size.toLong()), target, hash, onProgress = { _, _ ->
                throw CancellationException()
            })
        } catch (_: CancellationException) {
            cancelled = true
        }
        cancelled shouldBe true
        closed shouldBe true
        Files.readString(target) shouldBe "existing"
        Files.list(root).use { it.count() } shouldBe 1L
    }

    @Test
    fun `unrelated assets cannot substitute for a missing distribution`() {
        DesktopAppUpdateService.findBestAsset(
            listOf(AppReleaseAsset("source.zip", "unused")),
            DistributionMode.Portable,
        ) shouldBe
            null
        DesktopAppUpdateService.findBestAsset(listOf(asset, asset), DistributionMode.Installed) shouldBe null
    }

    @Test
    fun `wrong version assets and prereleases do not offer install packages`(): Unit = runBlocking {
        val json = """{"tag_name":"v0.3.0","assets":[{"name":"mihondesk-0.2.9.exe"}]}"""
        DesktopAppUpdateService(fetchText = { json }).checkForUpdates()
            .shouldBeInstanceOf<UpdateCheckResult.UpdateAvailable>().matchedAsset shouldBe null
        for (flag in listOf("draft", "prerelease")) {
            DesktopAppUpdateService(fetchText = { """{"tag_name":"v0.3.0","$flag":true}""" })
                .checkForUpdates().shouldBeInstanceOf<UpdateCheckResult.CheckFailed>()
        }
    }

    @Test
    fun `unchecked downloads are refused without opening network`(): Unit = runBlocking {
        var opened = false
        val service = DesktopAppUpdateService(downloadStream = {
            opened = true
            ByteArrayInputStream(bytes)
        })
        service.downloadAsset(AppReleaseAsset("app.zip", "unused"), root.resolve("app.zip")) shouldBe false
        opened shouldBe false
    }

    @Test
    fun `truncated download preserves existing file and removes temporary files`(): Unit = runBlocking {
        val target = root.resolve("app.zip")
        Files.writeString(target, "existing")
        val service = DesktopAppUpdateService(downloadStream = { ByteArrayInputStream(bytes) })
        service.downloadAsset(AppReleaseAsset("app.zip", "unused", bytes.size + 5L), target, hash) shouldBe false
        Files.readString(target) shouldBe "existing"
        Files.list(root).use { it.count() } shouldBe 1L
    }

    @Test
    fun `check cancellation is propagated`(): Unit = runBlocking {
        val service = DesktopAppUpdateService(fetchText = { throw CancellationException("cancel") })
        var cancelled = false
        try {
            service.checkForUpdates()
        } catch (_: CancellationException) {
            cancelled = true
        }
        cancelled shouldBe true
    }

    @Test
    fun `invalid stable versions fail instead of reporting current`(): Unit = runBlocking {
        for (tag in listOf("", "broken", "v0.3.0-beta.1")) {
            DesktopAppUpdateService(fetchText = { """{"tag_name":"$tag"}""" })
                .checkForUpdates().shouldBeInstanceOf<UpdateCheckResult.CheckFailed>()
        }
    }
}
