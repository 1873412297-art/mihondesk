package mihon.desktop.updates

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateWatcherTest {

    @TempDir
    lateinit var tempDir: Path

    private val store by lazy { DesktopPreferenceStore(tempDir.resolve("preferences.properties")) }

    private fun available(tag: String, withPackage: Boolean = true) = UpdateCheckResult.UpdateAvailable(
        release = AppReleaseInfo(version = tag.removePrefix("v"), tagName = tag),
        currentVersion = "0.4.0",
        matchedAsset = if (withPackage) {
            AppReleaseAsset(
                "mihondesk-$tag.msi",
                "https://example.invalid/$tag",
                1L,
            )
        } else {
            null
        },
    )

    private fun TestScope.watcher(
        results: ArrayDeque<UpdateCheckResult>,
        announced: MutableList<String>,
    ) = AppUpdateWatcher(
        check = { results.removeFirstOrNull() ?: UpdateCheckResult.UpToDate("0.4.0") },
        preferences = store,
        scope = backgroundScope,
        onNewRelease = { announced += it.update.release.tagName },
        initialDelay = 1.minutes,
        interval = 24.hours,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `checks after the startup delay and then every 24 hours`() = runTest {
        var checks = 0
        val watcher = AppUpdateWatcher(
            check = {
                checks++
                UpdateCheckResult.UpToDate("0.4.0")
            },
            preferences = store,
            scope = backgroundScope,
            onNewRelease = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        watcher.start()

        advanceTimeBy(59.seconds.inWholeMilliseconds)
        runCurrent()
        checks shouldBe 0
        advanceTimeBy(2.seconds.inWholeMilliseconds)
        runCurrent()
        checks shouldBe 1
        advanceTimeBy(24.hours.inWholeMilliseconds)
        runCurrent()
        checks shouldBe 2
        watcher.close()
    }

    @Test
    fun `a version is announced once, across restarts`() = runTest {
        val announced = mutableListOf<String>()
        val first = watcher(ArrayDeque(listOf(available("v0.5.0"), available("v0.5.0"))), announced)
        first.checkOnce()
        first.checkOnce()

        // A new watcher (app restart) reads the persisted notified version.
        watcher(ArrayDeque(listOf(available("v0.5.0"), available("v0.6.0"))), announced).apply {
            checkOnce()
            checkOnce()
        }

        announced shouldContainExactly listOf("v0.5.0", "v0.6.0")
        store.load().appUpdateNotifiedVersion shouldBe "v0.6.0"
    }

    @Test
    fun `nothing is announced without a package for this platform, when turned off, or on failure`() = runTest {
        val announced = mutableListOf<String>()
        val watcher = watcher(
            ArrayDeque(
                listOf(
                    available("v0.5.0", withPackage = false),
                    UpdateCheckResult.CheckFailed("rate limited", retryAfterSeconds = 60),
                    available("v0.6.0"),
                ),
            ),
            announced,
        )
        watcher.checkOnce() shouldBe null
        watcher.checkOnce() shouldBe null

        store.updatePreferences { it.copy(appUpdateAutoCheck = false) }
        watcher.checkOnce() shouldBe null

        announced shouldBe emptyList()
        // The turned-off check did not consume the queued release.
        store.updatePreferences { it.copy(appUpdateAutoCheck = true) }
        watcher.checkOnce()?.update?.release?.tagName shouldBe "v0.6.0"
    }

    @Test
    fun `with automatic downloads on, the announced release carries its verified package`() = runTest {
        val announced = mutableListOf<AppUpdateDiscovery>()
        val downloads = mutableListOf<String>()
        val package050 = DownloadedAppUpdate(tempDir.resolve("mihondesk-v0.5.0.msi"), "a".repeat(64))
        val watcher = AppUpdateWatcher(
            check = { available("v0.5.0") },
            preferences = store,
            scope = backgroundScope,
            onNewRelease = { announced += it },
            download = { update ->
                downloads += update.release.tagName
                package050
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        // Off by default: the release is announced without downloading anything.
        watcher.checkOnce()?.download shouldBe null
        downloads shouldBe emptyList()

        store.updatePreferences { it.copy(appUpdateAutoDownload = true, appUpdateNotifiedVersion = "") }
        watcher.checkOnce()?.download shouldBe package050
        downloads shouldContainExactly listOf("v0.5.0")
        announced.map { it.download } shouldContainExactly listOf(null, package050)

        // Already announced: not announced again, but the card still gets the downloaded package.
        watcher.checkOnce() shouldBe null
        watcher.latest.value?.download shouldBe package050
    }

    @Test
    fun `a failed background download still announces the release`() = runTest {
        val announced = mutableListOf<AppUpdateDiscovery>()
        store.updatePreferences { it.copy(appUpdateAutoDownload = true) }
        val watcher = AppUpdateWatcher(
            check = { available("v0.5.0") },
            preferences = store,
            scope = backgroundScope,
            onNewRelease = { announced += it },
            download = { error("network down") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        watcher.checkOnce()?.update?.release?.tagName shouldBe "v0.5.0"
        announced.single().download shouldBe null
    }
}
