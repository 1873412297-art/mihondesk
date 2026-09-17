package mihon.desktop.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mihon.desktop.updates.AppUpdatePhase
import mihon.desktop.updates.AppUpdatePresenter
import mihon.desktop.updates.DesktopAppUpdateService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class AppUpdateNavigationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `leaving about keeps the download and returning shows its verified result`() = runComposeUiTest {
        val content = "app bytes".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val name = "mihondesk-0.3.0.exe"
        val base = "https://github.com/${DesktopAppUpdateService.DEFAULT_REPO}/releases/download/v0.3.0/"
        val json = """{"tag_name":"v0.3.0","assets":[
            {"name":"$name","browser_download_url":"$base$name","size":${content.size}},
            {"name":"SHA256SUMS.txt","browser_download_url":"${base}SHA256SUMS.txt"}]}"""
        val calls = AtomicInteger()
        val streamGate = CompletableDeferred<Unit>()
        val service = DesktopAppUpdateService(fetchText = {
            calls.incrementAndGet()
            if (it.endsWith("latest")) json else "$hash  $name"
        }, downloadStream = {
            streamGate.await()
            ByteArrayInputStream(content)
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val presenter = AppUpdatePresenter(service, scope)
        val showAbout = mutableStateOf(true)
        try {
            setContent { if (showAbout.value) AppUpdatePanel(presenter, service) }
            waitForIdle()
            calls.get() shouldBe 0
            onNodeWithTag("app-update-check").performClick()
            waitUntil { presenter.state.value.phase == AppUpdatePhase.Available }
            runOnIdle {
                presenter.download(root.resolve(name))
                showAbout.value = false
            }
            waitForIdle()
            streamGate.complete(Unit)
            waitUntil { presenter.state.value.phase == AppUpdatePhase.Ready }
            runOnIdle { showAbout.value = true }
            onNodeWithTag("app-update-saved").assertExists()
            Files.readAllBytes(root.resolve(name)) shouldBe content
            calls.get() shouldBe 2
        } finally {
            streamGate.complete(Unit)
            scope.cancel()
        }
    }
}
