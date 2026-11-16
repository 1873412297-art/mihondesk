package mihon.desktop.updates

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mihon.desktop.activity.ActivityKind
import mihon.desktop.activity.ActivityState
import mihon.desktop.i18n.EnglishStrings
import mihon.desktop.notification.LibraryUpdateAlerts
import mihon.desktop.notification.LibraryUpdateTarget
import mihon.desktop.ui.settings.AppUpdateCard
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AppUpdateNotificationsTest {

    private val release = UpdateCheckResult.UpdateAvailable(
        release = AppReleaseInfo(version = "0.5.0", tagName = "v0.5.0"),
        currentVersion = "0.4.0",
        matchedAsset = AppReleaseAsset("mihondesk-0.5.0.msi", "https://example.invalid/0.5.0", 1L),
    )

    private fun alerts(enabled: Boolean, shown: MutableList<String>, badges: MutableList<Int>) = LibraryUpdateAlerts(
        display = { title, message -> shown += "$title|$message" },
        badge = { badges += it },
        strings = { EnglishStrings },
        hideContent = { false },
        enabled = { enabled },
    )

    @Test
    fun `an app update shows a balloon whose click opens the update card and leaves the chapter badge alone`() {
        val shown = mutableListOf<String>()
        val badges = mutableListOf<Int>()
        val alerts = alerts(enabled = true, shown, badges)

        alerts.onAppUpdate("MihonDesk update available", "Version 0.5.0 is ready")

        shown shouldContainExactly listOf("MihonDesk update available|Version 0.5.0 is ready")
        badges.shouldBeEmpty()
        alerts.consumeClick() shouldBe LibraryUpdateTarget.AppUpdate
        alerts.clicks.value shouldBe LibraryUpdateTarget.AppUpdate
    }

    @Test
    fun `no balloon when desktop notifications are off`() {
        val shown = mutableListOf<String>()
        alerts(enabled = false, shown, mutableListOf()).onAppUpdate("title", "message")
        shown.shouldBeEmpty()
    }

    @Test
    fun `the activity entry names the version and its action opens the update card`() {
        var opened = 0
        val item = appUpdateActivity(release, EnglishStrings) { opened++ }

        item.id shouldBe APP_UPDATE_ACTIVITY_ID
        item.kind shouldBe ActivityKind.APP_UPDATE
        item.state shouldBe ActivityState.SUCCEEDED
        item.subject shouldBe "MihonDesk 0.5.0"
        item.detail shouldBe "Version 0.5.0 is available (installed: 0.4.0)"
        item.actions.single().run()
        opened shouldBe 1
    }

    @Test
    fun `a release from the background check shows on an idle update card only`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val presenter = AppUpdatePresenter(DesktopAppUpdateService(), scope)
            presenter.offer(release)
            presenter.state.value.phase shouldBe AppUpdatePhase.Available
            presenter.state.value.release shouldBe release

            // Already showing a release: a later offer does not replace it mid-flow.
            val newer = release.copy(release = release.release.copy(version = "0.6.0", tagName = "v0.6.0"))
            presenter.offer(newer)
            presenter.state.value.release shouldBe release
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the update card switch turns automatic checks on and off`() = runComposeUiTest {
        val changes = mutableListOf<Boolean>()
        setContent {
            MaterialTheme {
                AppUpdateCard(
                    state = AppUpdateState(),
                    onCheck = {},
                    onDownload = {},
                    onCancel = {},
                    onOpenFolder = {},
                    onOpenRelease = {},
                    autoCheck = true,
                    onAutoCheckChange = { changes += it },
                )
            }
        }
        onNodeWithTag("app-update-auto-check").assertIsOn()
        onNodeWithTag("app-update-auto-check").performClick()
        changes shouldContainExactly listOf(false)
    }

    @Test
    fun `the update card hides the switch when no setting is wired`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AppUpdateCard(
                    state = AppUpdateState(),
                    onCheck = {},
                    onDownload = {},
                    onCancel = {},
                    onOpenFolder = {},
                    onOpenRelease = {},
                )
            }
        }
        onNodeWithTag("app-update-auto-check").assertDoesNotExist()
    }
}
