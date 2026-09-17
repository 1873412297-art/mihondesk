package mihon.desktop.updates

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.platform.DistributionMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Read-only contract smoke; regular test runs never depend on GitHub availability. */
@EnabledIfEnvironmentVariable(named = "MIHON_UPDATE_LIVE", matches = "1")
class AppUpdateLiveTest {
    @Test
    fun `published release provides checksums for both Windows distributions`(): Unit = runBlocking {
        for (mode in DistributionMode.entries) {
            val service = DesktopAppUpdateService(currentVersion = "0.0.0", distributionMode = mode)
            val available = service.checkForUpdates().shouldBeInstanceOf<UpdateCheckResult.UpdateAvailable>()
            val asset = requireNotNull(available.matchedAsset)
            val checksum = service.releaseChecksum(available.release, asset)
            Regex("[a-fA-F0-9]{64}").matches(checksum) shouldBe true
            println("LIVE_RELEASE ${available.release.tagName} ${asset.name} ${asset.size} $checksum")
        }
    }
}
