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
        onNewRelease = { announced += it.release.tagName },
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
        watcher.checkOnce()?.release?.tagName shouldBe "v0.6.0"
    }
}
