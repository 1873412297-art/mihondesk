package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.SimplifiedChineseStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.sync.gdrive.DriveTokenVault
import mihon.desktop.sync.gdrive.FileDriveSyncStateStore
import mihon.desktop.sync.gdrive.GoogleDriveConfig
import mihon.desktop.sync.gdrive.GoogleDriveSyncService
import mihon.desktop.sync.gdrive.TokenCipher
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.ui.UI_TEST_TIMEOUT
import mihon.sync.core.model.Changeset
import mihon.sync.core.model.EntityDelta
import mihon.sync.engine.SyncApplyResult
import mihon.sync.engine.SyncLocalRepository
import mihon.sync.transport.api.SyncTransport
import mihon.sync.transport.gdrive.GoogleTokens
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class GoogleDriveSyncCardTest {
    @TempDir lateinit var dir: Path

    private object Plain : TokenCipher {
        override fun protect(plain: ByteArray) = plain
        override fun unprotect(data: ByteArray) = data
    }

    private object EmptyRepo : SyncLocalRepository {
        override suspend fun exportDelta(sinceCursor: Long) = EntityDelta()
        override suspend fun applyChangeset(changeset: Changeset) = SyncApplyResult()
    }

    private fun service(failing: Boolean) = GoogleDriveSyncService(
        config = GoogleDriveConfig("cid", "s"),
        vault = DriveTokenVault(dir.resolve("tokens.bin"), Plain),
        stateStore = FileDriveSyncStateStore(dir.resolve("state.json")) { "desk" },
        localRepository = { EmptyRepo },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        authorizer = { GoogleTokens("a", "r", Long.MAX_VALUE) },
        refresher = { it },
        transportFactory = {
            object : SyncTransport {
                override suspend fun push(changeset: Changeset) = Unit
                override suspend fun pull(sinceCursors: Map<String, Long>, excludeDeviceId: String?) =
                    if (failing) throw java.net.UnknownHostException("www.googleapis.com") else emptyList<Changeset>()
                override suspend fun headCursor(excludeDeviceId: String?) = 0L
            }
        },
        clock = { 1_793_590_000_000 },
    )

    @Test
    fun `signed in card shows status, last sync and friendly network error in chinese`() =
        runComposeUiTest(testTimeout = UI_TEST_TIMEOUT) {
            val ok = service(failing = false)
            runBlocking {
                ok.signIn()
                ok.syncNow()
            }
            val bad = service(failing = true)
            runBlocking { bad.syncNow() }
            setContent {
                CompositionLocalProvider(LocalStrings provides SimplifiedChineseStrings) {
                    MihonDesktopTheme(themeMode = ThemeMode.Light) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.requiredWidth(720.dp).padding(16.dp)) { GoogleDriveSyncCard(bad) }
                        }
                    }
                }
            }
            onNodeWithText("Google Drive 同步").assertExists()
            onNodeWithText("已连接 Google Drive").assertExists()
            onNodeWithTag("gdrive-last-sync").assertTextContains("上次同步：", substring = true)
            onNodeWithTag("gdrive-error").assertTextContains("代理", substring = true)
            onNodeWithTag("gdrive-sync-now").assertIsEnabled()
            onNodeWithTag("gdrive-sign-out").assertExists()
            System.getenv("MIHON_GDRIVE_EVIDENCE")?.let { location ->
                val target = Files.createDirectories(Path.of(location))
                Image.makeFromBitmap(onNodeWithTag("gdrive-sync-card").captureToImage().asSkiaBitmap()).use { img ->
                    img.encodeToData()!!.use { Files.write(target.resolve("gdrive-settings.png"), it.bytes) }
                }
            }
        }
}
