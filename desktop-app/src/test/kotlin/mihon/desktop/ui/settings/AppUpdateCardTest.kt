package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.i18n.AppLanguage
import mihon.desktop.i18n.ProvideDesktopStrings
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.MihonDesktopTheme
import mihon.desktop.updates.AppReleaseAsset
import mihon.desktop.updates.AppReleaseInfo
import mihon.desktop.updates.AppUpdatePhase
import mihon.desktop.updates.AppUpdateState
import mihon.desktop.updates.UpdateCheckResult
import org.jetbrains.skia.Image
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class AppUpdateCardTest {
    @ParameterizedTest
    @CsvSource(
        "1024,English,false,Download update…",
        "480,English,true,Download update…",
        "1024,SimplifiedChinese,true,下载更新…",
        "480,SimplifiedChinese,false,下载更新…",
        "1024,TraditionalChinese,false,下載更新…",
        "480,TraditionalChinese,true,下載更新…",
    )
    fun `update flow has usable localized actions across desktop widths`(
        width: Int,
        language: AppLanguage,
        dark: Boolean,
        downloadLabel: String,
    ) = runComposeUiTest {
        System.getenv("MIHON_UPDATE_APP")?.let { directory ->
            for (name in listOf(
                "mihon.desktop.ui.settings.AppUpdateCardKt",
                "mihon.desktop.ui.settings.AppUpdatePanelKt",
                "mihon.desktop.updates.AppUpdatePresenter",
                "mihon.desktop.updates.DesktopAppUpdateService",
            )) {
                val origin = Path.of(Class.forName(name).protectionDomain.codeSource.location.toURI())
                check(origin.startsWith(Path.of(directory))) { "Expected packaged $name, got $origin" }
                println("PACKAGED_APP_UPDATE $name $origin")
            }
        }
        val asset = AppReleaseAsset("mihondesk-0.3.0-windows-x64-portable.zip", "unused", 512000000)
        val release = UpdateCheckResult.UpdateAvailable(
            AppReleaseInfo(
                "0.3.0",
                "v0.3.0",
                "Release notes / 更新说明 / 更新說明\nImproved reader and recovery.",
                assets = listOf(asset),
            ),
            "0.2.18",
            asset,
        )
        val state = mutableStateOf(AppUpdateState(AppUpdatePhase.Available, release))
        var downloads = 0
        var checks = 0
        var cancels = 0
        var folders = 0
        setContent {
            ProvideDesktopStrings(language) {
                MihonDesktopTheme(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light, isAmoled = dark) {
                    Surface(Modifier.requiredSize(width.dp, 680.dp).testTag("app-update-render")) {
                        AboutScreen(updateContent = {
                            AppUpdateCard(state.value, { checks++ }, { downloads++ }, { cancels++ }, { folders++ }, {})
                        })
                    }
                }
            }
        }
        onNodeWithText(downloadLabel).assertIsDisplayed().performClick()
        downloads shouldBe 1
        onNodeWithTag("app-update-check").performClick()
        checks shouldBe 1
        runOnIdle {
            state.value =
                state.value.copy(phase = AppUpdatePhase.Downloading, received = 256000000, total = asset.size)
        }
        onNodeWithTag("app-update-download").assertDoesNotExist()
        onNodeWithTag("app-update-cancel").performClick()
        cancels shouldBe 1
        runOnIdle { state.value = state.value.copy(phase = AppUpdatePhase.Cancelling) }
        onNodeWithTag("app-update-cancel").assertIsNotEnabled()
        runOnIdle {
            state.value =
                state.value.copy(phase = AppUpdatePhase.Ready, savedFile = Path.of("C:/Downloads/" + asset.name))
        }
        onNodeWithTag("app-update-saved").assertIsDisplayed()
        onNodeWithTag("app-update-folder").assertIsDisplayed().performClick()
        folders shouldBe 1
        System.getenv("MIHON_UPDATE_EVIDENCE")?.let { location ->
            val target = Files.createDirectories(Path.of(location))
            Image.makeFromBitmap(onNodeWithTag("app-update-render").captureToImage().asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { png ->
                    Files.write(target.resolve("updates-$width-$language.png"), png.bytes)
                }
            }
        }
    }
}
